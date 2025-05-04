package ru.driics.sablebot.common.persistence.repository

import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.stereotype.Repository
import ru.driics.sablebot.common.persistence.entity.AuditAction
import ru.driics.sablebot.common.persistence.repository.base.GuildRepository
import java.util.*

@Repository
interface AuditActionRepository : GuildRepository<AuditAction>, JpaSpecificationExecutor<AuditAction> {
    @Modifying
    fun deleteByActionDateBefore(expiryDate: Date)
}