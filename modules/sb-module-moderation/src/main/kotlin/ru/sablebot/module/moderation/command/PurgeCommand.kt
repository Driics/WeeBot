package ru.sablebot.module.moderation.command

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
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
class PurgeCommand(
    private val moderationService: IModerationService
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "purge", "Bulk delete messages from a channel",
        CommandCategory.MODERATION, UUID.fromString("b1000001-0000-0000-0000-000000000009")
    ) {
        defaultMemberPermissions = DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE)
        executor = PurgeExecutor()
    }

    inner class PurgeExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val count = string("count", "Number of messages to delete (1-100)")
            val user = optionalUser("user", "Only delete messages from this user")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                context.deferChannelMessage(false)
                val countStr = args[options.count]
                val count = countStr.toIntOrNull()
                    ?: throw DiscordException("Count must be a number between 1 and 100.")
                if (count < 1 || count > 100) {
                    throw DiscordException("Count must be between 1 and 100.")
                }

                val filterUser = args[options.user]?.user
                val channel = context.channel as TextChannel

                val deleted = moderationService.purgeMessages(channel, count, filterUser)
                context.reply(ephemeral = false, "Purged **$deleted** messages.")
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }
}
