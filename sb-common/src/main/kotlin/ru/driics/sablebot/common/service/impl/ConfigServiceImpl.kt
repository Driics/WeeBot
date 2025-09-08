package ru.driics.sablebot.common.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.driics.sablebot.common.configuration.CommonProperties
import ru.driics.sablebot.common.persistence.entity.GuildConfig
import ru.driics.sablebot.common.persistence.repository.GuildConfigRepository
import ru.driics.sablebot.common.service.ConfigService
import ru.driics.sablebot.common.utils.LocaleUtils

@Service
open class ConfigServiceImpl(
    repository: GuildConfigRepository,
    private val commonProperties: CommonProperties
) : AbstractDomainServiceImpl<GuildConfig, GuildConfigRepository>(
    repository,
    commonProperties.domainCache.guildConfig
), ConfigService {

    @Transactional(readOnly = true)
    override fun getPrefix(guildId: Long): String =
        repository.findPrefixByGuildId(guildId) ?: commonProperties.discord.defaultPrefix

    @Transactional(readOnly = true)
    override fun getLocale(guildId: Long): String =
        repository.findLocaleByGuildId(guildId) ?: LocaleUtils.DEFAULT_LOCALE

    @Transactional(readOnly = true)
    override fun getColor(guildId: Long): String =
        repository.findColorByGuildId(guildId) ?: commonProperties.discord.defaultAccentColor

    override fun createNew(guildId: Long): GuildConfig =
        GuildConfig(guildId).apply {
            prefix = commonProperties.discord.defaultPrefix
            color = commonProperties.discord.defaultAccentColor
            locale = LocaleUtils.DEFAULT_LOCALE
            commandLocale = LocaleUtils.DEFAULT_LOCALE
            timeZone = "Etc/Greenwich"
        }

    override fun getDomainClass(): Class<GuildConfig> = GuildConfig::class.java
}