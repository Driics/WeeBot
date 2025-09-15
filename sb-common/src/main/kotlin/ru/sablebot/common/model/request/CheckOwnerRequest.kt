package ru.sablebot.common.model.request

import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.entities.channel.ChannelType

@Serializable
data class CheckOwnerRequest(
    @Enumerated(EnumType.STRING)
    val type: ChannelType,
    val channelId: String,
    val userId: String,
)
