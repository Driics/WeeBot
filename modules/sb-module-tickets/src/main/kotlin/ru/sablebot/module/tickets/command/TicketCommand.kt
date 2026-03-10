package ru.sablebot.module.tickets.command

import dev.minn.jda.ktx.coroutines.await
import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import org.springframework.stereotype.Component
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.model.exception.DiscordException
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.command.model.dsl.slashCommand
import ru.sablebot.common.worker.message.model.commands.options.ApplicationCommandOptions
import ru.sablebot.module.tickets.service.ITicketConfigService
import ru.sablebot.module.tickets.service.ITicketService
import java.util.UUID

@Component
class TicketCommand(
    private val ticketService: ITicketService,
    private val ticketConfigService: ITicketConfigService
) : SlashCommandDeclarationWrapper {

    companion object {
        private val log = KotlinLogging.logger { }
    }

    override fun command() = slashCommand(
        "ticket", "Open a support ticket",
        CommandCategory.TICKETS, UUID.fromString("c1000001-0000-0000-0000-000000000001")
    ) {
        executor = TicketExecutor()
    }

    inner class TicketExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val subject = string("subject", "Brief description of your issue")
            val category = optionalString("category", "Ticket category")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val guild = context.guild
                val user = context.user
                val member = context.member

                // Check if tickets are enabled
                if (!ticketConfigService.isEnabled(guild.idLong)) {
                    throw DiscordException("Ticket system is not enabled in this server.")
                }

                val config = ticketConfigService.getConfig(guild.idLong)
                    ?: throw DiscordException("Ticket configuration not found.")

                // Check per-user ticket limit
                val activeTickets = ticketService.countActiveUserTickets(guild.idLong, user.id)
                if (activeTickets >= config.maxTicketsPerUser) {
                    throw DiscordException("You have reached the maximum number of open tickets (${config.maxTicketsPerUser}). Please close an existing ticket before opening a new one.")
                }

                // Get subject and optional category
                val subject = args[options.subject]
                val categoryName = args[options.category]

                // Validate category if provided
                if (categoryName != null) {
                    val categories = ticketConfigService.getCategories(guild.idLong)
                    if (categories.none { it.name.equals(categoryName, ignoreCase = true) }) {
                        throw DiscordException("Invalid category. Available categories: ${categories.joinToString(", ") { it.name }}")
                    }
                }

                // Defer reply as we'll be creating a thread
                context.deferChannelMessage(true)

                // Create the ticket entity
                val ticket = ticketService.createTicket(
                    guild = guild,
                    user = user,
                    subject = subject,
                    category = categoryName,
                    initialMessage = null
                )

                // Determine where to create the thread
                val supportChannelId = config.supportChannelId
                    ?: throw DiscordException("Support channel not configured. Please contact an administrator.")

                val supportChannel = guild.getTextChannelById(supportChannelId)
                    ?: throw DiscordException("Support channel not found. Please contact an administrator.")

                // Check bot permissions
                if (!guild.selfMember.hasPermission(supportChannel, Permission.VIEW_CHANNEL, Permission.CREATE_PUBLIC_THREADS)) {
                    throw DiscordException("Bot lacks permission to create threads in the support channel.")
                }

                // Create a private thread for the ticket
                val threadName = "Ticket #${ticket.ticketNumber} - ${subject.take(50)}"
                val thread: ThreadChannel = supportChannel.createThreadChannel(threadName, true).await()

                // Update ticket with thread ID
                ticket.channelId = supportChannel.id
                ticket.threadId = thread.id
                ticketService.getTicketById(ticket.id!!)?.let { savedTicket ->
                    savedTicket.channelId = supportChannel.id
                    savedTicket.threadId = thread.id
                }

                // Add the user to the thread
                thread.addThreadMember(member).await()

                // Send initial message in the thread
                val initialEmbed = buildString {
                    appendLine("**Ticket #${ticket.ticketNumber}** opened by ${user.asMention}")
                    appendLine()
                    appendLine("**Subject:** $subject")
                    if (categoryName != null) {
                        appendLine("**Category:** $categoryName")
                    }
                    appendLine()
                    appendLine("A staff member will assist you shortly. Please provide any additional details about your issue.")
                }

                thread.sendMessage(initialEmbed).await()

                // Add staff roles to thread if configured
                val staffRoles = ticketConfigService.getStaffRoles(guild.idLong)
                for (roleId in staffRoles) {
                    val role = guild.getRoleById(roleId)
                    if (role != null) {
                        // Mention the role in the thread to notify staff
                        thread.sendMessage("${role.asMention} - New ticket opened").await()
                        break // Only mention one staff role to avoid spam
                    }
                }

                log.info { "User ${user.effectiveName} (${user.id}) opened ticket #${ticket.ticketNumber} in guild ${guild.name} (${guild.id})" }

                // Reply to the user
                context.reply(
                    ephemeral = true,
                    "✅ Ticket #${ticket.ticketNumber} created! Please check ${thread.asMention} for assistance."
                )

            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            } catch (e: Exception) {
                log.error(e) { "Error creating ticket: ${e.message}" }
                context.reply(ephemeral = true, "An unexpected error occurred while creating your ticket. Please try again later.")
            }
        }
    }
}
