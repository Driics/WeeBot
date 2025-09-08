package ru.driics.sablebot.common.service.impl

import org.springframework.stereotype.Service
import ru.driics.sablebot.common.configuration.CommonProperties
import ru.driics.sablebot.common.persistence.entity.ModerationConfig
import ru.driics.sablebot.common.persistence.repository.ModerationConfigRepository
import ru.driics.sablebot.common.service.ModerationConfigService

@Service
class ModerationConfigServiceImpl(
    repository: ModerationConfigRepository,
    commonProperties: CommonProperties
): AbstractDomainServiceImpl<ModerationConfig, ModerationConfigRepository>(repository, commonProperties.domainCache.moderationConfig),
    ModerationConfigService {
    override fun createNew(guildId: Long): ModerationConfig {
        val moderationConfig = ModerationConfig(guildId)
        moderationConfig.cooldownIgnored = true
        moderationConfig.actions = mutableListOf()
        return moderationConfig
    }

    override fun getDomainClass(): Class<ModerationConfig> = ModerationConfig::class.java
}