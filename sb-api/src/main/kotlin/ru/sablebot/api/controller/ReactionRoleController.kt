package ru.sablebot.api.controller

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import ru.sablebot.api.dto.reactionrole.*
import ru.sablebot.api.security.annotation.RequireGuildPermission
import ru.sablebot.api.service.ReactionRoleApiService
import ru.sablebot.common.model.ReactionRoleMenuType

@RestController
@RequestMapping("/api/guilds/{guildId}")
class ReactionRoleController(
    private val reactionRoleApiService: ReactionRoleApiService
) {

    // Menu endpoints

    @GetMapping("/reaction-roles/menus")
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun getMenus(
        @PathVariable guildId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) menuType: ReactionRoleMenuType?
    ): ResponseEntity<ReactionRoleMenuListResponse> {
        return ResponseEntity.ok(reactionRoleApiService.getMenus(guildId.toLong(), page, size, menuType))
    }

    @GetMapping("/reaction-roles/menus/{menuId}")
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun getMenu(
        @PathVariable guildId: String,
        @PathVariable menuId: Long
    ): ResponseEntity<ReactionRoleMenuResponse> {
        val menu = reactionRoleApiService.getMenu(guildId.toLong(), menuId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(menu)
    }

    @PostMapping("/reaction-roles/menus")
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun createMenu(
        @PathVariable guildId: String,
        @Valid @RequestBody request: CreateReactionRoleMenuRequest
    ): ResponseEntity<ReactionRoleActionResponse> {
        val response = reactionRoleApiService.createMenu(guildId.toLong(), request)
        return ResponseEntity.ok(response)
    }

    @PutMapping("/reaction-roles/menus/{menuId}")
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun updateMenu(
        @PathVariable guildId: String,
        @PathVariable menuId: Long,
        @Valid @RequestBody request: UpdateReactionRoleMenuRequest
    ): ResponseEntity<ReactionRoleActionResponse> {
        val response = reactionRoleApiService.updateMenu(guildId.toLong(), menuId, request)
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @DeleteMapping("/reaction-roles/menus/{menuId}")
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun deleteMenu(
        @PathVariable guildId: String,
        @PathVariable menuId: Long
    ): ResponseEntity<ReactionRoleActionResponse> {
        val response = reactionRoleApiService.deleteMenu(guildId.toLong(), menuId)
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/reaction-roles/menus/{menuId}/post")
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun postMenu(
        @PathVariable guildId: String,
        @PathVariable menuId: Long
    ): ResponseEntity<PostMenuResponse> {
        val response = reactionRoleApiService.requestPostMenu(guildId.toLong(), menuId)
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.badRequest().body(response)
        }
    }

    @PostMapping("/reaction-roles/menus/{menuId}/update")
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun updatePostedMenu(
        @PathVariable guildId: String,
        @PathVariable menuId: Long
    ): ResponseEntity<PostMenuResponse> {
        val response = reactionRoleApiService.requestUpdateMenu(guildId.toLong(), menuId)
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.badRequest().body(response)
        }
    }

    // Menu Item endpoints

    @PostMapping("/reaction-roles/menus/{menuId}/items")
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun createMenuItem(
        @PathVariable guildId: String,
        @PathVariable menuId: Long,
        @Valid @RequestBody request: CreateReactionRoleMenuItemRequest
    ): ResponseEntity<ReactionRoleActionResponse> {
        val response = reactionRoleApiService.createMenuItem(guildId.toLong(), menuId, request)
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @PutMapping("/reaction-roles/items/{itemId}")
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun updateMenuItem(
        @PathVariable guildId: String,
        @PathVariable itemId: Long,
        @Valid @RequestBody request: UpdateReactionRoleMenuItemRequest
    ): ResponseEntity<ReactionRoleActionResponse> {
        val response = reactionRoleApiService.updateMenuItem(guildId.toLong(), itemId, request)
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @DeleteMapping("/reaction-roles/items/{itemId}")
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun deleteMenuItem(
        @PathVariable guildId: String,
        @PathVariable itemId: Long
    ): ResponseEntity<ReactionRoleActionResponse> {
        val response = reactionRoleApiService.deleteMenuItem(guildId.toLong(), itemId)
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    // Group endpoints

    @GetMapping("/reaction-roles/groups")
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun getGroups(
        @PathVariable guildId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<ReactionRoleGroupListResponse> {
        return ResponseEntity.ok(reactionRoleApiService.getGroups(guildId.toLong(), page, size))
    }

    @GetMapping("/reaction-roles/groups/{groupId}")
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun getGroup(
        @PathVariable guildId: String,
        @PathVariable groupId: Long
    ): ResponseEntity<ReactionRoleGroupResponse> {
        val group = reactionRoleApiService.getGroup(guildId.toLong(), groupId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(group)
    }

    @PostMapping("/reaction-roles/groups")
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun createGroup(
        @PathVariable guildId: String,
        @Valid @RequestBody request: CreateReactionRoleGroupRequest
    ): ResponseEntity<ReactionRoleActionResponse> {
        val response = reactionRoleApiService.createGroup(guildId.toLong(), request)
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.badRequest().body(response)
        }
    }

    @PutMapping("/reaction-roles/groups/{groupId}")
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun updateGroup(
        @PathVariable guildId: String,
        @PathVariable groupId: Long,
        @Valid @RequestBody request: UpdateReactionRoleGroupRequest
    ): ResponseEntity<ReactionRoleActionResponse> {
        val response = reactionRoleApiService.updateGroup(guildId.toLong(), groupId, request)
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.badRequest().body(response)
        }
    }

    @DeleteMapping("/reaction-roles/groups/{groupId}")
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun deleteGroup(
        @PathVariable guildId: String,
        @PathVariable groupId: Long
    ): ResponseEntity<ReactionRoleActionResponse> {
        val response = reactionRoleApiService.deleteGroup(guildId.toLong(), groupId)
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.notFound().build()
        }
    }
}
