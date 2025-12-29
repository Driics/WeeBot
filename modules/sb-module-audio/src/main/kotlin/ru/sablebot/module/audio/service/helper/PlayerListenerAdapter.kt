package ru.sablebot.module.audio.service.helper

import dev.arbjerg.lavalink.client.LavalinkClient
import dev.arbjerg.lavalink.client.event.TrackEndEvent
import dev.arbjerg.lavalink.client.event.TrackExceptionEvent
import dev.arbjerg.lavalink.client.event.TrackStartEvent
import dev.arbjerg.lavalink.client.event.TrackStuckEvent
import dev.arbjerg.lavalink.client.player.TrackException
import dev.arbjerg.lavalink.protocol.v4.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.reactive.asFlow
import ru.sablebot.module.audio.model.PlaybackInstance
import java.util.concurrent.ConcurrentHashMap

abstract class PlayerListenerAdapter(
    protected val lavalink: LavalinkClient,
    private val scope: CoroutineScope
) {
    private val instancesByGuild = ConcurrentHashMap<Long, PlaybackInstance>()

    init {
        setupEventListeners()
    }

    // ==================== Abstract Methods ====================

    protected abstract suspend fun onTrackEnd(
        instance: PlaybackInstance,
        endReason: Message.EmittedEvent.TrackEndEvent.AudioTrackEndReason
    )

    protected abstract suspend fun onTrackStart(instance: PlaybackInstance)

    protected abstract suspend fun onTrackException(
        instance: PlaybackInstance,
        exception: TrackException
    )

    protected abstract suspend fun onTrackStuck(
        instance: PlaybackInstance,
        thresholdMs: Long
    )

    // ==================== Event Setup ====================

    private fun setupEventListeners() {
        lavalink.on<TrackStartEvent>()
            .asFlow()
            .onEach { event ->
                instancesByGuild[event.guildId]?.let { instance ->
                    onTrackStart(instance)
                }
            }
            .launchIn(scope)

        lavalink.on<TrackEndEvent>()
            .asFlow()
            .onEach { event ->
                instancesByGuild[event.guildId]?.let { instance ->
                    onTrackEnd(instance, event.endReason)
                }
            }
            .launchIn(scope)

        lavalink.on<TrackExceptionEvent>()
            .asFlow()
            .onEach { event ->
                instancesByGuild[event.guildId]?.let { instance ->
                    onTrackException(instance, event.exception)
                }
            }
            .launchIn(scope)

        lavalink.on<TrackStuckEvent>()
            .asFlow()
            .onEach { event ->
                instancesByGuild[event.guildId]?.let { instance ->
                    onTrackStuck(instance, event.thresholdMs)
                }
            }
            .launchIn(scope)
    }

    // ==================== Instance Management ====================

    protected fun registerInstance(instance: PlaybackInstance): PlaybackInstance {
        instancesByGuild[instance.guildId] = instance
        return instance
    }

    protected fun unregisterInstance(instance: PlaybackInstance) {
        instancesByGuild.remove(instance.guildId, instance)
    }

    protected fun getInstance(guildId: Long): PlaybackInstance? {
        return instancesByGuild[guildId]
    }
}