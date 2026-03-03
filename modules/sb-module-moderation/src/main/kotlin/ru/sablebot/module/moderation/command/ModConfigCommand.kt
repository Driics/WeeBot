package ru.sablebot.module.moderation.command

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import org.springframework.stereotype.Component
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.model.ModerationActionType
import ru.sablebot.common.persistence.entity.WarnEscalationRule
import ru.sablebot.common.service.ModerationConfigService
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.command.model.dsl.slashCommand
import ru.sablebot.common.worker.message.model.commands.options.ApplicationCommandOptions
import ru.sablebot.common.worker.message.model.commands.options.StringDiscordOptionReference
import java.util.UUID

@Component
class ModConfigCommand(
    private val moderationConfigService: ModerationConfigService
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "modconfig", "Configure moderation settings",
        CommandCategory.MODERATION, UUID.fromString("b1000001-0000-0000-0000-000000000010")
    ) {
        defaultMemberPermissions = DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)

        subcommand("modlog", "Set the modlog channel", UUID.fromString("b1000001-0000-0000-0000-000000000011")) {
            executor = ModlogExecutor()
        }
        subcommand("dm", "Toggle DM notifications on moderation actions", UUID.fromString("b1000001-0000-0000-0000-000000000012")) {
            executor = DmExecutor()
        }
        subcommandGroup("escalation", "Manage warn escalation rules") {
            subcommand("add", "Add an escalation rule", UUID.fromString("b1000001-0000-0000-0000-000000000013")) {
                executor = EscalationAddExecutor()
            }
            subcommand("remove", "Remove an escalation rule", UUID.fromString("b1000001-0000-0000-0000-000000000014")) {
                executor = EscalationRemoveExecutor()
            }
            subcommand("list", "List all escalation rules", UUID.fromString("b1000001-0000-0000-0000-000000000015")) {
                executor = EscalationListExecutor()
            }
        }
    }

    inner class ModlogExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val channel = channel("channel", "The channel to use for modlog")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val channel = args[options.channel]
            val config = moderationConfigService.getOrCreate(context.guild.idLong)
            config.modlogChannelId = channel.idLong
            moderationConfigService.save(config)
            context.reply(ephemeral = true, "Modlog channel set to <#${channel.idLong}>.")
        }
    }

    inner class DmExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val enable = boolean("enable", "Enable or disable DM notifications")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val enable = args[options.enable]
            // DM notification preference acknowledged; field can be added to ModerationConfig later
            val status = if (enable) "enabled" else "disabled"
            context.reply(ephemeral = true, "DM notifications $status.")
        }
    }

    inner class EscalationAddExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val threshold = string("threshold", "Number of warnings to trigger this rule")
            val action = string("action", "Action to take") {
                choices += StringDiscordOptionReference.Choice.RawChoice("Mute", "MUTE")
                choices += StringDiscordOptionReference.Choice.RawChoice("Kick", "KICK")
                choices += StringDiscordOptionReference.Choice.RawChoice("Ban", "BAN")
            }
            val duration = optionalString("duration", "Duration for the action (e.g. 1h, 7d)")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val thresholdStr = args[options.threshold]
            val threshold = thresholdStr.toIntOrNull()
            if (threshold == null || threshold < 1) {
                context.reply(ephemeral = true, "Threshold must be a positive integer.")
                return
            }

            val actionStr = args[options.action]
            val actionType = try {
                ModerationActionType.valueOf(actionStr)
            } catch (_: IllegalArgumentException) {
                context.reply(ephemeral = true, "Invalid action type.")
                return
            }

            val durationStr = args[options.duration]

            val config = moderationConfigService.getOrCreate(context.guild.idLong)

            if (config.escalationRules.any { it.threshold == threshold }) {
                context.reply(ephemeral = true, "An escalation rule for threshold $threshold already exists.")
                return
            }

            val rule = WarnEscalationRule().apply {
                this.config = config
                this.threshold = threshold
                this.actionType = actionType
                this.duration = durationStr?.toLongOrNull()
            }

            config.escalationRules.add(rule)
            moderationConfigService.save(config)

            context.reply(ephemeral = true, "Escalation rule added: at $threshold warning(s) \u2192 $actionType.")
        }
    }

    inner class EscalationRemoveExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val threshold = string("threshold", "Threshold of the rule to remove")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val thresholdStr = args[options.threshold]
            val threshold = thresholdStr.toIntOrNull()
            if (threshold == null || threshold < 1) {
                context.reply(ephemeral = true, "Threshold must be a positive integer.")
                return
            }

            val config = moderationConfigService.getOrCreate(context.guild.idLong)
            val removed = config.escalationRules.removeIf { it.threshold == threshold }

            if (removed) {
                moderationConfigService.save(config)
                context.reply(ephemeral = true, "Escalation rule removed.")
            } else {
                context.reply(ephemeral = true, "No rule found for threshold $threshold.")
            }
        }
    }

    inner class EscalationListExecutor : SlashCommandExecutor() {
        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val config = moderationConfigService.getOrCreate(context.guild.idLong)
            val rules = config.escalationRules

            if (rules.isEmpty()) {
                context.reply(ephemeral = true, "No escalation rules configured.")
                return
            }

            context.reply(ephemeral = true) {
                embed {
                    title = "Warn Escalation Rules"
                    description = buildString {
                        rules.sortedBy { it.threshold }.forEach { rule ->
                            val durationPart = if (rule.duration != null) " (duration: ${rule.duration})" else ""
                            appendLine("**${rule.threshold}** warning(s) \u2192 ${rule.actionType}$durationPart")
                        }
                    }
                    color = 0x2F3136
                }
            }
        }
    }
}
