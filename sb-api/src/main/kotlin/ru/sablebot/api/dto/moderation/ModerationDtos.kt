package ru.sablebot.api.dto.moderation

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import ru.sablebot.common.model.ModerationCaseType
import java.time.Instant

data class CaseResponse(
    val id: Long,
    val caseNumber: Int,
    val actionType: ModerationCaseType,
    val moderatorId: String,
    val moderatorName: String,
    val targetId: String,
    val targetName: String,
    val reason: String?,
    val duration: Long?,
    val createdAt: Instant,
    val active: Boolean
)

data class CaseListResponse(
    val cases: List<CaseResponse>,
    val total: Long,
    val page: Int,
    val size: Int
)

data class BanRequest(
    @field:NotBlank
    val targetId: String,
    @field:Size(max = 512)
    val reason: String?,
    val duration: Long? = null,
    @field:Min(0) @field:Max(7)
    val deleteMessageDays: Int = 0
)

data class KickRequest(
    @field:NotBlank
    val targetId: String,
    @field:Size(max = 512)
    val reason: String?
)

data class WarnRequest(
    @field:NotBlank
    val targetId: String,
    @field:Size(max = 512)
    val reason: String?
)

data class ModerationActionResponse(
    val success: Boolean,
    val caseNumber: Int?,
    val message: String
)
