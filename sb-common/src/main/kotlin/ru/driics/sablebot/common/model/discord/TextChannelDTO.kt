package ru.driics.sablebot.common.model.discord

import kotlinx.serialization.SerialName

class TextChannelDTO(
    id: String,
    name: String,
    permission: Long,
    val topic: String? = null,
    @SerialName("isNSFW")
    val isNSFW: Boolean = false,
    @SerialName("isCanTalk")
    val canTalk: Boolean = false,
) : ChannelDTO(id, name, permission)
