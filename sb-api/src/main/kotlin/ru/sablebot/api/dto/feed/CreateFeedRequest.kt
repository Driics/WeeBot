package ru.sablebot.api.dto.feed

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import ru.sablebot.common.persistence.entity.FeedType

data class CreateFeedRequest(
    @field:NotNull
    val feedType: FeedType,

    @field:NotBlank
    @field:Size(max = 255)
    val targetIdentifier: String,

    @field:NotBlank
    val targetChannelId: String,

    @field:Min(1)
    val checkIntervalMinutes: Int = 15,

    val embedConfig: Map<String, Any>? = null,

    val enabled: Boolean = true
)
