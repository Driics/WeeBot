package ru.sablebot.module.tickets.interaction

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import ru.sablebot.common.model.TicketStatus
import ru.sablebot.common.persistence.entity.Ticket
import ru.sablebot.common.persistence.repository.TicketRepository
import ru.sablebot.common.worker.event.listeners.DiscordEventListener
import ru.sablebot.module.tickets.service.ITicketConfigService
import ru.sablebot.module.tickets.service.ITicketService

@Component
class TicketButtonHandler(
    @Lazy private val ticketService: ITicketService,
    private val ticketConfigService: ITicketConfigService,
    private val ticketRepository: TicketRepository
) : DiscordEventListener() {

    companion object {
        private val logger = KotlinLogging.logger {}
        private const val TICKET_PREFIX = "ticket:"
        const val BTN_CLAIM = "ticket:claim"
        const val BTN_CLOSE = "ticket:close"
        const val BTN_REOPEN = "ticket:reopen"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val customId = event.componentId
        if (!customId.startsWith(TICKET_PREFIX)) return

        val guild = event.guild ?: return
        val member = event.member ?: return

        // Get ticket from channel
        val ticket = getTicketFromChannel(guild, event.channel.id)
        if (ticket == null) {
            event.reply("This channel is not associated with a ticket.").setEphemeral(true).queue()
            return
        }

        // Route to handler
        when (customId) {
            BTN_CLAIM -> handleClaim(event, member, guild, ticket)
            BTN_CLOSE -> handleClose(event, member, guild, ticket)
            BTN_REOPEN -> handleReopen(event, member, guild, ticket)
            else -> {
                // Unknown ticket button, ignore
                event.deferEdit().queue()
            }
        }
    }

    private fun getTicketFromChannel(guild: Guild, channelId: String): Ticket? {
        return ticketRepository.findByGuildIdAndChannelId(guild.idLong, channelId)
    }

    private fun handleClaim(event: ButtonInteractionEvent, member: Member, guild: Guild, ticket: Ticket) {
        // Check if user is staff
        if (!isStaff(member, guild)) {
            event.reply("Only staff members can claim tickets.").setEphemeral(true).queue()
            return
        }

        // Check if ticket is already claimed
        if (ticket.status == TicketStatus.CLAIMED) {
            event.reply("This ticket is already claimed by ${ticket.assignedStaffName}.").setEphemeral(true).queue()
            return
        }

        // Check if ticket is closed
        if (ticket.status == TicketStatus.CLOSED) {
            event.reply("Cannot claim a closed ticket. Please reopen it first.").setEphemeral(true).queue()
            return
        }

        event.deferEdit().queue()
        scope.launch {
            try {
                ticketService.claimTicket(guild, ticket, member)
                logger.info { "Ticket #${ticket.ticketNumber} claimed by ${member.effectiveName} (${member.id}) in guild ${guild.name} (${guild.id})" }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to claim ticket #${ticket.ticketNumber} for guild ${guild.idLong}" }
            }
        }
    }

    private fun handleClose(event: ButtonInteractionEvent, member: Member, guild: Guild, ticket: Ticket) {
        // Check if user is staff or ticket owner
        val isStaffMember = isStaff(member, guild)
        val isTicketOwner = ticket.userId == member.id

        if (!isStaffMember && !isTicketOwner) {
            event.reply("Only staff members or the ticket owner can close this ticket.").setEphemeral(true).queue()
            return
        }

        // Check if ticket is already closed
        if (ticket.status == TicketStatus.CLOSED) {
            event.reply("This ticket is already closed.").setEphemeral(true).queue()
            return
        }

        event.deferEdit().queue()
        scope.launch {
            try {
                ticketService.closeTicket(guild, ticket, member, null)
                logger.info { "Ticket #${ticket.ticketNumber} closed by ${member.effectiveName} (${member.id}) in guild ${guild.name} (${guild.id})" }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to close ticket #${ticket.ticketNumber} for guild ${guild.idLong}" }
            }
        }
    }

    private fun handleReopen(event: ButtonInteractionEvent, member: Member, guild: Guild, ticket: Ticket) {
        // Check if user is staff or ticket owner
        val isStaffMember = isStaff(member, guild)
        val isTicketOwner = ticket.userId == member.id

        if (!isStaffMember && !isTicketOwner) {
            event.reply("Only staff members or the ticket owner can reopen this ticket.").setEphemeral(true).queue()
            return
        }

        // Check if ticket is not closed
        if (ticket.status != TicketStatus.CLOSED) {
            event.reply("This ticket is not closed.").setEphemeral(true).queue()
            return
        }

        event.deferEdit().queue()
        scope.launch {
            try {
                ticketService.reopenTicket(guild, ticket, member)
                logger.info { "Ticket #${ticket.ticketNumber} reopened by ${member.effectiveName} (${member.id}) in guild ${guild.name} (${guild.id})" }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to reopen ticket #${ticket.ticketNumber} for guild ${guild.idLong}" }
            }
        }
    }

    private fun isStaff(member: Member, guild: Guild): Boolean {
        // Check if user has administrator permission
        if (member.hasPermission(Permission.ADMINISTRATOR)) {
            return true
        }

        // Check if user has any staff role
        val staffRoles = ticketConfigService.getStaffRoles(guild.idLong)
        return member.roles.any { role -> staffRoles.contains(role.id) }
    }
}
