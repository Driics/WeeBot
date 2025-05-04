package ru.driics.sablebot.common.persistence.repository

import org.springframework.stereotype.Repository
import ru.driics.sablebot.common.persistence.entity.ModerationConfig
import ru.driics.sablebot.common.persistence.repository.base.GuildRepository

@Repository
interface ModerationConfigRepository: GuildRepository<ModerationConfig>