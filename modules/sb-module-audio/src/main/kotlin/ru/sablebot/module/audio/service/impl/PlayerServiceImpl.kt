package ru.sablebot.module.audio.service.impl

import jakarta.transaction.Transactional
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.springframework.stereotype.Service
import ru.sablebot.common.service.MusicConfigService
import ru.sablebot.common.worker.configuration.WorkerProperties
import ru.sablebot.common.worker.event.service.ContextService
import ru.sablebot.common.worker.feature.service.FeatureSetService
import ru.sablebot.common.worker.shared.service.DiscordService
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
) : PlayerServiceV4,
    PlayerListenerAdapter(lavaAudioService.lavalink, CoroutineScope(SupervisorJob() + Dispatchers.Default)) {
    private val instances = ConcurrentHashMap<Long, PlaybackInstance>()

    override fun instances(): Map<Long, PlaybackInstance> =
        Collections.unmodifiableMap(instances)

    @Transactional
    override fun get(guildId: Long, create: Boolean): PlaybackInstance? {
        if (!create)
            instances.get(guildId)

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
}