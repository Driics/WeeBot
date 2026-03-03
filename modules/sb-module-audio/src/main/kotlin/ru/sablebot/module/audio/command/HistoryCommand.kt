package ru.sablebot.module.audio.command

import org.springframework.stereotype.Component
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.model.exception.DiscordException
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.command.model.dsl.slashCommand
import ru.sablebot.module.audio.service.PlayerServiceV4
import java.util.*

@Component
class HistoryCommand(
    private val playerService: PlayerServiceV4
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "history", "Show recently played tracks",
        CommandCategory.MUSIC, UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567821")
    ) {
        executor = HistoryExecutor()
    }

    inner class HistoryExecutor : SlashCommandExecutor() {
        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val guild = context.guild
                val instance = AudioCommandPreconditions.requireActivePlayer(guild, playerService)
                val queue = instance.queueSnapshot()

                if (queue.isEmpty()) {
                    context.reply(ephemeral = true, "No tracks have been played yet.")
                    return
                }

                // Show the full queue as history (tracks already played + current)
                val display = queue.take(15)

                context.reply(ephemeral = false) {
                    embed {
                        title = "Recently Played"
                        description = buildString {
                            display.forEachIndexed { i, track ->
                                val marker = if (i == 0) " (current)" else ""
                                appendLine("`${i + 1}.` ${track.title ?: "Unknown"} - ${formatDuration(track.lengthMs)}$marker")
                            }
                            if (queue.size > 15) {
                                appendLine()
                                appendLine("...and ${queue.size - 15} more")
                            }
                        }
                        color = 0x2F3136
                    }
                }
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }

        private fun formatDuration(ms: Long): String {
            val totalSeconds = ms / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
    }
}
