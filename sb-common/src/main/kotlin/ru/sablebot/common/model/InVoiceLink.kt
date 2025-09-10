package ru.sablebot.common.model

import kotlinx.serialization.Serializable

@Serializable
data class InVoiceLink(
    val channelId: String,
    val roleId: String,
)
