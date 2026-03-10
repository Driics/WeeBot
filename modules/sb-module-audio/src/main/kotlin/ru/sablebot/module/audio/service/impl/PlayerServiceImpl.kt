package ru.sablebot.module.audio.service.impl

import dev.arbjerg.lavalink.client.player.*
import dev.arbjerg.lavalink.protocol.v4.Message
import dev.arbjerg.lavalink.protocol.v4.Omissible
import dev.arbjerg.lavalink.protocol.v4.PlayerUpdateTrack
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import jakarta.annotation.PreDestroy
import jakarta.transaction.Transactional
import kotlinx.coroutines.*
import kotlinx.coroutines.reactor.awaitSingle
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import org.springframework.context.annotation.Lazy
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Service
import ru.sablebot.common.model.exception.DiscordException
import ru.sablebot.common.service.MusicConfigService
import ru.sablebot.common.worker.configuration.WorkerProperties
import ru.sablebot.common.worker.event.service.ContextService
import ru.sablebot.common.worker.feature.service.FeatureSetService
import ru.sablebot.common.worker.shared.service.DiscordService
import ru.sablebot.module.audio.model.*
import ru.sablebot.module.audio.service.IAudioSearchProvider
import ru.sablebot.module.audio.service.IFilterService
import ru.sablebot.module.audio.service.ILavalinkV4AudioService
import ru.sablebot.module.audio.service.PlayerServiceV4
import ru.sablebot.module.audio.service.helper.AudioMessageManager
import ru.sablebot.module.audio.service.helper.PlayerListenerAdapter
import ru.sablebot.module.audio.service.helper.ValidationService
import java.util.*

@Service
class PlayerServiceImpl(
    @Lazy private val messageManager: AudioMessageManager,
    @Lazy private val discordService: DiscordService,
    private val musicConfigService: MusicConfigService,
    private val contextService: ContextService,
    private val lavaAudioService: ILavalinkV4AudioService,
    @Lazy private val validationService: ValidationService,
    private val featureSetService: FeatureSetService,
    private val workerProperties: WorkerProperties,
    private val searchProviders: List<IAudioSearchProvider>,
    private val taskExecutor: ThreadPoolTaskExecutor,
    meterRegistry: MeterRegistry,
    @Lazy private val filterService: IFilterService,
) : PlayerServiceV4,
    PlayerListenerAdapter(
        lavaAudioService,
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
        meterRegistry
    ) {

    private val log = KotlinLogging.logger {}
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        lavaAudioService.addOnConfiguredCallback { initializeListeners() }
    }

    companion object {
        private const val INACTIVE_TIMEOUT_MS = 5 * 60 * 1000L
    }

    // ==================== Instance Access ====================

    override fun instances(): Map<Long, PlaybackInstance> =
        Collections.unmodifiableMap(instancesByGuild)

    @Transactional
    override fun get(guildId: Long, create: Boolean): PlaybackInstance? {
        if (!create) return instancesByGuild[guildId]

        return instancesByGuild.computeIfAbsent(guildId) { id ->
            musicConfigService.getOrCreate(guildId)
            val player = lavaAudioService.player(id)
            registerInstance(PlaybackInstance(id, player))
        }
    }

    override fun get(guild: Guild): PlaybackInstance? = instancesByGuild[guild.idLong]

    override fun getOrCreate(guild: Guild): PlaybackInstance =
        get(guild.idLong, create = true)!!

    // ==================== Connection ====================

    @Throws(DiscordException::class)
    override suspend fun connectToChannel(instance: PlaybackInstance, member: Member): VoiceChannel {
        val voiceChannel = member.voiceState?.channel?.asVoiceChannel()
            ?: throw DiscordException("discord.command.audio.error.notInChannel")

        lavaAudioService.connectAndWait(voiceChannel)
        return voiceChannel
    }

    override fun isInChannel(member: Member): Boolean {
        val botChannel = connectedChannel(member.guild) ?: return false
        val memberChannel = member.voiceState?.channel ?: return false
        return botChannel.idLong == memberChannel.idLong
    }

    override fun hasAccess(member: Member): Boolean {
        val botChannel = connectedChannel(member.guild) ?: return true
        val memberChannel = member.voiceState?.channel ?: return false
        return botChannel.idLong == memberChannel.idLong
    }

    override fun memberChannel(member: Member): VoiceChannel? =
        member.voiceState?.channel?.asVoiceChannel()

    override fun connectedChannel(guild: Guild): VoiceChannel? =
        guild.audioManager.connectedChannel?.asVoiceChannel()

    override fun connectedChannel(guildId: Long): VoiceChannel? {
        val guild = discordService.shardManager.getGuildById(guildId) ?: return null
        return connectedChannel(guild)
    }

    override fun desiredChannel(member: Member): VoiceChannel? =
        connectedChannel(member.guild) ?: memberChannel(member)

    // ==================== Track Loading ====================

    override suspend fun loadAndPlay(channel: TextChannel, requestedBy: Member, identifier: String) {
        val resolvedIdentifier = resolveIdentifier(identifier)
        val guildId = channel.guild.idLong
        val link = lavaAudioService.lavalink.getOrCreateLink(guildId)

        val result = link.loadItem(resolvedIdentifier).awaitSingle()
            ?: throw DiscordException("discord.command.audio.error.loadFailed")

        val jda = channel.jda
        val channelId = channel.idLong
        val memberId = requestedBy.idLong

        when (result) {
            is TrackLoaded -> {
                val request = toTrackRequest(result.track, jda, guildId, channelId, memberId)
                play(request)
            }

            is PlaylistLoaded -> {
                val requests = result.tracks.map { track ->
                    toTrackRequest(track, jda, guildId, channelId, memberId)
                }
                if (requests.isNotEmpty()) {
                    playAll(requests)
                } else {
                    throw DiscordException("discord.command.audio.error.noMatches")
                }
            }

            is SearchResult -> {
                val track = result.tracks.firstOrNull()
                    ?: throw DiscordException("discord.command.audio.error.noMatches")
                val request = toTrackRequest(track, jda, guildId, channelId, memberId)
                play(request)
            }

            is NoMatches -> throw DiscordException("discord.command.audio.error.noMatches")
            is LoadFailed -> throw DiscordException("discord.command.audio.error.loadFailed")
        }
    }

    private fun toTrackRequest(
        track: Track,
        jda: net.dv8tion.jda.api.JDA,
        guildId: Long,
        channelId: Long,
        memberId: Long
    ): TrackRequest {
        val info = track.info
        return TrackRequest(
            encodedTrack = track.encoded,
            jda = jda,
            guildId = guildId,
            channelId = channelId,
            memberId = memberId,
            identifier = info.identifier,
            title = info.title,
            author = info.author,
            uri = info.uri,
            sourceName = info.sourceName,
            lengthMs = info.length,
            isStream = info.isStream,
            isSeekable = info.isSeekable,
            artworkUrl = info.artworkUrl,
            isrc = info.isrc
        )
    }

    private fun resolveIdentifier(identifier: String): String {
        if (identifier.startsWith("http://") || identifier.startsWith("https://")) {
            return identifier
        }
        val source = SearchSource.fromName(workerProperties.audio.searchProvider) ?: SearchSource.YOUTUBE
        return "${source.prefix}$identifier"
    }

    // ==================== Playback Control ====================

    @Throws(DiscordException::class)
    override suspend fun play(request: TrackRequest) {
        val guild = discordService.shardManager.getGuildById(request.guildId) ?: return
        val instance = getOrCreate(guild)

        contextService.withContext(instance.guildId) {
            messageManager.onTrackAdd(request, instance)
        }

        val member = request.member()
        if (member != null) {
            connectToChannel(instance, member)
        }

        val trackToStart = instance.enqueue(request)
        if (trackToStart != null) {
            startTrack(instance, trackToStart)
        }
    }

    override suspend fun playAll(requests: List<TrackRequest>): Int {
        if (requests.isEmpty()) return 0

        val first = requests.first()
        val member = first.member() ?: return 0
        val guild = discordService.shardManager.getGuildById(first.guildId) ?: return 0
        val instance = getOrCreate(guild)

        val filtered = validationService.filterPlaylist(requests, member, playlistRequested = true)
        if (filtered.isEmpty()) return 0

        connectToChannel(instance, member)

        var started = false
        for (request in filtered) {
            contextService.withContext(instance.guildId) {
                messageManager.onTrackAdd(request, instance)
            }
            val trackToStart = instance.enqueue(request)
            if (trackToStart != null && !started) {
                startTrack(instance, trackToStart)
                started = true
            }
        }
        return filtered.size
    }

    private suspend fun startTrack(instance: PlaybackInstance, request: TrackRequest) {
        val link = lavaAudioService.lavalink.getOrCreateLink(instance.guildId)
        link.createOrUpdatePlayer()
            .updateTrack(PlayerUpdateTrack(encoded = Omissible.of(request.encodedTrack)))
            .setVolume(instance.volume)
            .awaitSingle()
    }

    private suspend fun playNext(instance: PlaybackInstance): Boolean {
        val next = instance.nextToPlay() ?: return false
        startTrack(instance, next)
        return true
    }

    override fun skipTrack(member: Member, guild: Guild) {
        val instance = get(guild) ?: return
        val current = instance.currentOrNull()
        if (current != null) {
            current.endReason = EndReason.SKIPPED
            current.endMemberId = member.idLong
        }

        scope.launch {
            if (!playNext(instance)) {
                clearInstance(instance, notify = true)
            }
        }
    }

    override fun skipTo(guild: Guild, index: Int): TrackRequest? {
        val instance = get(guild) ?: return null
        val track = instance.skipTo(index) ?: return null
        scope.launch {
            startTrack(instance, track)
        }
        return track
    }

    override fun removeByIndex(guild: Guild, index: Int): TrackRequest? =
        get(guild)?.removeByIndex(index)

    override fun shuffle(guild: Guild): Boolean =
        get(guild)?.shuffleUpcoming() ?: false

    override fun stop(member: Member, guild: Guild): Boolean {
        val instance = get(guild) ?: return false
        val current = instance.currentOrNull()
        if (current != null) {
            current.endReason = EndReason.STOPPED
            current.endMemberId = member.idLong
        }
        clearInstance(instance, notify = true)
        return true
    }

    override suspend fun pause(guild: Guild): Boolean {
        get(guild) ?: return false
        val link = lavaAudioService.lavalink.getOrCreateLink(guild.idLong)
        link.createOrUpdatePlayer()
            .setPaused(true)
            .awaitSingle()
        return true
    }

    override suspend fun resume(guild: Guild, resetTrack: Boolean): Boolean {
        val instance = get(guild) ?: return false
        val link = lavaAudioService.lavalink.getOrCreateLink(guild.idLong)

        if (resetTrack) {
            val current = instance.currentOrNull()
            if (current != null) {
                current.resetOnResume = true
                link.createOrUpdatePlayer()
                    .updateTrack(PlayerUpdateTrack(encoded = Omissible.of(current.encodedTrack)))
                    .setPaused(false)
                    .awaitSingle()
                return true
            }
        }

        link.createOrUpdatePlayer()
            .setPaused(false)
            .awaitSingle()
        return true
    }

    override suspend fun seek(guild: Guild, positionMs: Long): Boolean {
        val instance = get(guild) ?: return false
        val current = instance.currentOrNull() ?: return false
        if (!current.isSeekable) return false

        val link = lavaAudioService.lavalink.getOrCreateLink(guild.idLong)
        link.createOrUpdatePlayer()
            .setPosition(positionMs)
            .awaitSingle()
        return true
    }

    override suspend fun setVolume(guild: Guild, volume: Int): Boolean {
        val instance = get(guild) ?: return false
        val clamped = volume.coerceIn(0, 1000)

        val link = lavaAudioService.lavalink.getOrCreateLink(guild.idLong)
        link.createOrUpdatePlayer()
            .setVolume(clamped)
            .awaitSingle()
        instance.volume = clamped
        return true
    }

    override suspend fun applyFilter(guild: Guild, preset: FilterPreset): Boolean {
        get(guild) ?: return false
        filterService.apply(guild.idLong, preset)
        return true
    }

    override fun moveTo(guild: Guild, from: Int, to: Int): Boolean =
        get(guild)?.moveTo(from, to) ?: false

    override fun clearQueue(guild: Guild): Int =
        get(guild)?.clear() ?: 0

    override fun set247(guild: Guild, enabled: Boolean) {
        val instance = getOrCreate(guild)
        instance.twentyFourSeven = enabled
    }

    // ==================== Status ====================

    override fun isActive(guild: Guild): Boolean {
        return isActive(get(guild) ?: return false)
    }

    override fun isActive(instance: PlaybackInstance): Boolean =
        instance.currentOrNull() != null

    override fun activeCount(): Long =
        instancesByGuild.values.count { isActive(it) }.toLong()

    // ==================== Monitoring ====================

    override fun monitor() {
        val now = System.currentTimeMillis()
        val toDisconnect = mutableListOf<PlaybackInstance>()

        instancesByGuild.values.forEach { instance ->
            if (!instance.twentyFourSeven && !isActive(instance)) {
                if (now - instance.activeTime > INACTIVE_TIMEOUT_MS) {
                    toDisconnect.add(instance)
                }
            }
        }

        toDisconnect.forEach { instance ->
            log.debug { "Auto-disconnecting inactive instance for guild ${instance.guildId}" }
            clearInstance(instance, notify = false)
        }

        messageManager.monitor(instancesByGuild.keys)
    }

    // ==================== Track Event Handlers ====================

    override suspend fun onTrackStart(instance: PlaybackInstance) {
        instance.tick()
        val request = instance.currentOrNull()

        if (request != null && request.timeCode > 0 && request.isSeekable) {
            if (request.lengthMs > request.timeCode) {
                val link = lavaAudioService.lavalink.getOrCreateLink(instance.guildId)
                link.createOrUpdatePlayer()
                    .setPosition(request.timeCode)
                    .awaitSingle()
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

        if (endReason.mayStartNext) {
            if (playNext(instance)) return
            instance.currentOrNull()?.let { current ->
                contextService.withContext(current.guildId) {
                    messageManager.onQueueEnd(current)
                }
            }
        }

        if (endReason != Message.EmittedEvent.TrackEndEvent.AudioTrackEndReason.REPLACED) {
            scope.launch(Dispatchers.IO) {
                clearInstance(instance, notify = false)
            }
        }
    }

    override suspend fun onTrackException(instance: PlaybackInstance, exception: TrackException) {
        log.error {
            "Track exception in guild ${instance.guildId}: ${exception.message} (severity: ${exception.severity})"
        }

        if (!playNext(instance)) {
            scope.launch(Dispatchers.IO) {
                clearInstance(instance, notify = true)
            }
        }
    }

    override suspend fun onTrackStuck(instance: PlaybackInstance, thresholdMs: Long) {
        log.warn { "Track stuck in guild ${instance.guildId} (threshold: ${thresholdMs}ms)" }

        if (!playNext(instance)) {
            scope.launch(Dispatchers.IO) {
                clearInstance(instance, notify = true)
            }
        }
    }

    // ==================== Internal ====================

    private fun clearInstance(instance: PlaybackInstance, notify: Boolean) {
        if (!instance.stop()) return

        if (notify) {
            notifyCurrentEnd(instance, Message.EmittedEvent.TrackEndEvent.AudioTrackEndReason.STOPPED)
        }

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

    // ==================== Lifecycle ====================

    @PreDestroy
    fun destroy() {
        scope.cancel("PlayerServiceImpl shutting down")

        instancesByGuild.values.toList().forEach { instance ->
            runCatching {
                instance.currentOrNull()?.endReason = EndReason.SHUTDOWN
                clearInstance(instance, notify = true)
            }.onFailure { e ->
                log.warn(e) { "Error cleaning up instance for guild ${instance.guildId}" }
            }
        }
    }
}
