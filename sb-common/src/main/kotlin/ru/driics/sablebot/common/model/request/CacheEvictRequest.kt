package ru.driics.sablebot.common.model.request

import java.io.Serializable

data class CacheEvictRequest(
    val cacheName: String = "",
    val guildId: Long = 0L
) : Serializable
