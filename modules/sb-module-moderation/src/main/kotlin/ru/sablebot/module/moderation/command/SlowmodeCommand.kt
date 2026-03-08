package ru.sablebot.module.moderation.command

import dev.minn.jda.ktx.coroutines.await
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
import java.util.UUID

@Component
class SlowmodeCommand : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "slowmode", "Set the slowmode delay for this channel",
        CommandCategory.MODERATION, UUID.fromString("b1000001-0000-0000-0000-00000000000a")
    ) {
        defaultMemberPermissions = DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL)
        executor = SlowmodeExecutor()
    }

    inner class SlowmodeExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val seconds = string("seconds", "Slowmode delay in seconds (0-21600, 0 to disable)")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                context.deferChannelMessage(false)
                val secondsStr = args[options.seconds]
                val seconds = secondsStr.toIntOrNull()
                    ?: throw DiscordException("Seconds must be a number between 0 and 21600.")
                if (seconds < 0 || seconds > 21600) {
                    throw DiscordException("Seconds must be between 0 and 21600.")
                }

                val channel = context.channel as TextChannel
                channel.manager.setSlowmode(seconds).await()

                if (seconds == 0) {
                    context.reply(ephemeral = false, "Slowmode disabled.")
                } else {
                    context.reply(ephemeral = false, "Slowmode set to **$seconds** seconds.")
                }
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }
}
