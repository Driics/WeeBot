package ru.driics.sablebot.common.persistence.repository

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.driics.sablebot.common.persistence.entity.GuildConfig
import ru.driics.sablebot.common.persistence.repository.base.GuildRepository

@Repository
interface GuildConfigRepository : GuildRepository<GuildConfig> {

    @Query("SELECT e.prefix FROM GuildConfig e WHERE e.guildId = :guildId")
    fun findPrefixByGuildId(@Param("guildId") guildId: Long): String?

    @Query("SELECT e.locale FROM GuildConfig e WHERE e.guildId = :guildId")
    fun findLocaleByGuildId(@Param("guildId") guildId: Long): String?

    @Query("SELECT e.color FROM GuildConfig e WHERE e.guildId = :guildId")
    fun findColorByGuildId(@Param("guildId") guildId: Long): String?
}