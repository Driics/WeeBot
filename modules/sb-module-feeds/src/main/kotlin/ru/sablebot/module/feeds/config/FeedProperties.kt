package ru.sablebot.module.feeds.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("sablebot.feeds")
open class FeedProperties {

    var limits: Limits = Limits()
    var polling: Polling = Polling()
    var reddit: Reddit = Reddit()
    var twitch: Twitch = Twitch()
    var youtube: YouTube = YouTube()

    data class Limits(
        var maxFeedsPerGuild: Int = 25,
        var maxNotificationHistoryDays: Int = 30
    )

    data class Polling(
        var defaultIntervalSeconds: Int = 300,
        var redditIntervalSeconds: Int = 300,
        var twitchIntervalSeconds: Int = 120,
        var youtubeIntervalSeconds: Int = 600,
        var corePoolSize: Int = 2,
        var maxPoolSize: Int = 4
    )

    data class Reddit(
        var enabled: Boolean = true,
        var userAgent: String = "SableBot Discord Bot",
        var rateLimitRequestsPerMinute: Int = 60
    )

    data class Twitch(
        var enabled: Boolean = true,
        var clientId: String = "",
        var clientSecret: String = "",
        var rateLimitRequestsPerMinute: Int = 800
    )

    data class YouTube(
        var enabled: Boolean = true,
        var apiKey: String = "",
        var rateLimitQuotaPerDay: Int = 10000
    )
}
