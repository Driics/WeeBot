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
class ClearWarningsCommand(
    private val moderationService: IModerationService
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "clear-warnings", "Clear all warnings for a user",
        CommandCategory.MODERATION, UUID.fromString("b1000001-0000-0000-0000-000000000006")
    ) {
        defaultMemberPermissions = DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS)
        executor = ClearWarningsExecutor()
    }

    inner class ClearWarningsExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val user = user("user", "The user to clear warnings for")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val userAndMember = args[options.user]
                val targetUser = userAndMember.user

                context.deferChannelMessage(false)

                val cleared = moderationService.clearWarnings(context.guild.idLong, targetUser.id)

                if (cleared == 0) {
                    context.reply(ephemeral = false, "**${targetUser.name}** has no warnings to clear.")
                } else {
                    context.reply(ephemeral = false, "Cleared **$cleared** warning(s) for **${targetUser.name}**.")
                }
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }
}
