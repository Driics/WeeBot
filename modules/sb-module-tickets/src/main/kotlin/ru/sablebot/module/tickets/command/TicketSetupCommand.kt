package ru.sablebot.module.tickets.command

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import org.springframework.stereotype.Component
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.command.model.dsl.slashCommand
import ru.sablebot.common.worker.message.model.commands.options.ApplicationCommandOptions
import ru.sablebot.module.tickets.service.ITicketConfigService
import java.util.UUID

@Component
class TicketSetupCommand(
    private val ticketConfigService: ITicketConfigService
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "ticket-setup", "Configure ticket system settings",
        CommandCategory.TICKETS, UUID.fromString("c1000001-0000-0000-0000-000000000010")
    ) {
        defaultMemberPermissions = DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)

        subcommand("enable", "Enable the ticket system", UUID.fromString("c1000001-0000-0000-0000-000000000011")) {
            executor = EnableExecutor()
        }
        subcommand("disable", "Disable the ticket system", UUID.fromString("c1000001-0000-0000-0000-000000000012")) {
            executor = DisableExecutor()
        }
        subcommand("support-channel", "Set the support channel for ticket creation", UUID.fromString("c1000001-0000-0000-0000-000000000013")) {
            executor = SupportChannelExecutor()
        }
        subcommand("category", "Set the category where ticket channels are created", UUID.fromString("c1000001-0000-0000-0000-000000000014")) {
            executor = CategoryChannelExecutor()
        }
        subcommand("transcript-channel", "Set the channel for ticket transcripts", UUID.fromString("c1000001-0000-0000-0000-000000000015")) {
            executor = TranscriptChannelExecutor()
        }
        subcommand("max-tickets", "Set maximum tickets per user", UUID.fromString("c1000001-0000-0000-0000-000000000016")) {
            executor = MaxTicketsExecutor()
        }
        subcommand("auto-close", "Set auto-close inactive days", UUID.fromString("c1000001-0000-0000-0000-000000000017")) {
            executor = AutoCloseExecutor()
        }
        subcommand("dm-on-close", "Toggle DM notifications on ticket close", UUID.fromString("c1000001-0000-0000-0000-000000000018")) {
            executor = DmOnCloseExecutor()
        }
        subcommandGroup("staff", "Manage staff roles") {
            subcommand("add", "Add a staff role", UUID.fromString("c1000001-0000-0000-0000-000000000019")) {
                executor = StaffAddExecutor()
            }
            subcommand("remove", "Remove a staff role", UUID.fromString("c1000001-0000-0000-0000-00000000001a")) {
                executor = StaffRemoveExecutor()
            }
            subcommand("list", "List all staff roles", UUID.fromString("c1000001-0000-0000-0000-00000000001b")) {
                executor = StaffListExecutor()
            }
        }
        subcommand("view", "View current ticket configuration", UUID.fromString("c1000001-0000-0000-0000-00000000001c")) {
            executor = ViewExecutor()
        }
    }

    inner class EnableExecutor : SlashCommandExecutor() {
        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            ticketConfigService.enableTickets(context.guild.idLong)
            context.reply(ephemeral = true, "✅ Ticket system enabled.")
        }
    }

    inner class DisableExecutor : SlashCommandExecutor() {
        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            ticketConfigService.disableTickets(context.guild.idLong)
            context.reply(ephemeral = true, "❌ Ticket system disabled.")
        }
    }

    inner class SupportChannelExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val channel = channel("channel", "The channel where users can create tickets")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val channel = args[options.channel]
            ticketConfigService.setSupportChannel(context.guild.idLong, channel.id)
            context.reply(ephemeral = true, "✅ Support channel set to <#${channel.id}>.")
        }
    }

    inner class CategoryChannelExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val category = channel("category", "The category where ticket channels will be created")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val category = args[options.category]
            ticketConfigService.setCategoryChannel(context.guild.idLong, category.id)
            context.reply(ephemeral = true, "✅ Ticket category set to ${category.name}.")
        }
    }

    inner class TranscriptChannelExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val channel = channel("channel", "The channel where ticket transcripts will be posted")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val channel = args[options.channel]
            ticketConfigService.setTranscriptChannel(context.guild.idLong, channel.id)
            context.reply(ephemeral = true, "✅ Transcript channel set to <#${channel.id}>.")
        }
    }

    inner class MaxTicketsExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val max = string("max", "Maximum number of tickets per user (1-10)")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val maxStr = args[options.max]
            val max = maxStr.toIntOrNull()
            if (max == null || max < 1 || max > 10) {
                context.reply(ephemeral = true, "❌ Maximum tickets must be a number between 1 and 10.")
                return
            }

            ticketConfigService.setMaxTicketsPerUser(context.guild.idLong, max)
            context.reply(ephemeral = true, "✅ Maximum tickets per user set to $max.")
        }
    }

    inner class AutoCloseExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val days = optionalString("days", "Number of inactive days before auto-close (empty to disable)")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val daysStr = args[options.days]

            if (daysStr == null) {
                ticketConfigService.setAutoCloseInactiveDays(context.guild.idLong, null)
                context.reply(ephemeral = true, "✅ Auto-close disabled.")
                return
            }

            val days = daysStr.toIntOrNull()
            if (days == null || days < 1) {
                context.reply(ephemeral = true, "❌ Days must be a positive number.")
                return
            }

            ticketConfigService.setAutoCloseInactiveDays(context.guild.idLong, days)
            context.reply(ephemeral = true, "✅ Tickets will auto-close after $days days of inactivity.")
        }
    }

    inner class DmOnCloseExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val enable = boolean("enable", "Enable or disable DM notifications on ticket close")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val enable = args[options.enable]
            ticketConfigService.setDmOnClose(context.guild.idLong, enable)
            val status = if (enable) "enabled" else "disabled"
            context.reply(ephemeral = true, "✅ DM notifications on ticket close $status.")
        }
    }

    inner class StaffAddExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val role = role("role", "The role to add as a staff role")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val role = args[options.role]
            ticketConfigService.addStaffRole(context.guild.idLong, role.id)
            context.reply(ephemeral = true, "✅ Added ${role.asMention} as a staff role.")
        }
    }

    inner class StaffRemoveExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val role = role("role", "The role to remove from staff roles")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val role = args[options.role]
            ticketConfigService.removeStaffRole(context.guild.idLong, role.id)
            context.reply(ephemeral = true, "✅ Removed ${role.asMention} from staff roles.")
        }
    }

    inner class StaffListExecutor : SlashCommandExecutor() {
        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val staffRoles = ticketConfigService.getStaffRoles(context.guild.idLong)

            if (staffRoles.isEmpty()) {
                context.reply(ephemeral = true, "No staff roles configured.")
                return
            }

            val roleList = staffRoles.joinToString("\n") { roleId ->
                "• <@&$roleId>"
            }

            context.reply(ephemeral = true) {
                embed {
                    title = "Staff Roles"
                    description = roleList
                    color = 0x5865F2
                }
            }
        }
    }

    inner class ViewExecutor : SlashCommandExecutor() {
        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val config = ticketConfigService.getConfig(context.guild.idLong)

            if (config == null) {
                context.reply(ephemeral = true, "Ticket system not configured yet. Use `/ticket-setup` subcommands to configure.")
                return
            }

            context.reply(ephemeral = true) {
                embed {
                    title = "Ticket Configuration"
                    color = 0x5865F2

                    field {
                        name = "Status"
                        value = if (config.enabled) "✅ Enabled" else "❌ Disabled"
                        inline = true
                    }

                    field {
                        name = "Max Tickets/User"
                        value = config.maxTicketsPerUser.toString()
                        inline = true
                    }

                    field {
                        name = "DM on Close"
                        value = if (config.dmOnClose) "✅ Enabled" else "❌ Disabled"
                        inline = true
                    }

                    field {
                        name = "Support Channel"
                        value = config.supportChannelId?.let { "<#$it>" } ?: "Not set"
                        inline = false
                    }

                    field {
                        name = "Ticket Category"
                        value = config.categoryChannelId?.let { "<#$it>" } ?: "Not set"
                        inline = false
                    }

                    field {
                        name = "Transcript Channel"
                        value = config.transcriptChannelId?.let { "<#$it>" } ?: "Not set"
                        inline = false
                    }

                    field {
                        name = "Auto-Close After"
                        value = config.autoCloseInactiveDays?.let { "$it days" } ?: "Disabled"
                        inline = false
                    }

                    field {
                        name = "Staff Roles"
                        value = if (config.staffRoleIds.isEmpty()) {
                            "None configured"
                        } else {
                            config.staffRoleIds.joinToString(", ") { "<@&$it>" }
                        }
                        inline = false
                    }
                }
            }
        }
    }
}
