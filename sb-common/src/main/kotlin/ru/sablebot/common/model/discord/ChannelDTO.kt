package ru.sablebot.common.model.discord

import kotlinx.serialization.Serializable

@Serializable
abstract class ChannelDTO(
    val id: String,
    val name: String,
    val permission: Long
)