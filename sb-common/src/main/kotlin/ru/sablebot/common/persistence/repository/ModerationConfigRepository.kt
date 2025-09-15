package ru.sablebot.common.persistence.repository

import org.springframework.stereotype.Repository
import ru.sablebot.common.persistence.entity.ModerationConfig
import ru.sablebot.common.persistence.repository.base.GuildRepository

@Repository
interface ModerationConfigRepository: GuildRepository<ModerationConfig>