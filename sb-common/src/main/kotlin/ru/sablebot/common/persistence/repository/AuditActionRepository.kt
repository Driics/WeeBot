package ru.sablebot.common.persistence.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.stereotype.Repository
import ru.sablebot.common.persistence.entity.AuditAction
import ru.sablebot.common.persistence.repository.base.GuildRepository
import java.util.*

@Repository
interface AuditActionRepository : GuildRepository<AuditAction>, JpaSpecificationExecutor<AuditAction> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    fun deleteByActionDateBefore(expiryDate: Date): Int

    fun findAllByGuildId(guildId: Long, pageable: Pageable): Page<AuditAction>
}