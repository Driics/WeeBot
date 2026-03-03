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
import ru.sablebot.module.moderation.model.DurationParser
import ru.sablebot.module.moderation.service.IModerationService
import java.util.UUID

@Component
class TimeoutCommand(
    private val moderationService: IModerationService
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "timeout", "Timeout a member",
        CommandCategory.MODERATION, UUID.fromString("b1000001-0000-0000-0000-000000000007")
    ) {
        defaultMemberPermissions = DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS)
        executor = TimeoutExecutor()
    }

    inner class TimeoutExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val user = user("user", "The member to timeout")
            val duration = string("duration", "Duration (e.g. 1h30m, 2d, 30m)")
            val reason = optionalString("reason", "Reason for the timeout")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                context.deferChannelMessage(false)
                val target = args[options.user].member
                    ?: throw DiscordException("Could not resolve the member.")
                val durationStr = args[options.duration]
                val reason = args[options.reason]

                val duration = DurationParser.parse(durationStr)
                    ?: throw DiscordException("Invalid duration format. Use e.g. `1h30m`, `2d`, `30m`.")

                ModerationPreconditions.requireCanModerate(context.member, target)

                val case = moderationService.timeout(
                    context.guild, target, context.member, duration.toMillis(), reason
                )
                context.reply(
                    ephemeral = false,
                    "Timed out **${target.user.name}** for **${DurationParser.format(duration.toMillis())}**. (Case #${case.caseNumber})"
                )
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }
}
