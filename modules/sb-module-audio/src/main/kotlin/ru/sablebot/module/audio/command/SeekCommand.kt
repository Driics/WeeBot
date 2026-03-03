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
import ru.sablebot.module.audio.service.PlayerServiceV4
import java.util.*

@Component
class SeekCommand(
    private val playerService: PlayerServiceV4
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "seek", "Seek to a position in the current track",
        CommandCategory.MUSIC, UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567811")
    ) {
        executor = SeekExecutor()
    }

    inner class SeekExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val position = string("position", "Position to seek to (e.g. 1:30 or 90)")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val member = context.member
                val guild = context.guild
                AudioCommandPreconditions.requireSameChannel(member, guild, playerService)
                AudioCommandPreconditions.requireActivePlayer(guild, playerService)

                context.deferChannelMessage(false)

                val input = args[options.position]
                val positionMs = parsePosition(input)
                    ?: throw DiscordException("Invalid position format. Use `1:30` or `90` (seconds).")

                val seeked = playerService.seek(guild, positionMs)
                if (seeked) {
                    context.reply(ephemeral = false, "Seeked to **${formatDuration(positionMs)}**.")
                } else {
                    context.reply(ephemeral = true, "Unable to seek in the current track.")
                }
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }

        private fun parsePosition(input: String): Long? {
            // Try mm:ss or hh:mm:ss format
            val parts = input.split(":")
            return when (parts.size) {
                1 -> parts[0].trim().toLongOrNull()?.times(1000)
                2 -> {
                    val minutes = parts[0].trim().toLongOrNull() ?: return null
                    val seconds = parts[1].trim().toLongOrNull() ?: return null
                    (minutes * 60 + seconds) * 1000
                }

                3 -> {
                    val hours = parts[0].trim().toLongOrNull() ?: return null
                    val minutes = parts[1].trim().toLongOrNull() ?: return null
                    val seconds = parts[2].trim().toLongOrNull() ?: return null
                    (hours * 3600 + minutes * 60 + seconds) * 1000
                }

                else -> null
            }
        }

        private fun formatDuration(ms: Long): String {
            val totalSeconds = ms / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
            else "%d:%02d".format(minutes, seconds)
        }
    }
}
