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
class NowPlayingCommand(
    private val playerService: PlayerServiceV4
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "nowplaying", "Show the currently playing track",
        CommandCategory.MUSIC, UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567807")
    ) {
        executor = NowPlayingExecutor()
    }

    inner class NowPlayingExecutor : SlashCommandExecutor() {
        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val guild = context.guild
                val instance = AudioCommandPreconditions.requireActivePlayer(guild, playerService)
                val current = instance.currentOrNull()
                    ?: throw DiscordException("Nothing is currently playing.")

                val positionMs = instance.lastKnownPositionMs
                val durationMs = current.lengthMs
                val progress = if (durationMs > 0 && !current.isStream) {
                    val filled = ((positionMs.toDouble() / durationMs) * 20).toInt().coerceIn(0, 20)
                    val bar = "\u25AC".repeat(filled) + "\u25CB" + "\u25AC".repeat(20 - filled)
                    "`${formatDuration(positionMs)}` $bar `${formatDuration(durationMs)}`"
                } else if (current.isStream) {
                    "LIVE"
                } else {
                    "`${formatDuration(positionMs)}` / `${formatDuration(durationMs)}`"
                }

                context.reply(ephemeral = false) {
                    embed {
                        title = "Now Playing"
                        description = buildString {
                            appendLine("**${current.title ?: "Unknown"}**")
                            appendLine("by ${current.author ?: "Unknown"}")
                            appendLine()
                            appendLine(progress)
                            appendLine()
                            appendLine("Volume: ${instance.volume}% | Repeat: ${instance.mode.name.lowercase()}")
                            val filter = instance.activeFilter
                            if (filter != null && filter != ru.sablebot.module.audio.model.FilterPreset.NONE) {
                                appendLine("Filter: ${filter.displayName}")
                            }
                        }
                        color = 0x2F3136
                        if (current.artworkUrl != null) {
                            thumbnail = current.artworkUrl
                        }
                        if (current.uri != null) {
                            url = current.uri
                        }
                    }
                }
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
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
