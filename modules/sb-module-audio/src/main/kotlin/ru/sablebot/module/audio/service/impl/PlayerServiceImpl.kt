package ru.sablebot.module.audio.service.impl

import dev.arbjerg.lavalink.protocol.v4.Message
import jakarta.transaction.Transactional
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Service
import ru.sablebot.common.service.MusicConfigService
import ru.sablebot.common.worker.configuration.WorkerProperties
import ru.sablebot.common.worker.event.service.ContextService
import ru.sablebot.common.worker.feature.service.FeatureSetService
import ru.sablebot.common.worker.shared.service.DiscordService
import ru.sablebot.module.audio.model.EndReason
import ru.sablebot.module.audio.model.PlaybackInstance
import ru.sablebot.module.audio.service.IAudioSearchProvider
import ru.sablebot.module.audio.service.ILavalinkV4AudioService
import ru.sablebot.module.audio.service.PlayerServiceV4
import ru.sablebot.module.audio.service.helper.AudioMessageManager
import ru.sablebot.module.audio.service.helper.PlayerListenerAdapter
import ru.sablebot.module.audio.service.helper.ValidationService
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Service
class PlayerServiceImpl(
    private val messageManager: AudioMessageManager,
    private val discordService: DiscordService,
    private val musicConfigService: MusicConfigService,
    private val contextService: ContextService,
    private val lavaAudioService: ILavalinkV4AudioService,
    private val validationService: ValidationService,
    private val featureSetService: FeatureSetService,
    private val workerProperties: WorkerProperties,
    private val searchProviders: List<IAudioSearchProvider>,
    private val taskExecutor: ThreadPoolTaskExecutor,
) : PlayerServiceV4,
    PlayerListenerAdapter(lavaAudioService.lavalink, CoroutineScope(SupervisorJob() + Dispatchers.Default)) {
    private val instances = ConcurrentHashMap<Long, PlaybackInstance>()

    override fun instances(): Map<Long, PlaybackInstance> =
        Collections.unmodifiableMap(instances)

    @Transactional
    override fun get(guildId: Long, create: Boolean): PlaybackInstance? {
        if (!create)
            return instances[guildId]

        return instances.computeIfAbsent(guildId) { e ->
            val config = musicConfigService.getOrCreate(guildId)
            val player = lavaAudioService.player(guildId)

            registerInstance(PlaybackInstance(e, player))
        }
    }

    override suspend fun onTrackStart(instance: PlaybackInstance) {
        val request = instance.currentOrNull()

        if (request != null && request.timeCode > 0 && request.isSeekable) {
            val seekTo = request.timeCode
            if (request.lengthMs > seekTo) {
                instance.seek(seekTo)
            }
        }

        contextService.withContext(instance.guildId) {
            messageManager.onTrackStart(instance.currentOrNull())
        }
    }

    override suspend fun onTrackEnd(
        instance: PlaybackInstance,
        endReason: Message.EmittedEvent.TrackEndEvent.AudioTrackEndReason
    ) {
        notifyCurrentEnd(instance, endReason)
        if (endReason.mayStartNext /* TODO: add featureSet */) {
            if (instance.playNext()) {
                return
            }
            instance.currentOrNull()?.let { current ->
                contextService.withContext(current.guildId) {
                    messageManager.onQueueEnd(current)
                }
            }
        }

        if (endReason != Message.EmittedEvent.TrackEndEvent.AudioTrackEndReason.REPLACED) {
            taskExecutor.execute {
                clearInstance(instance, false)
            }
        }
    }

    private fun notifyCurrentEnd(instance: PlaybackInstance, endReason: Message.EmittedEvent.TrackEndEvent) {
        instance.currentOrNull()?.let { current ->
            if (current.endReason == null) {
                current.endReason = EndReason.getForLavaPlayer(endReason)
            }

            contextService.withContext(current.guildId) {
                messageManager.onTrackEnd(current)
            }
        }
    }
}