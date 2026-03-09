package ru.sablebot.api.dto.reactionrole

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import ru.sablebot.common.model.ReactionRoleMenuType
import java.time.Instant

// Response DTOs

data class ReactionRoleMenuResponse(
    val id: Long,
    val guildId: String,
    val channelId: String,
    val messageId: String,
    val title: String,
    val description: String?,
    val menuType: ReactionRoleMenuType,
    val createdAt: Instant,
    val updatedAt: Instant?,
    val active: Boolean,
    val items: List<ReactionRoleMenuItemResponse> = emptyList()
)

data class ReactionRoleMenuItemResponse(
    val id: Long,
    val menuId: Long,
    val guildId: String,
    val groupId: Long?,
    val roleId: String,
    val emoji: String?,
    val label: String,
    val description: String?,
    val displayOrder: Int,
    val toggleable: Boolean,
    val requiredRoleIds: List<String>?,
    val active: Boolean
)

data class ReactionRoleGroupResponse(
    val id: Long,
    val guildId: String,
    val name: String,
    val description: String?,
    val createdAt: Instant,
    val updatedAt: Instant?,
    val active: Boolean
)

data class ReactionRoleMenuListResponse(
    val menus: List<ReactionRoleMenuResponse>,
    val total: Long,
    val page: Int,
    val size: Int
)

data class ReactionRoleGroupListResponse(
    val groups: List<ReactionRoleGroupResponse>,
    val total: Long,
    val page: Int,
    val size: Int
)

// Request DTOs

data class CreateReactionRoleMenuRequest(
    @field:NotBlank
    @field:Size(max = 256)
    val title: String,

    @field:Size(max = 2048)
    val description: String?,

    @field:NotNull
    val menuType: ReactionRoleMenuType,

    @field:NotBlank
    val channelId: String,

    val items: List<CreateReactionRoleMenuItemRequest> = emptyList()
)

data class UpdateReactionRoleMenuRequest(
    @field:Size(max = 256)
    val title: String?,

    @field:Size(max = 2048)
    val description: String?,

    val menuType: ReactionRoleMenuType?,

    val channelId: String?,

    val active: Boolean?
)

data class CreateReactionRoleMenuItemRequest(
    val groupId: Long?,

    @field:NotBlank
    val roleId: String,

    @field:Size(max = 100)
    val emoji: String?,

    @field:NotBlank
    @field:Size(max = 256)
    val label: String,

    @field:Size(max = 1024)
    val description: String?,

    val displayOrder: Int = 0,

    val toggleable: Boolean = true,

    val requiredRoleIds: List<String>? = null
)

data class UpdateReactionRoleMenuItemRequest(
    val groupId: Long?,

    val roleId: String?,

    @field:Size(max = 100)
    val emoji: String?,

    @field:Size(max = 256)
    val label: String?,

    @field:Size(max = 1024)
    val description: String?,

    val displayOrder: Int?,

    val toggleable: Boolean?,

    val requiredRoleIds: List<String>?,

    val active: Boolean?
)

data class CreateReactionRoleGroupRequest(
    @field:NotBlank
    @field:Size(max = 256)
    val name: String,

    @field:Size(max = 1024)
    val description: String?
)

data class UpdateReactionRoleGroupRequest(
    @field:Size(max = 256)
    val name: String?,

    @field:Size(max = 1024)
    val description: String?,

    val active: Boolean?
)

// Action Response DTOs

data class ReactionRoleActionResponse(
    val success: Boolean,
    val id: Long?,
    val message: String
)

data class PostMenuResponse(
    val success: Boolean,
    val messageId: String?,
    val message: String
)
