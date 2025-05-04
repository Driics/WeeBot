package ru.driics.sablebot.common.persistence.repository.base

import org.springframework.data.repository.NoRepositoryBean
import ru.driics.sablebot.common.persistence.entity.base.MemberEntity

@NoRepositoryBean
interface MemberRepository<T : MemberEntity> : GuildRepository<T> {
    fun findByGuildIdAndUserId(guildId: Long, userId: String): T?
    fun deleteByGuildIdAndUserId(guildId: Long, userId: String)
}