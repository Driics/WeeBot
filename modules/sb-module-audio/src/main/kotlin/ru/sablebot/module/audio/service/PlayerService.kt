package ru.sablebot.module.audio.service

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import ru.sablebot.common.model.exception.DiscordException
import ru.sablebot.module.audio.model.FilterPreset
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

    suspend fun loadAndPlay(channel: TextChannel, requestedBy: Member, identifier: String)

    suspend fun play(request: TrackRequest)

    suspend fun playAll(requests: List<TrackRequest>): Int

    fun skipTrack(member: Member, guild: Guild)

    fun removeByIndex(guild: Guild, index: Int): TrackRequest?

    fun shuffle(guild: Guild): Boolean

    fun isInChannel(member: Member): Boolean
    fun hasAccess(member: Member): Boolean

    fun memberChannel(member: Member): VoiceChannel?

    @Throws(DiscordException::class)
    suspend fun connectToChannel(instance: PlaybackInstance, member: Member): VoiceChannel

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

    // New methods for full music module
    suspend fun seek(guild: Guild, positionMs: Long): Boolean
    suspend fun setVolume(guild: Guild, volume: Int): Boolean
    suspend fun applyFilter(guild: Guild, preset: FilterPreset): Boolean
    fun moveTo(guild: Guild, from: Int, to: Int): Boolean
    fun skipTo(guild: Guild, index: Int): TrackRequest?
    fun clearQueue(guild: Guild): Int
    fun set247(guild: Guild, enabled: Boolean)
}
