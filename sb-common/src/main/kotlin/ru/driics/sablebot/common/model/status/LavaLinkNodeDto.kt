package ru.driics.sablebot.common.model.status

import kotlinx.serialization.Serializable

@Serializable
data class LavaLinkNodeDto(
    var name: String = "",
    var available: Boolean = false,
    var players: Int = 0,
    var playingPlayers: Int = 0,
    var systemLoad: Double = 0.0,
    var lavalinkLoad: Double = 0.0
)
