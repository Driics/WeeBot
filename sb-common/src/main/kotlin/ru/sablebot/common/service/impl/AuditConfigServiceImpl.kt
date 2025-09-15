package ru.sablebot.common.service.impl

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import ru.sablebot.common.configuration.CommonProperties
import ru.sablebot.common.persistence.entity.AuditConfig
import ru.sablebot.common.persistence.repository.AuditConfigRepository
import ru.sablebot.common.service.AuditConfigService

@Service
class AuditConfigServiceImpl @Autowired constructor(
    repository: AuditConfigRepository,
    commonProperties: CommonProperties
) : AbstractDomainServiceImpl<AuditConfig, AuditConfigRepository>(repository, commonProperties.domainCache.auditConfig), AuditConfigService {
    override fun createNew(guildId: Long): AuditConfig = AuditConfig(guildId)

    override fun getDomainClass(): Class<AuditConfig> = AuditConfig::class.java
}