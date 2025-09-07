package ru.driics.sablebot.common.service

import ru.driics.sablebot.common.persistence.entity.GuildConfig

interface ConfigService : DomainService<GuildConfig> {
    fun getPrefix(guildId: Long): String

    fun getLocale(guildId: Long): String

    fun getColor(guildId: Long): String
}