package ru.driics.sablebot.common.model.discord

import kotlinx.serialization.Serializable

@Serializable
data class ChannelDTO(
    val id: String,
    val name: String,
    val permission: Long
)