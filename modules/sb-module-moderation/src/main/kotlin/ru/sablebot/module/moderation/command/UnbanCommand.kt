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
class UnbanCommand(
    private val moderationService: IModerationService
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "unban", "Unban a user from the server",
        CommandCategory.MODERATION, UUID.fromString("b1000001-0000-0000-0000-000000000002")
    ) {
        defaultMemberPermissions = DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS)
        executor = UnbanExecutor()
    }

    inner class UnbanExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val user = user("user", "The user to unban")
            val reason = optionalString("reason", "Reason for the unban")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val userAndMember = args[options.user]
                val targetUser = userAndMember.user
                val moderator = context.member

                context.deferChannelMessage(false)

                val reason = args[options.reason]
                val case = moderationService.unban(context.guild, targetUser, moderator, reason)

                context.reply(ephemeral = false, "Unbanned **${targetUser.name}**. (Case #${case.caseNumber})")
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }
}
