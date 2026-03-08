package ru.sablebot.api.dto.feed

import ru.sablebot.common.persistence.entity.FeedType
import java.time.Instant

data class FeedResponse(
    val id: Long,
    val guildId: String,
    val feedType: FeedType,
    val targetIdentifier: String,
    val targetChannelId: String,
    val checkIntervalMinutes: Int,
    val embedConfig: Map<String, Any>?,
    val enabled: Boolean,
    val lastCheckTime: Instant?,
    val lastItemId: String?,
    val createdAt: Instant
)
