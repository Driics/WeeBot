package ru.sablebot.module.audio.service.helper

import dev.arbjerg.lavalink.client.LavalinkNode
import dev.arbjerg.lavalink.client.player.LavalinkPlayer
import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.exceptions.PermissionException
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.utils.messages.MessageEditData
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.TaskScheduler
import ru.sablebot.common.configuration.CommonConfiguration
import ru.sablebot.common.configuration.CommonProperties
import ru.sablebot.common.service.MusicConfigService
import ru.sablebot.common.worker.configuration.WorkerProperties
import ru.sablebot.common.worker.event.service.ContextService
import ru.sablebot.common.worker.feature.service.FeatureSetService
import ru.sablebot.common.worker.message.service.MessageService
import ru.sablebot.common.worker.shared.service.DiscordService
import ru.sablebot.module.audio.model.PlaybackInstance
import ru.sablebot.module.audio.model.RepeatMode
import ru.sablebot.module.audio.model.TrackRequest
import ru.sablebot.module.audio.service.ILavalinkV4AudioService
import ru.sablebot.module.audio.utils.MessageController
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.roundToInt

class AudioMessageManager(
    @param:Qualifier(CommonConfiguration.SCHEDULER)
    private val scheduler: TaskScheduler,
    private val contextService: ContextService,
    private val audioService: ILavalinkV4AudioService,
    private val messageService: MessageService,
    private val discordService: DiscordService,
    private val featureSetService: FeatureSetService,
    private val musicConfigService: MusicConfigService,
    private val commonProperties: CommonProperties,
    private val workerProperties: WorkerProperties
) {
    companion object {
        private val logger = KotlinLogging.logger {}

        private const val MAX_SHORT_QUEUE = 5
        private const val DEFAULT_VOLUME = 100
        private const val MIN_LOAD_PERCENT = 0
        private const val MAX_LOAD_PERCENT = 100
        private const val PAUSE_EMOJI = "⏸"

        // Discord formatting constants
        private const val TIMESTAMP_START = "`"
        private const val TIMESTAMP_END = "`"
        private const val ZERO_WIDTH_SPACE = "\u200B"
        private const val EMPTY_SYMBOL = ""

        // Icons for queue display
        private const val ICON_STREAM = "🔴"
        private const val ICON_PLAYING = "▶️"
    }

    private val updaterTasks = ConcurrentHashMap<Long, ScheduledFuture<*>>()
    private val controllers = ConcurrentHashMap<Long, MessageController>()
    private val guildLocks = ConcurrentHashMap<Long, ReentrantLock>()

    fun clear(guildId: Long) {
        cancelUpdate(guildId)
        controllers.remove(guildId)
    }

    fun cancelUpdate(request: TrackRequest) = cancelUpdate(request.guildId)

    fun cancelUpdate(guildId: Long) {
        updaterTasks.computeIfPresent(guildId) { _, task ->
            task.cancel(false)
            null
        }
    }

    fun monitor(alive: Set<Long>) {
        val dead = updaterTasks.keys - alive
        dead.forEach { guildId ->
            runCatching { clear(guildId) }
                .onFailure { logger.warn(it) { "Could not clear dead updater for guild $guildId" } }
        }
    }

    fun updateMessage(request: TrackRequest) {
        val channel = request.channel() ?: run {
            cancelUpdate(request)
            return
        }

        try {
            if (request.resetMessage) {
                sendResetMessage(request)
                return
            }

            controllers[request.guildId]?.executeForMessage(
                { message ->
                    message.editMessage(MessageEditData.fromEmbeds(getPlayMessage(request).build())).queue(
                        { },
                        { throwable ->
                            if (throwable is ErrorResponseException) {
                                handleUpdateError(request, throwable)
                            }
                        }
                    )
                }
            )
        } catch (e: PermissionException) {
            logger.warn(e) { "No permission to update" }
            cancelUpdate(request)
        } catch (e: ErrorResponseException) {
            handleUpdateError(request, e)
        }
    }

    private fun sendResetMessage(request: TrackRequest) {
        // TODO: Implement reset message logic
        cancelUpdate(request)
    }

    private fun handleUpdateError(
        request: TrackRequest,
        e: ErrorResponseException
    ) {
        when (e.errorResponse) {
            ErrorResponse.UNKNOWN_MESSAGE, ErrorResponse.MISSING_ACCESS -> cancelUpdate(request)
            else -> {
                logger.error(e) { "Update error" }
                return
            }
        }
    }

    private fun runUpdater(request: TrackRequest) {
        syncByGuild(request) {
            val task = scheduler.scheduleWithFixedDelay({
                contextService.withContext(request.guildId) {
                    updateMessage(request)
                }
            }, Duration.ofMillis(workerProperties.audio.panelRefreshInterval.toLong()))

            updaterTasks.put(request.guildId, task)?.cancel(true)
        }
    }

    private fun isRefreshable(guildId: Long): Boolean {
        val config = musicConfigService.getByGuildId(guildId) ?: return false
        return config.autoRefresh /* TODO: add featureSetService */
    }

    private fun syncByGuild(request: TrackRequest, action: () -> Unit) {
        val lock = guildLocks.computeIfAbsent(request.guildId) { ReentrantLock() }
        lock.lock()
        try {
            contextService.withContext(request.guildId, action)
        } finally {
            lock.unlock()

            synchronized(guildLocks) {
                if (!lock.hasQueuedThreads()) {
                    guildLocks.remove(request.guildId, lock)
                }
            }
        }
    }

    private fun getBasicMessage(request: TrackRequest): EmbedBuilder {
        return EmbedBuilder()
    }

    private fun getPlayMessage(request: TrackRequest): EmbedBuilder {
        // Get player instance from audio service
        val player = audioService.player(request.guildId)
        val instance = player?.let { PlaybackInstance(request.guildId, it) }
            ?: return getBasicMessage(request)

        val context = PlayMessageContext(
            request = request,
            instance = instance,
            isRefreshable = isRefreshable(instance.guildId),
            isEnded = request.endReason != null
        )

        return buildEmbed(getBasicMessage(request)) {
            setDescription(null)

            withPlaylistLink(context)
            withQueuePreview(context)
            withTrackInfoFields(context)

            if (!context.isEnded) {
                withPlaybackStatusFields(context)
                withNodeFooter(context)
            }
        }
    }

    private fun EmbedBuilder.withPlaylistLink(context: PlayMessageContext) {
        val playlistUuid = context.instance.playlistUuid ?: return

        setDescription(
            messageService.getMessage(
                "discord.command.audio.panel.playlist",
                commonProperties.branding.websiteUrl,
                playlistUuid
            )
        )
    }

    private fun EmbedBuilder.withQueuePreview(context: PlayMessageContext) {
        if (context.isEnded) return

        val queue = context.instance.queueSnapshot()
        if (queue.size <= 1) return

        val config = musicConfigService.getByGuildId(context.instance.guildId)
        if (config?.showQueue != true) return

        val nextTracks = queue.subList(1, minOf(queue.size, MAX_SHORT_QUEUE + 1))
        val startIndex = context.instance.cursor + 2

        addQueue(context.instance, nextTracks, startIndex, showNextHint = true)
    }

    // endregion

    private fun buildDurationText(context: PlayMessageContext): String {
        return if (context.isEnded) {
            buildEndedDurationText(context)
        } else {
            getTextProgress(context.instance, context.request, context.isRefreshable)
        }
    }

    private fun buildEndedDurationText(context: PlayMessageContext): String = buildString {
        val request = context.request
        val isStream = request.isStream
        val lengthMs = request.lengthMs ?: 0
        val hasDuration = !isStream && lengthMs > 0

        if (hasDuration) {
            append(formatDuration(lengthMs))
            append(" (")
        }

        val endReason = context.request.endReason ?: return@buildString
        val endReasonText = messageService.getMessage(
            "discord.command.audio.endReason.${endReason.name.lowercase()}"
        )
        append(endReasonText)

        val endMember = getMemberName(context.request, MemberType.Ender)
        if (endMember.isNotBlank()) {
            append(" - **")
            append(endMember)
            append("**")
        }

        if (hasDuration) {
            append(")")
        }

        append(EMPTY_SYMBOL)
    }

    // region Track Info Fields

    private fun EmbedBuilder.withTrackInfoFields(context: PlayMessageContext) {
        val durationText = buildDurationText(context)
        val requestedBy = getMemberName(context.request, MemberType.Requester)
        val isCompactLayout = !context.request.isStream && context.isRefreshable

        if (isCompactLayout) {
            withCompactTrackField(requestedBy, durationText)
        } else {
            withExpandedTrackFields(durationText, requestedBy)
        }
    }

    private fun EmbedBuilder.withCompactTrackField(requestedBy: String, durationText: String) {
        val label = buildString {
            append(messageService.getMessage("discord.command.audio.panel.requestedBy"))
            append(": ")
            append(requestedBy)
        }
        addField(label, durationText, true)
    }

    private fun EmbedBuilder.withExpandedTrackFields(durationText: String, requestedBy: String) {
        addField(
            messageService.getMessage("discord.command.audio.panel.duration"),
            durationText,
            true
        )
        addField(
            messageService.getMessage("discord.command.audio.panel.requestedBy"),
            requestedBy,
            true
        )
    }

    private fun EmbedBuilder.withPlaybackStatusFields(context: PlayMessageContext) {
        val player = context.instance.player

        withVolumeField(player)
        withRepeatModeField(context.instance)
        withPausedField(player)
    }

    private fun EmbedBuilder.withVolumeField(player: LavalinkPlayer) {
        val volume = player.volume
        if (volume == DEFAULT_VOLUME) return

        addField(
            messageService.getMessage("discord.command.audio.panel.volume"),
            "$volume% TODO icon",
            true
        )
    }

    private fun EmbedBuilder.withRepeatModeField(instance: PlaybackInstance) {
        val mode = instance.mode
        if (mode == RepeatMode.NONE) return

        addField(
            messageService.getMessage("discord.command.audio.panel.repeatMode"),
            mode.emoji.formatted,
            true
        )
    }

    private fun EmbedBuilder.withPausedField(player: LavalinkPlayer) {
        if (!player.paused) return

        addField(
            messageService.getMessage("discord.command.audio.panel.paused"),
            PAUSE_EMOJI,
            true
        )
    }

    private fun EmbedBuilder.withNodeFooter(context: PlayMessageContext) {
        val node = audioService.lavalink.nodes
            .firstOrNull { it.getPlayer(context.instance.guildId).block() == context.instance.player }
            ?: return

        val footerText = buildNodeFooterText(node, context.isRefreshable)
        setFooter(footerText, null)
    }

    private fun buildNodeFooterText(node: LavalinkNode, isRefreshable: Boolean): String {
        val baseText = messageService.getMessage(
            "discord.command.audio.panel.poweredBy",
            node.name
        )

        if (!isRefreshable) return baseText

        val stats = node.stats ?: return baseText

        val loadPercentage = (stats.cpu.systemLoad * 100)
            .roundToInt()
            .coerceIn(MIN_LOAD_PERCENT, MAX_LOAD_PERCENT)

        val loadText = messageService.getMessage(
            "discord.command.audio.panel.load",
            loadPercentage
        )

        return "$baseText $loadText"
    }

    /**
     * Specifies which member to resolve from a track request.
     */
    private sealed interface MemberType {
        data object Requester : MemberType
        data object Ender : MemberType
    }

    private fun getMemberName(request: TrackRequest, type: MemberType): String {
        val userId = when (type) {
            MemberType.Requester -> request.memberId
            MemberType.Ender -> request.endMemberId ?: return ""
        }

        return resolveMemberName(request.guildId, userId)
    }

    private fun resolveMemberName(guildId: Long, userId: Long): String {
        val shardManager = discordService.shardManager
        val guild = shardManager.getGuildById(guildId)
        val user = shardManager.getUserById(userId)

        return when {
            user != null && guild != null -> {
                guild.getMember(user)?.effectiveName ?: user.name
            }

            user != null -> user.name
            else -> userId.toString()
        }
    }

    // Convenience overloads for cleaner call sites
    private fun getRequesterName(request: TrackRequest): String =
        getMemberName(request, MemberType.Requester)

    private fun getEnderName(request: TrackRequest): String =
        getMemberName(request, MemberType.Ender)

    private fun getTextProgress(
        instance: PlaybackInstance,
        request: TrackRequest,
        showLiveProgress: Boolean
    ): String = buildString {
        val isStream = request.isStream
        val duration = request.lengthMs ?: 0
        val hasValidDuration = !isStream && duration >= 0

        when {
            showLiveProgress && instance.player.track != null -> {
                appendLiveProgress(instance, request, hasValidDuration)
            }

            hasValidDuration -> {
                append(formatDuration(duration))
            }
        }

        if (isStream) {
            appendStreamIndicator(showLiveProgress)
        }
    }

    private fun StringBuilder.appendLiveProgress(
        instance: PlaybackInstance,
        request: TrackRequest,
        hasValidDuration: Boolean
    ) {
        val position = instance.lastKnownPositionMs
        val duration = request.lengthMs ?: 0

        if (hasValidDuration) {
            val progressPercent = calculateProgressPercent(position, duration)
            append(getProgressString(progressPercent))
            append(" ")
        }

        append(TIMESTAMP_START)
        append(formatDuration(position))

        if (hasValidDuration) {
            append(" / ")
            append(formatDuration(duration))
        }

        append(TIMESTAMP_END)
    }

    private fun calculateProgressPercent(position: Long, duration: Long): Int {
        if (duration <= 0) return 0
        return ((position.toDouble() / duration.toDouble()) * 100).toInt()
    }

    private fun StringBuilder.appendStreamIndicator(showLiveProgress: Boolean) {
        val streamText = messageService.getMessage("discord.command.audio.panel.stream")
        if (showLiveProgress) {
            append(" ($streamText)")
        } else {
            append(streamText)
        }
    }

    // endregion

    // region Queue Display

    private fun EmbedBuilder.addQueue(
        instance: PlaybackInstance,
        requests: List<TrackRequest>,
        startIndex: Int,
        showNextHint: Boolean
    ) {
        if (requests.isEmpty()) return

        requests.forEachIndexed { index, request ->
            val queueEntry = buildQueueEntry(
                request = request,
                instance = instance,
                position = startIndex + index,
                isNextTrack = showNextHint && index == 0,
                showPlayingIcon = !showNextHint
            )

            addField(queueEntry.title, queueEntry.description, false)
        }
    }

    private fun buildQueueEntry(
        request: TrackRequest,
        instance: PlaybackInstance,
        position: Int,
        isNextTrack: Boolean,
        showPlayingIcon: Boolean
    ): QueueEntry {

        val title = when {
            isNextTrack -> messageService.getMessage("discord.command.audio.queue.next")
            else -> ZERO_WIDTH_SPACE
        }

        val description = formatQueueEntryDescription(
            request = request,
            position = position,
            isAboutToPlay = showPlayingIcon && isNextInQueue(position, instance),
            requesterName = getRequesterName(request)
        )

        return QueueEntry(title, description)
    }

    private fun formatQueueEntryDescription(
        request: TrackRequest,
        position: Int,
        isAboutToPlay: Boolean,
        requesterName: String
    ): String {
        val duration = formatQueueDuration(request)
        val icon = selectQueueIcon(request, isAboutToPlay)

        return messageService.getMessage(
            "discord.command.audio.queue.list.entry",
            position,
            duration,
            icon,
            requesterName
        )
    }

    private fun formatQueueDuration(request: TrackRequest): String {
        if (request.isStream) return ""
        val lengthMs = request.lengthMs ?: return ""
        return "$TIMESTAMP_START${formatDuration(lengthMs)}$TIMESTAMP_END"
    }

    private fun selectQueueIcon(request: TrackRequest, isAboutToPlay: Boolean): String {
        return when {
            request.isStream -> ICON_STREAM
            isAboutToPlay -> ICON_PLAYING
            else -> ""
        }
    }

    private fun isNextInQueue(position: Int, instance: PlaybackInstance): Boolean {
        return position - instance.cursor == 1
    }

    private data class QueueEntry(
        val title: String,
        val description: String
    )

    // Extension function for cleaner embed building
    private inline fun buildEmbed(
        base: EmbedBuilder,
        block: EmbedBuilder.() -> Unit
    ): EmbedBuilder = base.apply(block)

    private data class PlayMessageContext(
        val request: TrackRequest,
        val instance: PlaybackInstance,
        val isRefreshable: Boolean,
        val isEnded: Boolean
    )

    // Utility methods
    private fun formatDuration(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%d:%02d", minutes, secs)
        }
    }

    private fun getProgressString(percent: Int): String {
        val totalBlocks = 15
        val filledBlocks = (percent * totalBlocks / 100).coerceIn(0, totalBlocks)
        val emptyBlocks = totalBlocks - filledBlocks

        return buildString {
            append("▬".repeat(filledBlocks))
            append("🔘")
            append("▬".repeat(emptyBlocks.coerceAtLeast(0)))
        }
    }
}