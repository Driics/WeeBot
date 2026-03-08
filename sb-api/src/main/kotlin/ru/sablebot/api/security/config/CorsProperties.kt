package ru.sablebot.api.security.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sablebot.cors")
data class CorsProperties(
    val allowedOrigins: String = "http://localhost:3000"
)
