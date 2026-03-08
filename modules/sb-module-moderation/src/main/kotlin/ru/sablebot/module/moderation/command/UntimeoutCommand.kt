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
class UntimeoutCommand(
    private val moderationService: IModerationService
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "untimeout", "Remove a timeout from a member",
        CommandCategory.MODERATION, UUID.fromString("b1000001-0000-0000-0000-000000000008")
    ) {
        defaultMemberPermissions = DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS)
        executor = UntimeoutExecutor()
    }

    inner class UntimeoutExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val user = user("user", "The member to remove timeout from")
            val reason = optionalString("reason", "Reason for removing the timeout")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                context.deferChannelMessage(false)
                val target = args[options.user].member
                    ?: throw DiscordException("Could not resolve the member.")
                val reason = args[options.reason]

                ModerationPreconditions.requireCanModerate(context.member, target)

                val case = moderationService.removeTimeout(context.guild, target, context.member, reason)
                context.reply(
                    ephemeral = false,
                    "Removed timeout from **${target.user.name}**. (Case #${case.caseNumber})"
                )
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }
}
