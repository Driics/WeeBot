package ru.sablebot.module.moderation.command

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import org.springframework.stereotype.Component
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.model.exception.DiscordException
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.command.model.dsl.slashCommand
import ru.sablebot.common.worker.message.model.commands.options.ApplicationCommandOptions
import ru.sablebot.module.moderation.service.IModerationService
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

@Component
class WarningsCommand(
    private val moderationService: IModerationService
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "warnings", "View warnings for a user",
        CommandCategory.MODERATION, UUID.fromString("b1000001-0000-0000-0000-000000000005")
    ) {
        defaultMemberPermissions = DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS)
        executor = WarningsExecutor()
    }

    inner class WarningsExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val user = user("user", "The user to view warnings for")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val userAndMember = args[options.user]
                val targetUser = userAndMember.user

                val warnings = moderationService.getWarnings(context.guild.idLong, targetUser.id)

                if (warnings.isEmpty()) {
                    context.reply(ephemeral = false, "**${targetUser.name}** has no warnings.")
                    return
                }

                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC)

                context.reply(ephemeral = false) {
                    embed {
                        title = "Warnings for ${targetUser.name}"
                        description = buildString {
                            warnings.forEach { warning ->
                                val status = if (warning.active) "Active" else "Expired"
                                val timestamp = formatter.format(warning.createdAt)
                                appendLine("**Case #${warning.caseNumber}** [$status]")
                                appendLine("Reason: ${warning.reason ?: "No reason provided"}")
                                appendLine("Moderator: ${warning.moderatorName} | $timestamp")
                                appendLine()
                            }
                        }
                        footer {
                            name = "${warnings.count { it.active }} active warning(s) out of ${warnings.size} total"
                        }
                        color = 0xFFA500
                    }
                }
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }
}
