package ru.driics.sablebot.common.model.discord

import ru.driics.sablebot.common.model.FeatureSet
import java.io.Serializable

data class GuildDTO(
    val id: String,
    val name: String,
    val iconURL: String,
    val available: Boolean,
    val featureSet: Set<FeatureSet>,
    val defaultMusicChannelId: String
): Serializable