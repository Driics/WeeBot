package ru.sablebot.common.persistence.repository.base

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.NoRepositoryBean
import ru.sablebot.common.persistence.entity.base.GuildEntity

@NoRepositoryBean
interface GuildRepository<T: GuildEntity>: JpaRepository<T, Long> {
    fun findByGuildId(guildId: Long): T?

    fun existsByGuildId(guildId: Long): Boolean
}