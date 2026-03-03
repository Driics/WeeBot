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
class KickCommand(
    private val moderationService: IModerationService
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "kick", "Kick a user from the server",
        CommandCategory.MODERATION, UUID.fromString("b1000001-0000-0000-0000-000000000003")
    ) {
        defaultMemberPermissions = DefaultMemberPermissions.enabledFor(Permission.KICK_MEMBERS)
        executor = KickExecutor()
    }

    inner class KickExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val user = user("user", "The user to kick")
            val reason = optionalString("reason", "Reason for the kick")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val userAndMember = args[options.user]
                val target = userAndMember.member
                    ?: throw DiscordException("User is not in this server.")
                val moderator = context.member

                ModerationPreconditions.requireCanModerate(moderator, target)
                context.deferChannelMessage(false)

                val reason = args[options.reason]
                val case = moderationService.kick(context.guild, target, moderator, reason)

                context.reply(ephemeral = false, "Kicked **${target.user.name}**. (Case #${case.caseNumber})")
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }
}
