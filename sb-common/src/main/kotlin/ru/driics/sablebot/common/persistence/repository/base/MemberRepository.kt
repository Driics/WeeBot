package ru.driics.sablebot.common.persistence.repository.base

import org.springframework.data.repository.NoRepositoryBean
import ru.driics.sablebot.common.persistence.entity.base.MemberEntity

@NoRepositoryBean
interface MemberRepository<T : MemberEntity> : GuildRepository<T> {
    fun findAllByGuildIdAndUserId(guildId: Long, userId: String): List<T>
    fun deleteByGuildIdAndUserId(guildId: Long, userId: String)
}