package ru.driics.sablebot.common.model.discord

import kotlinx.serialization.Serializable

@Serializable
class TextChannelDTO(
    id: String,
    name: String,
    permission: Long,
    val topic: String? = null,
    val isNSFW: Boolean = false,
    val isCanTalk: Boolean = false,
) : ChannelDTO(id, name, permission)
