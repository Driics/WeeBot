package ru.sablebot.common.service.impl

import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import ru.sablebot.common.configuration.CommonProperties
import ru.sablebot.common.persistence.entity.MusicConfig
import ru.sablebot.common.persistence.repository.MusicConfigRepository
import ru.sablebot.common.service.MusicConfigService

@Service
class MusicConfigServiceImpl(
    repository: MusicConfigRepository,
    commonProperties: CommonProperties
) : AbstractDomainServiceImpl<MusicConfig, MusicConfigRepository>(
    repository,
    commonProperties.domainCache.musicConfig
), MusicConfigService {
    override fun createNew(guildId: Long): MusicConfig =
        MusicConfig(guildId).apply {
            voiceVolume = 100
        }

    @Transactional
    override fun updateVolume(guildId: Long, volume: Int) {
        repository.updateVolume(guildId, volume)
        cacheManager.evict(MusicConfig::class.java, guildId)
    }

    override fun getDomainClass(): Class<MusicConfig> = MusicConfig::class.java
}