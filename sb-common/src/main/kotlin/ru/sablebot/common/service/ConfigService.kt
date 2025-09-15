package ru.sablebot.common.service

import ru.sablebot.common.persistence.entity.GuildConfig

interface ConfigService : DomainService<GuildConfig> {
    fun getPrefix(guildId: Long): String

    fun getLocale(guildId: Long): String

    fun getColor(guildId: Long): String
}