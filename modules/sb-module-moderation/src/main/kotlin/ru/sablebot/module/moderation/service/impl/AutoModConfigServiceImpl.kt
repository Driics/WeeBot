package ru.sablebot.module.moderation.service.impl

import org.springframework.stereotype.Service
import ru.sablebot.common.configuration.CommonProperties
import ru.sablebot.common.persistence.entity.AutoModConfig
import ru.sablebot.common.persistence.repository.AutoModConfigRepository
import ru.sablebot.common.service.impl.AbstractDomainServiceImpl
import ru.sablebot.module.moderation.service.IAutoModConfigService

@Service
class AutoModConfigServiceImpl(
    repository: AutoModConfigRepository,
    commonProperties: CommonProperties
) : AbstractDomainServiceImpl<AutoModConfig, AutoModConfigRepository>(repository, commonProperties.domainCache.autoModConfig),
    IAutoModConfigService {

    override fun createNew(guildId: Long): AutoModConfig = AutoModConfig(guildId)

    override fun getDomainClass(): Class<AutoModConfig> = AutoModConfig::class.java
}
