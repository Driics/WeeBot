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
import ru.sablebot.common.utils.CommonUtils
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
        val channel = request.channel ?: run {
            cancelUpdate(request)
            return
        }

        try {
            if (request.resetMessage) {
                sendResetMessage(request, channel)
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
        val instance = TrackData.get(request.track).instance
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

        addQueue(this, context.instance, nextTracks, startIndex, compact = true)
    }

    // endregion

    private fun buildDurationText(context: PlayMessageContext): String {
        return if (context.isEnded) {
            buildEndedDurationText(context)
        } else {
            getTextProgress(context.instance, context.request.track, context.isRefreshable)
        }
    }

    private fun buildEndedDurationText(context: PlayMessageContext): String = buildString {
        val info = context.request.track.info
        val hasDuration = !info.isStream && info.length > 0

        if (hasDuration) {
            append(CommonUtils.formatDuration(context.request.track.duration))
            append(" (")
        }

        append(messageService.getEnumTitle(context.request.endReason))

        val endMember = getMemberName(context.request, forEndReason = true)
        if (endMember.isNotBlank()) {
            append(" - **")
            append(endMember)
            append("**")
        }

        if (hasDuration) {
            append(")")
        }

        append(CommonUtils.EMPTY_SYMBOL)
    }

    // region Track Info Fields

    private fun EmbedBuilder.withTrackInfoFields(context: PlayMessageContext) {
        val durationText = buildDurationText(context)
        val requestedBy = getMemberName(context.request, forEndReason = false)
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
}