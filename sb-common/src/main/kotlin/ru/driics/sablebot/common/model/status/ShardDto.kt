package ru.driics.sablebot.common.model.status

import kotlinx.serialization.Serializable

@Serializable
data class ShardDto(
    var id: Int = 0,
    var guilds: Long = 0,
    var users: Long = 0,
    var channels: Long = 0,
    var ping: Long = 0,
    var connected: Boolean = false
)
