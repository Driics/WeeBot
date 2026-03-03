package ru.sablebot.common.persistence.repository

import org.springframework.stereotype.Repository
import ru.sablebot.common.persistence.entity.Playlist
import ru.sablebot.common.persistence.repository.base.GuildRepository

@Repository
interface PlaylistRepository : GuildRepository<Playlist> {
    fun findAllByGuildId(guildId: Long): List<Playlist>

    fun findByUuid(uuid: String): Playlist?
}
