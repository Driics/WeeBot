package ru.sablebot.module.moderation.command

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import org.springframework.stereotype.Component
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.model.ModerationCaseType
import ru.sablebot.common.model.exception.DiscordException
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.command.model.dsl.slashCommand
import ru.sablebot.common.worker.message.model.commands.options.ApplicationCommandOptions
import ru.sablebot.module.moderation.model.DurationParser
import ru.sablebot.module.moderation.service.IModerationService
import java.util.UUID

@Component
class CaseCommand(
    private val moderationService: IModerationService
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "case", "View details of a moderation case",
        CommandCategory.MODERATION, UUID.fromString("b1000001-0000-0000-0000-00000000000d")
    ) {
        defaultMemberPermissions = DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS)
        executor = CaseExecutor()
    }

    inner class CaseExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val number = string("number", "The case number to look up")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val numberStr = args[options.number]
                val caseNumber = numberStr.toIntOrNull()
                    ?: throw DiscordException("Case number must be a valid integer.")

                val case = moderationService.getCase(context.guild.idLong, caseNumber)
                    ?: throw DiscordException("Case #$caseNumber not found.")

                context.reply(ephemeral = false) {
                    embed {
                        title = "Case #${case.caseNumber} — ${case.actionType.name}"
                        color = caseColor(case.actionType)
                        description = buildString {
                            appendLine("**User:** ${case.targetName} (`${case.targetId}`)")
                            appendLine("**Moderator:** ${case.moderatorName} (`${case.moderatorId}`)")
                            appendLine("**Reason:** ${case.reason ?: "No reason provided"}")
                            if (case.duration != null) {
                                appendLine("**Duration:** ${DurationParser.format(case.duration!!)}")
                            }
                            appendLine("**Active:** ${if (case.active) "Yes" else "No"}")
                        }
                        timestamp = case.createdAt
                    }
                }
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }

        private fun caseColor(type: ModerationCaseType): Int = when (type) {
            ModerationCaseType.BAN -> 0xE74C3C
            ModerationCaseType.UNBAN -> 0x2ECC71
            ModerationCaseType.KICK -> 0xE67E22
            ModerationCaseType.WARN -> 0xF1C40F
            ModerationCaseType.MUTE -> 0x9B59B6
            ModerationCaseType.UNMUTE -> 0x1ABC9C
            ModerationCaseType.TIMEOUT -> 0xE91E63
            ModerationCaseType.UNTIMEOUT -> 0x3498DB
        }
    }
}
