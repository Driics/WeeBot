package ru.sablebot.api.security.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sablebot.discord")
data class DiscordProperties(
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String,
    val botToken: String = ""
)
