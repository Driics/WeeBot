package ru.sablebot.common.persistence.repository

import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.sablebot.common.persistence.entity.MusicConfig
import ru.sablebot.common.persistence.repository.base.GuildRepository

@Repository
interface MusicConfigRepository : GuildRepository<MusicConfig> {
    @Modifying
    @Transactional
    @Query("UPDATE MusicConfig m SET m.voiceVolume = :voiceVolume WHERE m.guildId = :guildId")
    fun updateVolume(
        @Param("guild_id") guildId: Long,
        @Param("voiceVolume") voiceVolume: Int
    )
}