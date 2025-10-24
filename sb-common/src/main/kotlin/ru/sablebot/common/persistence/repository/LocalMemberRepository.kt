package ru.sablebot.common.persistence.repository

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.sablebot.common.persistence.entity.LocalMember
import ru.sablebot.common.persistence.repository.base.GuildRepository


@Repository
interface LocalMemberRepository : GuildRepository<LocalMember> {
    @Query("SELECT m FROM LocalMember m WHERE m.guildId = :guildId AND m.user.userId = :userId")
    fun findByGuildIdAndUserId(@Param("guildId") guildId: Long, @Param("userId") userId: Long): LocalMember?

    @Query("SELECT m FROM LocalMember m WHERE m.guildId = :guildId AND (m.user.userId = :query OR UPPER(m.effectiveName) LIKE CONCAT('%',UPPER(:query),'%') OR UPPER(m.user.name) LIKE CONCAT('%',UPPER(:query),'%'))")
    fun findLike(@Param("guildId") guildId: Long, @Param("query") query: String?): List<LocalMember>
}