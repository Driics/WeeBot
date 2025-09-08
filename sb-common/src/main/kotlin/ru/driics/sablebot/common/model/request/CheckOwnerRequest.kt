package ru.driics.sablebot.common.model.request

import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.entities.channel.ChannelType

@Serializable
data class CheckOwnerRequest(
    val type: ChannelType,
    val channelId: String,
    val userId: String,
)
