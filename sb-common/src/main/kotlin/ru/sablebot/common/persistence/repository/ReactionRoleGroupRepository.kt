package ru.sablebot.common.persistence.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import ru.sablebot.common.persistence.entity.ReactionRoleGroup
import ru.sablebot.common.persistence.repository.base.GuildRepository

@Repository
interface ReactionRoleGroupRepository : GuildRepository<ReactionRoleGroup> {

    fun findByGuildIdAndName(guildId: Long, name: String): ReactionRoleGroup?

    fun findByGuildIdAndActive(guildId: Long, active: Boolean): List<ReactionRoleGroup>

    fun findAllByGuildId(guildId: Long): List<ReactionRoleGroup>

    fun findByGuildId(guildId: Long, pageable: Pageable): Page<ReactionRoleGroup>

    fun existsByGuildIdAndName(guildId: Long, name: String): Boolean

    fun countByGuildIdAndActive(guildId: Long, active: Boolean): Int
}
