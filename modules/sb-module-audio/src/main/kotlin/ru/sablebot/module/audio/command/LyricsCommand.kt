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
import ru.sablebot.module.audio.service.ILyricsService
import ru.sablebot.module.audio.service.PlayerServiceV4
import java.util.*

@Component
class LyricsCommand(
    private val lyricsService: ILyricsService,
    private val playerService: PlayerServiceV4
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "lyrics", "Show lyrics for the current or specified track",
        CommandCategory.MUSIC, UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567819")
    ) {
        executor = LyricsExecutor()
    }

    inner class LyricsExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val query = optionalString("query", "Song name to search lyrics for")
            val page = optionalString("page", "Page number for long lyrics")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val queryParam = args[options.query]
                val pageNum = args[options.page]?.toIntOrNull()?.coerceAtLeast(1) ?: 1

                // Fetch lyrics based on query or current track
                val result = if (queryParam != null) {
                    // Search by user-provided query
                    lyricsService.searchLyrics(queryParam)
                } else {
                    // Get current track from player
                    val guild = context.guild
                    val instance = AudioCommandPreconditions.requireActivePlayer(guild, playerService)
                    val current = instance.currentOrNull()
                        ?: throw DiscordException("No track is currently playing. Please specify a search query.")

                    val trackName = current.title ?: throw DiscordException("Unable to determine track information.")
                    val artistName = current.author

                    // Use getLyrics if we have both track and artist, otherwise searchLyrics
                    if (artistName != null) {
                        lyricsService.getLyrics(trackName, artistName)
                    } else {
                        lyricsService.searchLyrics(trackName)
                    }
                }

                if (result == null || result.plainLyrics == null) {
                    throw DiscordException("Lyrics not found for this track. Try a different search query.")
                }

                // Split lyrics into pages (max 4000 chars per page for safety)
                val lyrics = result.plainLyrics
                val charsPerPage = 4000
                val pages = lyrics.chunked(charsPerPage)
                val totalPages = pages.size
                val page = pageNum.coerceAtMost(totalPages)
                val pageContent = pages[page - 1]

                context.reply(ephemeral = false) {
                    embed {
                        title = "Lyrics: ${result.trackName}"
                        description = pageContent
                        color = 0x2F3136

                        footer {
                            name = buildString {
                                append("Artist: ${result.artistName}")
                                if (totalPages > 1) {
                                    append(" | Page $page/$totalPages")
                                }
                                append(" | Source: LRCLIB")
                            }
                        }
                    }
                }
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }
}
