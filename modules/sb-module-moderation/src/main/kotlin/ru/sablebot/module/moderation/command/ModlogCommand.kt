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
import java.util.UUID

@Component
class ModlogCommand(
    private val moderationService: IModerationService
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "modlog", "View moderation history for a user",
        CommandCategory.MODERATION, UUID.fromString("b1000001-0000-0000-0000-00000000000e")
    ) {
        defaultMemberPermissions = DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS)
        executor = ModlogExecutor()
    }

    inner class ModlogExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val user = user("user", "The user to view moderation history for")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val targetUser = args[options.user].user
                val cases = moderationService.getModLog(context.guild.idLong, targetUser.id)

                if (cases.isEmpty()) {
                    context.reply(ephemeral = true, "No moderation history for **${targetUser.name}**.")
                    return
                }

                val display = cases.take(10)

                context.reply(ephemeral = false) {
                    embed {
                        title = "Moderation Log — ${targetUser.name}"
                        description = buildString {
                            display.forEach { case ->
                                appendLine("`#${case.caseNumber}` **${case.actionType.name}** — ${case.reason ?: "No reason"} (<t:${case.createdAt.epochSecond}:R>)")
                            }
                            if (cases.size > 10) {
                                appendLine()
                                appendLine("...and ${cases.size - 10} more cases")
                            }
                        }
                        color = 0x2F3136
                    }
                }
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }
}
