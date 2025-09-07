package ru.driics.sablebot.common.model.request

import net.dv8tion.jda.api.entities.channel.ChannelType
import java.io.Serializable

data class CheckOwnerRequest(
    val type: ChannelType,
    val channelId: String,
    val userId: String,
): Serializable
