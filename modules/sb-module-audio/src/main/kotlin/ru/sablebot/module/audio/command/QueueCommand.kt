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
class QueueCommand(
    private val playerService: PlayerServiceV4
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "queue", "Show the current queue",
        CommandCategory.MUSIC, UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567806")
    ) {
        executor = QueueExecutor()
    }

    inner class QueueExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val page = optionalString("page", "Page number")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val guild = context.guild
                val instance = AudioCommandPreconditions.requireActivePlayer(guild, playerService)
                val current = instance.currentOrNull()
                val queue = instance.queueSnapshot()
                val pageNum = args[options.page]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val tracksPerPage = 10
                // Queue without the current track
                val upcoming = if (queue.isNotEmpty()) queue.drop(1) else emptyList()
                val totalPages = ((upcoming.size + tracksPerPage - 1) / tracksPerPage).coerceAtLeast(1)
                val page = pageNum.coerceAtMost(totalPages)
                val startIndex = (page - 1) * tracksPerPage
                val pageItems = upcoming.drop(startIndex).take(tracksPerPage)

                context.reply(ephemeral = false) {
                    embed {
                        title = "Queue"
                        color = 0x2F3136

                        if (current != null) {
                            description = buildString {
                                appendLine("**Now Playing:**")
                                appendLine("${current.title ?: "Unknown"} - ${formatDuration(current.lengthMs)}")
                                appendLine()
                                if (pageItems.isNotEmpty()) {
                                    appendLine("**Up Next:**")
                                    pageItems.forEachIndexed { i, track ->
                                        appendLine(
                                            "`${startIndex + i + 1}.` ${track.title ?: "Unknown"} - ${
                                                formatDuration(
                                                    track.lengthMs
                                                )
                                            }"
                                        )
                                    }
                                } else if (upcoming.isEmpty()) {
                                    appendLine("No upcoming tracks.")
                                }
                            }
                        } else {
                            description = "The queue is empty."
                        }

                        footer {
                            name =
                                "Page $page/$totalPages | ${upcoming.size} tracks in queue | Repeat: ${instance.mode.name.lowercase()}"
                        }
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
