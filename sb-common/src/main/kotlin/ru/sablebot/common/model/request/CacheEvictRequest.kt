package ru.sablebot.common.model.request

data class CacheEvictRequest(
    val cacheName: String,
    val guildId: Long
)