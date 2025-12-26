package ru.sablebot.module.audio.service.helper

import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.emoji.Emoji
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
import ru.sablebot.common.worker.shared.service.DiscordService
import ru.sablebot.module.audio.model.PlaybackInstance
import ru.sablebot.module.audio.model.RepeatMode
import ru.sablebot.module.audio.model.TrackRequest
import ru.sablebot.module.audio.utils.MessageController
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.locks.ReentrantLock

class AudioMessageManager(
    @param:Qualifier(CommonConfiguration.SCHEDULER)
    private val scheduler: TaskScheduler,
    private val contextService: ContextService,
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
            description = null

            withPlaylistLink(context)
            withQueuePreview(context)
            withTrackInfoFields(context)

            if (!context.isEnded) {
                withPlaybackStatusFields(context)
                withNodeFooter(context)
            }
        }
    }

    // Extension function for cleaner embed building
    private inline fun buildEmbed(
        base: EmbedBuilder,
        block: EmbedBuilder.() -> Unit
    ): EmbedBuilder = base.apply(block)

    // Using sealed interface for field types
    private sealed interface EmbedField {
        val name: String
        val value: Emoji
        val inline: Boolean

        data class Volume(val volume: Int) : EmbedField {
            override val name = "Volume"
            override val value = Emoji.fromFormatted("$volume")
            override val inline = true
        }

        data class RepeatMode(val mode: ru.sablebot.module.audio.model.RepeatMode) : EmbedField {
            override val name = "Repeat"
            override val value = mode.emoji
            override val inline = true
        }

        data object Paused : EmbedField {
            override val name = "Status"
            override val value = Emoji.fromUnicode(PAUSE_EMOJI)
            override val inline = true
        }
    }

    private fun collectStatusFields(context: PlayMessageContext): List<EmbedField> = buildList {
        val player = context.instance.player

        if (player.volume != DEFAULT_VOLUME) {
            add(EmbedField.Volume(player.volume))
        }

        if (context.instance.mode != RepeatMode.NONE) {
            add(EmbedField.RepeatMode(context.instance.mode))
        }

        if (player.paused) {
            add(EmbedField.Paused)
        }
    }

    private fun EmbedBuilder.withPlaybackStatusFields(context: PlayMessageContext) {
        collectStatusFields(context).forEach { field ->
            addField(
                messageService.getMessage("discord.command.audio.panel.${field.name.lowercase()}"),
                field.value,
                field.inline
            )
        }
    }

    private data class PlayMessageContext(
        val request: TrackRequest,
        val instance: PlaybackInstance,
        val isRefreshable: Boolean,
        val isEnded: Boolean
    )
}