package ru.sablebot.module.audio.service.helper

import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.exceptions.PermissionException
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Lazy
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import ru.sablebot.common.configuration.CommonConfiguration
import ru.sablebot.common.service.MusicConfigService
import ru.sablebot.common.worker.configuration.WorkerProperties
import ru.sablebot.common.worker.event.service.ContextService
import ru.sablebot.common.worker.message.service.MessageService
import ru.sablebot.common.worker.shared.service.DiscordService
import ru.sablebot.module.audio.model.FilterPreset
import ru.sablebot.module.audio.model.PlaybackInstance
import ru.sablebot.module.audio.model.RepeatMode
import ru.sablebot.module.audio.model.TrackRequest
import ru.sablebot.module.audio.service.PlayerServiceV4
import java.awt.Color
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture

@Service
class AudioMessageManager(
    @param:Qualifier(CommonConfiguration.SCHEDULER)
    private val scheduler: TaskScheduler,
    private val contextService: ContextService,
    @Lazy private val playerService: PlayerServiceV4,
    private val messageService: MessageService,
    private val discordService: DiscordService,
    private val musicConfigService: MusicConfigService,
    private val workerProperties: WorkerProperties
) {
    companion object {
        private val logger = KotlinLogging.logger {}

        private const val PROGRESS_BAR_WIDTH = 20
        private const val EMBED_COLOR = 0x5865F2
        private const val ENDED_COLOR = 0x99AAB5

        // Button custom IDs
        const val BTN_PAUSE = "audio:pause"
        const val BTN_RESUME = "audio:resume"
        const val BTN_SKIP = "audio:skip"
        const val BTN_STOP = "audio:stop"
        const val BTN_REPEAT = "audio:repeat"
        const val BTN_SHUFFLE = "audio:shuffle"
    }

    /** guildId -> panel messageId */
    private val panelMessages = ConcurrentHashMap<Long, Long>()

    /** guildId -> scheduled refresh task */
    private val updaterTasks = ConcurrentHashMap<Long, ScheduledFuture<*>>()

    // ==================== Public API (called by PlayerServiceImpl) ====================

    fun onTrackStart(request: TrackRequest?) {
        if (request == null) return

        val instance = playerService.get(request.guildId) ?: return
        val channel = request.channel() ?: return

        cancelUpdate(request.guildId)

        try {
            val existingMessageId = panelMessages[request.guildId]
            val embed = buildNowPlayingEmbed(request, instance)
            val buttons = buildControlButtons(instance)
            val actionRow = ActionRow.of(buttons)

            if (existingMessageId != null) {
                val editData = MessageEditBuilder()
                    .setEmbeds(embed)
                    .setComponents(actionRow)
                    .build()

                channel.editMessageById(existingMessageId, editData).queue(
                    { scheduleRefresh(request, instance) },
                    { error ->
                        if (error is ErrorResponseException &&
                            (error.errorResponse == ErrorResponse.UNKNOWN_MESSAGE ||
                                    error.errorResponse == ErrorResponse.MISSING_ACCESS)
                        ) {
                            panelMessages.remove(request.guildId)
                            sendNewPanel(request, instance)
                        } else {
                            logger.warn(error) { "Failed to edit panel for guild ${request.guildId}" }
                        }
                    }
                )
            } else {
                sendNewPanel(request, instance)
            }
        } catch (e: PermissionException) {
            logger.warn(e) { "No permission to send/edit panel in guild ${request.guildId}" }
        }
    }

    fun onTrackEnd(request: TrackRequest) {
        cancelUpdate(request.guildId)

        val channel = request.channel() ?: return
        val messageId = panelMessages[request.guildId] ?: return

        try {
            val embed = buildTrackEndedEmbed(request)
            val editData = MessageEditBuilder()
                .setEmbeds(embed)
                .setComponents() // remove buttons
                .build()

            channel.editMessageById(messageId, editData).queue(
                { /* success */ },
                { error -> handleEditError(request.guildId, error) }
            )
        } catch (e: PermissionException) {
            logger.warn(e) { "No permission to update ended panel for guild ${request.guildId}" }
        }
    }

    fun onTrackAdd(request: TrackRequest, instance: PlaybackInstance) {
        val channel = request.channel() ?: return
        val memberName = resolveMemberName(request.guildId, request.memberId)
        val queuePosition = instance.queueSize()

        val embed = EmbedBuilder()
            .setDescription(
                buildString {
                    append("**")
                    append(request.title ?: "Unknown Track")
                    append("**")
                    append(" by ")
                    append(request.author ?: "Unknown")
                    append("\n\nAdded to queue at position **#$queuePosition**")
                    append("\nRequested by: **$memberName**")
                }
            )
            .setColor(Color(EMBED_COLOR))
            .build()

        val message = MessageCreateBuilder()
            .setEmbeds(embed)
            .build()

        channel.sendMessage(message).queue(
            { msg ->
                // Auto-delete after 10 seconds
                msg.delete().queueAfter(10, java.util.concurrent.TimeUnit.SECONDS, null) {}
            },
            { error -> logger.debug(error) { "Failed to send track-add notification" } }
        )
    }

    fun onQueueEnd(request: TrackRequest) {
        cancelUpdate(request.guildId)

        val channel = request.channel() ?: return
        val messageId = panelMessages[request.guildId] ?: return

        try {
            val embed = EmbedBuilder()
                .setTitle("\uD83C\uDFB5 Queue Ended")
                .setDescription("All tracks have been played.")
                .setColor(Color(ENDED_COLOR))
                .build()

            val editData = MessageEditBuilder()
                .setEmbeds(embed)
                .setComponents()
                .build()

            channel.editMessageById(messageId, editData).queue(
                { panelMessages.remove(request.guildId) },
                { error -> handleEditError(request.guildId, error) }
            )
        } catch (e: PermissionException) {
            logger.warn(e) { "No permission to update queue-end panel for guild ${request.guildId}" }
        }
    }

    fun clear(guildId: Long) {
        cancelUpdate(guildId)
        panelMessages.remove(guildId)
    }

    fun cancelUpdate(guildId: Long) {
        updaterTasks.computeIfPresent(guildId) { _, task ->
            task.cancel(false)
            null
        }
    }

    fun monitor(alive: Set<Long>) {
        val dead = panelMessages.keys - alive
        dead.forEach { guildId ->
            runCatching { clear(guildId) }
                .onFailure { logger.warn(it) { "Could not clear dead updater for guild $guildId" } }
        }
    }

    /**
     * Force-refresh the panel for a guild (e.g., after repeat mode or shuffle change from button).
     */
    fun refreshPanel(guildId: Long) {
        val instance = playerService.get(guildId) ?: return
        val request = instance.currentOrNull() ?: return
        val channel = request.channel() ?: return
        val messageId = panelMessages[guildId] ?: return

        try {
            val embed = buildNowPlayingEmbed(request, instance)
            val buttons = buildControlButtons(instance)
            val editData = MessageEditBuilder()
                .setEmbeds(embed)
                .setComponents(ActionRow.of(buttons))
                .build()

            channel.editMessageById(messageId, editData).queue(
                { /* success */ },
                { error -> handleEditError(guildId, error) }
            )
        } catch (e: PermissionException) {
            logger.warn(e) { "No permission to refresh panel for guild $guildId" }
        }
    }

    // ==================== Panel Sending ====================

    private fun sendNewPanel(request: TrackRequest, instance: PlaybackInstance) {
        val channel = request.channel() ?: return
        val embed = buildNowPlayingEmbed(request, instance)
        val buttons = buildControlButtons(instance)

        val message = MessageCreateBuilder()
            .setEmbeds(embed)
            .setComponents(ActionRow.of(buttons))
            .build()

        channel.sendMessage(message).queue(
            { msg ->
                panelMessages[request.guildId] = msg.idLong
                scheduleRefresh(request, instance)
            },
            { error -> logger.warn(error) { "Failed to send panel for guild ${request.guildId}" } }
        )
    }

    // ==================== Scheduled Refresh ====================

    private fun scheduleRefresh(request: TrackRequest, instance: PlaybackInstance) {
        if (request.isStream) return

        val config = musicConfigService.getByGuildId(request.guildId)
        if (config?.autoRefresh != true) return

        val intervalMs = workerProperties.audio.panelRefreshInterval.toLong()
        val task = scheduler.scheduleWithFixedDelay({
            contextService.withContext(request.guildId) {
                refreshPanelQuietly(request.guildId)
            }
        }, Duration.ofMillis(intervalMs))

        updaterTasks.put(request.guildId, task)?.cancel(false)
    }

    private fun refreshPanelQuietly(guildId: Long) {
        try {
            val instance = playerService.get(guildId) ?: run {
                cancelUpdate(guildId)
                return
            }
            val request = instance.currentOrNull() ?: run {
                cancelUpdate(guildId)
                return
            }
            val channel = request.channel() ?: run {
                cancelUpdate(guildId)
                return
            }
            val messageId = panelMessages[guildId] ?: run {
                cancelUpdate(guildId)
                return
            }

            val embed = buildNowPlayingEmbed(request, instance)
            val buttons = buildControlButtons(instance)
            val editData = MessageEditBuilder()
                .setEmbeds(embed)
                .setComponents(ActionRow.of(buttons))
                .build()

            channel.editMessageById(messageId, editData).queue(
                { /* success */ },
                { error ->
                    if (error is ErrorResponseException) {
                        when (error.errorResponse) {
                            ErrorResponse.UNKNOWN_MESSAGE, ErrorResponse.MISSING_ACCESS -> {
                                panelMessages.remove(guildId)
                                cancelUpdate(guildId)
                            }

                            else -> logger.debug(error) { "Refresh error for guild $guildId" }
                        }
                    }
                }
            )
        } catch (e: Exception) {
            logger.debug(e) { "Error during panel refresh for guild $guildId" }
        }
    }

    // ==================== Embed Builders ====================

    private fun buildNowPlayingEmbed(
        request: TrackRequest,
        instance: PlaybackInstance
    ): net.dv8tion.jda.api.entities.MessageEmbed {
        val title = request.title ?: "Unknown Track"
        val author = request.author ?: "Unknown"
        val memberName = resolveMemberName(request.guildId, request.memberId)
        val progressLine = buildProgressLine(request, instance)

        val description = buildString {
            append("**").append(title).append("**\n")
            append("by ").append(author).append("\n\n")
            append(progressLine).append("\n\n")
            append("Requested by: **").append(memberName).append("**")
        }

        val footerText = buildFooterText(instance)

        return EmbedBuilder()
            .setTitle("\uD83C\uDFB5 Now Playing")
            .setDescription(description)
            .apply { request.artworkUrl?.let { setThumbnail(it) } }
            .setColor(Color(EMBED_COLOR))
            .setFooter(footerText)
            .build()
    }

    private fun buildTrackEndedEmbed(request: TrackRequest): net.dv8tion.jda.api.entities.MessageEmbed {
        val title = request.title ?: "Unknown Track"
        val author = request.author ?: "Unknown"
        val endReasonText = request.endReason?.let {
            messageService.getMessage("discord.command.audio.endReason.${it.name.lowercase()}")
        } ?: "Ended"

        val description = buildString {
            append("**").append(title).append("**\n")
            append("by ").append(author).append("\n\n")
            if (!request.isStream && (request.lengthMs) > 0) {
                append(formatDuration(request.lengthMs)).append(" - ")
            }
            append(endReasonText)
        }

        return EmbedBuilder()
            .setTitle("\uD83C\uDFB5 Track Ended")
            .setDescription(description)
            .apply { request.artworkUrl?.let { setThumbnail(it) } }
            .setColor(Color(ENDED_COLOR))
            .build()
    }

    // ==================== Progress Bar ====================

    private fun buildProgressLine(request: TrackRequest, instance: PlaybackInstance): String {
        if (request.isStream) {
            return "\uD83D\uDD34 LIVE"
        }

        val duration = request.lengthMs
        if (duration <= 0) return ""

        val position = instance.lastKnownPositionMs
        val progressPercent = if (duration > 0) {
            ((position.toDouble() / duration.toDouble()) * 100).toInt().coerceIn(0, 100)
        } else 0

        val filledBlocks = (progressPercent * PROGRESS_BAR_WIDTH / 100).coerceIn(0, PROGRESS_BAR_WIDTH)
        val emptyBlocks = (PROGRESS_BAR_WIDTH - filledBlocks).coerceAtLeast(0)

        val bar = "\u2593".repeat(filledBlocks) + "\u2591".repeat(emptyBlocks)
        return "$bar ${formatDuration(position)} / ${formatDuration(duration)}"
    }

    // ==================== Footer ====================

    private fun buildFooterText(instance: PlaybackInstance): String {
        val parts = mutableListOf<String>()

        parts.add("Volume: ${instance.volume}%")

        val repeatText = when (instance.mode) {
            RepeatMode.NONE -> "Off"
            RepeatMode.CURRENT -> "Track"
            RepeatMode.ALL -> "Queue"
        }
        parts.add("Repeat: $repeatText")

        parts.add("Queue: ${instance.upcomingCount()} tracks")

        val filterName = instance.activeFilter?.let {
            if (it == FilterPreset.NONE) null else it.displayName
        }
        parts.add("Filter: ${filterName ?: "None"}")

        return parts.joinToString(" | ")
    }

    // ==================== Control Buttons ====================

    private fun buildControlButtons(instance: PlaybackInstance): List<Button> {
        val isPaused = instance.player.paused

        val pauseResumeButton = if (isPaused) {
            Button.primary(BTN_RESUME, "Resume").withEmoji(Emoji.fromUnicode("\u23EF"))
        } else {
            Button.primary(BTN_PAUSE, "Pause").withEmoji(Emoji.fromUnicode("\u23EF"))
        }

        val skipButton = Button.secondary(BTN_SKIP, "Skip")
            .withEmoji(Emoji.fromUnicode("\u23ED"))
        val stopButton = Button.danger(BTN_STOP, "Stop")
            .withEmoji(Emoji.fromUnicode("\u23F9"))
        val repeatButton = Button.secondary(BTN_REPEAT, "Repeat")
            .withEmoji(Emoji.fromUnicode("\uD83D\uDD01"))
        val shuffleButton = Button.secondary(BTN_SHUFFLE, "Shuffle")
            .withEmoji(Emoji.fromUnicode("\uD83D\uDD00"))

        return listOf(pauseResumeButton, skipButton, stopButton, repeatButton, shuffleButton)
    }

    // ==================== Utility ====================

    private fun resolveMemberName(guildId: Long, userId: Long): String {
        val shardManager = discordService.shardManager
        val guild = shardManager.getGuildById(guildId)
        val user = shardManager.getUserById(userId)

        return when {
            user != null && guild != null -> guild.getMember(user)?.effectiveName ?: user.name
            user != null -> user.name
            else -> userId.toString()
        }
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

    private fun handleEditError(guildId: Long, error: Throwable) {
        if (error is ErrorResponseException) {
            when (error.errorResponse) {
                ErrorResponse.UNKNOWN_MESSAGE -> panelMessages.remove(guildId)
                ErrorResponse.MISSING_ACCESS -> {
                    panelMessages.remove(guildId)
                    cancelUpdate(guildId)
                }

                else -> logger.debug(error) { "Edit error for guild $guildId" }
            }
        }
    }
}
