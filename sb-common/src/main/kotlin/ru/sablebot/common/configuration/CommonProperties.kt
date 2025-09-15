package ru.sablebot.common.configuration

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated

@Validated
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

    class Jmx {
        var enabled: Boolean = false

        @field:Min(1)
        @field:Max(65535)
        var port: Int = 9875
    }

    class Discord {
        var defaultPrefix: String = "!"
        var defaultAccentColor: String = "#FFA550"
        var superUserId: String? = null
    }

    class Execution {
        var corePoolSize: Int = 5
        var maxPoolSize: Int = 5
        var schedulerPoolSize: Int = 10
        var queueCapacity: Int = 10
    }

    class RabbitMQ {
        var hostname: String = "localhost"
        var port: Int = com.rabbitmq.client.ConnectionFactory.DEFAULT_AMQP_PORT
        var username: String = ""
        var password: String = ""
    }

    class Branding {
        var avatarUrl: String = ""
        var avatarSmallUrl: String = ""
        var copyrightIconUrl: String = ""
        var websiteUrl: String = ""
        var websiteAliases: Set<String> = emptySet()
    }

    class DomainCache {
        var auditConfig: Boolean = true
        var guildConfig: Boolean = true
        var moderationConfig: Boolean = true
        var musicConfig: Boolean = true
        var rankingConfig: Boolean = true
        var reactionRouletteConfig: Boolean = true
        var welcomeConfig: Boolean = true
    }
}
