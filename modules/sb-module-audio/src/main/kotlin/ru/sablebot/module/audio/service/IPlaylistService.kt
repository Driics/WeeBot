package ru.sablebot.module.audio.service

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import ru.sablebot.common.persistence.entity.Playlist
import ru.sablebot.common.persistence.entity.PlaylistItem

interface IPlaylistService {
    fun create(member: Member, guild: Guild, name: String, items: List<PlaylistItem>): Playlist
    fun delete(playlistId: Long, requesterId: Long): Boolean
    fun getByGuild(guildId: Long): List<Playlist>
    fun get(playlistId: Long): Playlist?
    fun getByUuid(uuid: String): Playlist?
    fun addItems(playlistId: Long, items: List<PlaylistItem>)
    fun removeItems(playlistId: Long, indices: List<Int>)
    suspend fun loadAndPlay(playlistId: Long, guild: Guild, member: Member, channel: TextChannel)
}
