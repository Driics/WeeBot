package ru.driics.sablebot.common.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("sablebot.common")
class CommonProperties {

    var jmx: Jmx = Jmx()
    var discord: Discord = Discord()
    var execution: Execution = Execution()
    var rabbitMQ: RabbitMQ = RabbitMQ()
    var branding: Branding = Branding()
    var domainCache: DomainCache = DomainCache()
    var youTubeApiKeys: List<String> = emptyList()

    data class Jmx(
        var enabled: Boolean = false,
        var port: Int = 9875
    )

    data class Discord(
        var defaultPrefix: String = "!",
        var defaultAccentColor: String = "#FFA550",
        var superUserId: String? = null
    )

    data class Execution(
        var corePoolSize: Int = 5,
        var maxPoolSize: Int = 5,
        var schedulerPoolSize: Int = 10
    )

    data class RabbitMQ(
        var hostname: String = "localhost",
        var port: Int = 8000,
        var username: String? = null,
        var password: String? = null
    )

    data class Branding(
        var avatarUrl: String? = null,
        var avatarSmallUrl: String? = null,
        var copyrightIconUrl: String? = null,
        var websiteUrl: String? = null,
        var websiteAliases: Set<String> = emptySet()
    )

    data class DomainCache(
        var auditConfig: Boolean = true,
        var guildConfig: Boolean = true,
        var moderationConfig: Boolean = true,
        var musicConfig: Boolean = true,
        var rankingConfig: Boolean = true,
        var reactionRouletteConfig: Boolean = true,
        var welcomeConfig: Boolean = true
    )
}