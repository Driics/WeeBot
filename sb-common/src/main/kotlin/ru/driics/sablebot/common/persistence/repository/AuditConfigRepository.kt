package ru.driics.sablebot.common.persistence.repository

import org.springframework.stereotype.Repository
import ru.driics.sablebot.common.persistence.entity.AuditConfig
import ru.driics.sablebot.common.persistence.repository.base.GuildRepository

@Repository
interface AuditConfigRepository: GuildRepository<AuditConfig>