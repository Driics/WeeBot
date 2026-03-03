package ru.sablebot.common.persistence.repository

import org.springframework.stereotype.Repository
import ru.sablebot.common.persistence.entity.AutoModConfig
import ru.sablebot.common.persistence.repository.base.GuildRepository

@Repository
interface AutoModConfigRepository : GuildRepository<AutoModConfig>
