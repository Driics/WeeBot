package ru.sablebot.common.service

import ru.sablebot.common.persistence.entity.MusicConfig

interface MusicConfigService : DomainService<MusicConfig> {
    fun updateVolume(guildId: Long, volume: Int)
}