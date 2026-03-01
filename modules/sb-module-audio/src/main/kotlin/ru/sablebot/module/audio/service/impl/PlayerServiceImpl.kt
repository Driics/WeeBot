package ru.sablebot.module.audio.service.impl

import dev.arbjerg.lavalink.protocol.v4.Message
import io.micrometer.core.instrument.MeterRegistry
import jakarta.transaction.Transactional
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Service
import ru.sablebot.common.model.exception.DiscordException
import ru.sablebot.common.service.MusicConfigService
import ru.sablebot.common.worker.configuration.WorkerProperties
import ru.sablebot.common.worker.event.service.ContextService
import ru.sablebot.common.worker.feature.service.FeatureSetService
import ru.sablebot.common.worker.shared.service.DiscordService
import ru.sablebot.module.audio.model.EndReason
import ru.sablebot.module.audio.model.PlaybackInstance
import ru.sablebot.module.audio.model.TrackRequest
import ru.sablebot.module.audio.service.IAudioSearchProvider
import ru.sablebot.module.audio.service.ILavalinkV4AudioService
import ru.sablebot.module.audio.service.PlayerServiceV4
import ru.sablebot.module.audio.service.helper.AudioMessageManager
import ru.sablebot.module.audio.service.helper.PlayerListenerAdapter
import ru.sablebot.module.audio.service.helper.ValidationService
import java.util.*

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
    private val meterRegistry: MeterRegistry,
) : PlayerServiceV4,
    PlayerListenerAdapter(
        lavaAudioService.lavalink,
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
        meterRegistry
    ) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun instances(): Map<Long, PlaybackInstance> =
        Collections.unmodifiableMap(instancesByGuild)

    @Transactional
    override fun get(guildId: Long, create: Boolean): PlaybackInstance? {
        if (!create)
            return instancesByGuild[guildId]

        return instancesByGuild.computeIfAbsent(guildId) { e ->
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
            scope.launch(Dispatchers.IO) {
                clearInstance(instance, false)
            }
        }
    }

    private fun clearInstance(instance: PlaybackInstance, notify: Boolean) {
        if (!instance.stop())
            return

        if (notify)
            notifyCurrentEnd(instance, Message.EmittedEvent.TrackEndEvent.AudioTrackEndReason.STOPPED)

        discordService.shardManager.getGuildById(instance.guildId)?.let {
            lavaAudioService.disconnect(it)
        }

        messageManager.clear(instance.guildId)
        instancesByGuild.remove(instance.guildId)
        unregisterInstance(instance)
    }

    private fun notifyCurrentEnd(
        instance: PlaybackInstance,
        endReason: Message.EmittedEvent.TrackEndEvent.AudioTrackEndReason
    ) {
        instance.currentOrNull()?.let { current ->
            if (current.endReason == null) {
                current.endReason = EndReason.getForLavaPlayer(endReason)
            }

            contextService.withContext(current.guildId) {
                messageManager.onTrackEnd(current)
            }
        }
    }

    @Throws(DiscordException::class)
    override suspend fun play(request: TrackRequest) {
        val guild = discordService.shardManager.getGuildById(request.guildId)
            ?: return

        val instance = getOrCreate(guild)
        play(request, instance)
    }

    @Throws(DiscordException::class)
    private fun play(request: TrackRequest, instance: PlaybackInstance) {
        contextService.withContext(instance.guildId) {
            messageManager.onTrackAdd(request, instance)
        }

        val member = request.member()
        if (member != null) {
            connectToChannel(instance, member)
            instance.play(request)
        }
    }

    override fun connectToChannel(instance: PlaybackInstance, member: Member): VoiceChannel {
        TODO()
    }
}