package ru.sablebot.api.dto.feed

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size

data class UpdateFeedRequest(
    @field:Size(max = 255)
    val targetIdentifier: String? = null,

    val targetChannelId: String? = null,

    @field:Min(1)
    val checkIntervalMinutes: Int? = null,

    val embedConfig: Map<String, Any>? = null,

    val enabled: Boolean? = null
)
