package ru.sablebot.module.audio.command

import org.springframework.stereotype.Component
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.model.exception.DiscordException
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.command.model.dsl.slashCommand
import ru.sablebot.common.worker.message.model.commands.options.ApplicationCommandOptions
import ru.sablebot.common.worker.message.model.commands.options.StringDiscordOptionReference
import ru.sablebot.module.audio.model.RepeatMode
import ru.sablebot.module.audio.service.PlayerServiceV4
import java.util.*

@Component
class RepeatCommand(
    private val playerService: PlayerServiceV4
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "repeat", "Set the repeat mode",
        CommandCategory.MUSIC, UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567809")
    ) {
        executor = RepeatExecutor()
    }

    inner class RepeatExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val mode = string("mode", "Repeat mode") {
                choices += StringDiscordOptionReference.Choice.RawChoice("None", "NONE")
                choices += StringDiscordOptionReference.Choice.RawChoice("Track", "CURRENT")
                choices += StringDiscordOptionReference.Choice.RawChoice("Queue", "ALL")
            }
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val member = context.member
                val guild = context.guild
                AudioCommandPreconditions.requireSameChannel(member, guild, playerService)
                val instance = AudioCommandPreconditions.requireActivePlayer(guild, playerService)

                val modeStr = args[options.mode]
                val repeatMode = try {
                    RepeatMode.valueOf(modeStr)
                } catch (_: IllegalArgumentException) {
                    throw DiscordException("Invalid repeat mode. Use: none, track, or queue.")
                }

                instance.mode = repeatMode
                val displayName = when (repeatMode) {
                    RepeatMode.NONE -> "off"
                    RepeatMode.CURRENT -> "track"
                    RepeatMode.ALL -> "queue"
                }
                context.reply(ephemeral = false, "Repeat mode set to **$displayName**.")
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }
}
