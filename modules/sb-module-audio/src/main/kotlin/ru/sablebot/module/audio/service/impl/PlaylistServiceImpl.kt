package ru.sablebot.module.audio.service.impl

import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.sablebot.common.persistence.entity.Playlist
import ru.sablebot.common.persistence.entity.PlaylistItem
import ru.sablebot.common.persistence.repository.PlaylistRepository
import ru.sablebot.module.audio.service.IPlaylistService
import ru.sablebot.module.audio.service.PlayerServiceV4
import java.util.*

@Service
class PlaylistServiceImpl(
    private val playlistRepository: PlaylistRepository,
    private val playerService: PlayerServiceV4
) : IPlaylistService {

    private val log = KotlinLogging.logger {}

    @Transactional
    override fun create(member: Member, guild: Guild, name: String, items: List<PlaylistItem>): Playlist {
        val playlist = Playlist(
            items = items.toMutableList(),
            date = Date(),
            uuid = name
        )
        playlist.guildId = guild.idLong

        items.forEach { item ->
            item.playlist = playlist
        }

        return playlistRepository.save(playlist)
    }

    @Transactional
    override fun delete(playlistId: Long, requesterId: Long): Boolean {
        val playlist = playlistRepository.findById(playlistId).orElse(null) ?: return false
        playlistRepository.delete(playlist)
        return true
    }

    @Transactional(readOnly = true)
    override fun getByGuild(guildId: Long): List<Playlist> {
        return playlistRepository.findAllByGuildId(guildId)
    }

    @Transactional(readOnly = true)
    override fun get(playlistId: Long): Playlist? {
        return playlistRepository.findById(playlistId).orElse(null)
    }

    @Transactional(readOnly = true)
    override fun getByUuid(uuid: String): Playlist? {
        return playlistRepository.findByUuid(uuid)
    }

    @Transactional
    override fun addItems(playlistId: Long, items: List<PlaylistItem>) {
        val playlist = playlistRepository.findById(playlistId).orElse(null) ?: return
        items.forEach { item ->
            item.playlist = playlist
            playlist.items.add(item)
        }
        playlistRepository.save(playlist)
    }

    @Transactional
    override fun removeItems(playlistId: Long, indices: List<Int>) {
        val playlist = playlistRepository.findById(playlistId).orElse(null) ?: return
        val sortedIndices = indices.sortedDescending()
        sortedIndices.forEach { index ->
            if (index in playlist.items.indices) {
                playlist.items.removeAt(index)
            }
        }
        playlistRepository.save(playlist)
    }

    override suspend fun loadAndPlay(playlistId: Long, guild: Guild, member: Member, channel: TextChannel) {
        val playlist = get(playlistId)
        if (playlist == null) {
            log.warn { "Playlist $playlistId not found for loadAndPlay" }
            return
        }

        playlist.items.forEach { item ->
            val request = ru.sablebot.module.audio.model.TrackRequest(
                encodedTrack = item.data?.let { String(it) } ?: "",
                jda = guild.jda,
                guildId = guild.idLong,
                channelId = channel.idLong,
                memberId = member.idLong,
                identifier = item.identifier,
                title = item.title,
                author = item.author,
                uri = item.uri,
                lengthMs = item.length,
                isStream = item.stream,
                artworkUrl = item.artworkUri
            )
            playerService.play(request)
        }
    }
}
