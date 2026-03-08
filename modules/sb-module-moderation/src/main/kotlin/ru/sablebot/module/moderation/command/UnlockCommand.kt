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
class UnlockCommand(
    private val moderationService: IModerationService
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "unlock", "Unlock a previously locked channel",
        CommandCategory.MODERATION, UUID.fromString("b1000001-0000-0000-0000-00000000000c")
    ) {
        defaultMemberPermissions = DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL)
        executor = UnlockExecutor()
    }

    inner class UnlockExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val channel = optionalChannel("channel", "The channel to unlock (defaults to current)")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                context.deferChannelMessage(false)
                val targetChannel = args[options.channel] as? TextChannel
                    ?: context.channel as TextChannel

                moderationService.unlockChannel(targetChannel, context.member)
                context.reply(ephemeral = false, "Unlocked **#${targetChannel.name}**.")
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }
}
