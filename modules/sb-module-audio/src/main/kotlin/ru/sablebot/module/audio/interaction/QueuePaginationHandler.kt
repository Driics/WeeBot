package ru.sablebot.module.audio.interaction

import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import ru.sablebot.common.worker.event.listeners.DiscordEventListener
import ru.sablebot.module.audio.model.PlaybackInstance
import ru.sablebot.module.audio.model.TrackRequest
import ru.sablebot.module.audio.service.PlayerServiceV4
import java.awt.Color

@Component
class QueuePaginationHandler(
    @Lazy private val playerService: PlayerServiceV4
) : DiscordEventListener() {

    companion object {
        private val logger = KotlinLogging.logger {}
        private const val QUEUE_PREFIX = "audio:queue:"
        private const val TRACKS_PER_PAGE = 10
        private const val EMBED_COLOR = 0x5865F2
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val customId = event.componentId
        if (!customId.startsWith(QUEUE_PREFIX)) return

        // Expected format: audio:queue:prev:{guildId} or audio:queue:next:{guildId}
        val parts = customId.split(":")
        if (parts.size < 4) {
            event.deferEdit().queue()
            return
        }

        val action = parts[2]   // "prev" or "next"
        val guildId = parts[3].toLongOrNull()
        if (guildId == null) {
            event.deferEdit().queue()
            return
        }

        val instance = playerService.get(guildId) ?: run {
            event.reply("No active player for this server.").setEphemeral(true).queue()
            return
        }

        val queue = instance.queueSnapshot()
        // The queue includes current track at index 0; upcoming starts at 1
        val upcoming = if (queue.size > 1) queue.subList(1, queue.size) else emptyList()
        val totalPages = if (upcoming.isEmpty()) 1 else ((upcoming.size - 1) / TRACKS_PER_PAGE) + 1

        // Extract current page from the embed footer (if available)
        val currentPage = extractCurrentPage(event)

        val newPage = when (action) {
            "prev" -> (currentPage - 1).coerceAtLeast(0)
            "next" -> (currentPage + 1).coerceAtMost(totalPages - 1)
            else -> currentPage
        }

        val embed = buildQueuePageEmbed(instance, upcoming, newPage, totalPages)
        val buttons = buildPaginationButtons(guildId, newPage, totalPages)

        val editData = MessageEditBuilder()
            .setEmbeds(embed)
            .setComponents(ActionRow.of(buttons))
            .build()

        event.editMessage(editData).queue(
            { /* success */ },
            { error -> logger.debug(error) { "Failed to update queue page" } }
        )
    }

    private fun buildQueuePageEmbed(
        instance: PlaybackInstance,
        upcoming: List<TrackRequest>,
        page: Int,
        totalPages: Int
    ): net.dv8tion.jda.api.entities.MessageEmbed {
        val current = instance.currentOrNull()

        val builder = EmbedBuilder()
            .setTitle("\uD83C\uDFB6 Queue")
            .setColor(Color(EMBED_COLOR))

        // Show currently playing track
        if (current != null) {
            val duration = if (current.isStream) "\uD83D\uDD34 LIVE" else formatDuration(current.lengthMs)
            builder.addField(
                "Now Playing",
                "**${current.title ?: "Unknown"}** by ${current.author ?: "Unknown"} [$duration]",
                false
            )
        }

        if (upcoming.isEmpty()) {
            builder.setDescription("The queue is empty.")
        } else {
            val startIndex = page * TRACKS_PER_PAGE
            val endIndex = minOf(startIndex + TRACKS_PER_PAGE, upcoming.size)
            val pageItems = upcoming.subList(startIndex, endIndex)

            val description = buildString {
                pageItems.forEachIndexed { index, request ->
                    val position = startIndex + index + 1
                    val duration = if (request.isStream) "\uD83D\uDD34 LIVE" else formatDuration(request.lengthMs)
                    append("`$position.` **${request.title ?: "Unknown"}** by ${request.author ?: "Unknown"} [$duration]\n")
                }
            }
            builder.setDescription(description)
        }

        builder.setFooter("Page ${page + 1}/$totalPages | ${upcoming.size} tracks in queue")

        return builder.build()
    }

    private fun buildPaginationButtons(guildId: Long, currentPage: Int, totalPages: Int): List<Button> {
        val prevButton = Button.secondary("audio:queue:prev:$guildId", "Previous")
            .withDisabled(currentPage <= 0)
        val nextButton = Button.secondary("audio:queue:next:$guildId", "Next")
            .withDisabled(currentPage >= totalPages - 1)

        return listOf(prevButton, nextButton)
    }

    private fun extractCurrentPage(event: ButtonInteractionEvent): Int {
        val footer = event.message.embeds.firstOrNull()?.footer?.text ?: return 0

        // Footer format: "Page 2/5 | 42 tracks in queue"
        val match = Regex("""Page (\d+)/""").find(footer)
        return match?.groupValues?.get(1)?.toIntOrNull()?.minus(1)?.coerceAtLeast(0) ?: 0
    }

    private fun formatDuration(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
}
