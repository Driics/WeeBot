package ru.sablebot.api.security.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sablebot.jwt")
data class JwtProperties(
    val secret: String,
    val expirationMs: Long = 86400000 // 24 hours
)
