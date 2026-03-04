package ru.sablebot.api.security.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sablebot.cookie")
data class CookieProperties(
    val secure: Boolean = true
)
