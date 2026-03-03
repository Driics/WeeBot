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
class BanCommand(
    private val moderationService: IModerationService
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "ban", "Ban a user from the server",
        CommandCategory.MODERATION, UUID.fromString("b1000001-0000-0000-0000-000000000001")
    ) {
        defaultMemberPermissions = DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS)
        executor = BanExecutor()
    }

    inner class BanExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val user = user("user", "The user to ban")
            val reason = optionalString("reason", "Reason for the ban")
            val duration = optionalString("duration", "Ban duration (e.g. 7d, 1w)")
            val deleteDays = optionalString("delete_days", "Days of messages to delete (0-7)")
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
                val durationStr = args[options.duration]
                val deleteDaysStr = args[options.deleteDays]

                val durationMillis = durationStr?.let {
                    DurationParser.parse(it)?.toMillis()
                        ?: throw DiscordException("Invalid duration format. Use e.g. 7d, 1w, 12h.")
                }

                val deleteDays = deleteDaysStr?.let {
                    val days = it.toIntOrNull() ?: throw DiscordException("delete_days must be a number (0-7).")
                    if (days !in 0..7) throw DiscordException("delete_days must be between 0 and 7.")
                    days
                }

                val case = moderationService.ban(context.guild, target, moderator, reason, durationMillis, deleteDays)

                val durationDisplay = if (durationMillis != null) " for ${DurationParser.format(durationMillis)}" else ""
                context.reply(ephemeral = false, "Banned **${target.user.name}**$durationDisplay. (Case #${case.caseNumber})")
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }
}
