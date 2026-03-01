package ru.sablebot.module.audio.service

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import ru.sablebot.common.model.exception.DiscordException
import ru.sablebot.module.audio.model.PlaybackInstance
import ru.sablebot.module.audio.model.TrackRequest

interface PlayerServiceV4 {

    companion object {
        const val ACTIVE_CONNECTIONS: String = "player.activeConnections"
    }

    fun instances(): Map<Long, PlaybackInstance>

    fun getOrCreate(guild: Guild): PlaybackInstance
    fun get(guild: Guild): PlaybackInstance?
    fun get(guildId: Long, create: Boolean = false): PlaybackInstance?

    /** Загружает трек/плейлист через Lavalink /v4/loadtracks и ставит в очередь/сразу играет. */
    suspend fun loadAndPlay(channel: TextChannel, requestedBy: Member, identifier: String)

    /** Проиграть один запрос (обычно уже содержит encoded или identifier). */
    suspend fun play(request: TrackRequest)

    /** Проиграть пачку запросов. */
    suspend fun playAll(requests: List<TrackRequest>): Int

    fun skipTrack(member: Member, guild: Guild)

    fun removeByIndex(guild: Guild, index: Int): TrackRequest?

    fun shuffle(guild: Guild): Boolean

    fun isInChannel(member: Member): Boolean
    fun hasAccess(member: Member): Boolean

    fun memberChannel(member: Member): VoiceChannel?

    /** Подключает бота к каналу через LavalinkV4AudioService.connect(). */
    @Throws(DiscordException::class)
    fun connectToChannel(instance: PlaybackInstance, member: Member): VoiceChannel

    fun connectedChannel(guild: Guild): VoiceChannel?
    fun connectedChannel(guildId: Long): VoiceChannel?

    fun desiredChannel(member: Member): VoiceChannel?

    fun monitor()
    fun activeCount(): Long

    fun stop(member: Member, guild: Guild): Boolean
    suspend fun pause(guild: Guild): Boolean
    suspend fun resume(guild: Guild, resetTrack: Boolean = false): Boolean

    fun isActive(guild: Guild): Boolean
    fun isActive(instance: PlaybackInstance): Boolean
}