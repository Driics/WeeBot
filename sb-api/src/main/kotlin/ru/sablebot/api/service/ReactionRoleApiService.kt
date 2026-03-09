package ru.sablebot.api.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.sablebot.api.dto.event.ReactionRoleCommandEvent
import ru.sablebot.api.dto.reactionrole.*
import ru.sablebot.common.model.ReactionRoleMenuType
import ru.sablebot.common.persistence.entity.ReactionRoleGroup
import ru.sablebot.common.persistence.entity.ReactionRoleMenu
import ru.sablebot.common.persistence.entity.ReactionRoleMenuItem
import ru.sablebot.common.persistence.repository.ReactionRoleGroupRepository
import ru.sablebot.common.persistence.repository.ReactionRoleMenuItemRepository
import ru.sablebot.common.persistence.repository.ReactionRoleMenuRepository
import java.time.Instant

@Service
class ReactionRoleApiService(
    private val menuRepository: ReactionRoleMenuRepository,
    private val menuItemRepository: ReactionRoleMenuItemRepository,
    private val groupRepository: ReactionRoleGroupRepository,
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {
    private val logger = KotlinLogging.logger {}

    // Menu operations

    @Transactional(readOnly = true)
    fun getMenus(guildId: Long, page: Int, size: Int, menuType: ReactionRoleMenuType?): ReactionRoleMenuListResponse {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val menusPage = if (menuType != null) {
            menuRepository.findByGuildIdAndMenuType(guildId, menuType).let { menus ->
                val startIndex = page * size
                val endIndex = minOf(startIndex + size, menus.size)
                val content = if (startIndex < menus.size) menus.subList(startIndex, endIndex) else emptyList()
                org.springframework.data.domain.PageImpl(content, pageable, menus.size.toLong())
            }
        } else {
            menuRepository.findByGuildId(guildId, pageable)
        }

        return ReactionRoleMenuListResponse(
            menus = menusPage.content.map { it.toMenuResponse() },
            total = menusPage.totalElements,
            page = page,
            size = size
        )
    }

    @Transactional(readOnly = true)
    fun getMenu(guildId: Long, menuId: Long): ReactionRoleMenuResponse? {
        val menu = menuRepository.findById(menuId).orElse(null) ?: return null
        if (menu.guildId != guildId) return null

        val items = menuItemRepository.findByMenuIdAndActiveOrderByDisplayOrderAsc(menuId, true)
        return menu.toMenuResponse(items)
    }

    @Transactional
    fun createMenu(guildId: Long, request: CreateReactionRoleMenuRequest): ReactionRoleActionResponse {
        val menu = ReactionRoleMenu().apply {
            this.guildId = guildId
            this.channelId = request.channelId
            this.messageId = "" // Will be populated after posting to Discord
            this.title = request.title
            this.description = request.description
            this.menuType = request.menuType
            this.createdAt = Instant.now()
            this.active = true
        }

        val savedMenu = menuRepository.save(menu)

        // Create menu items if provided
        request.items.forEachIndexed { index, itemRequest ->
            val item = ReactionRoleMenuItem().apply {
                this.guildId = guildId
                this.menuId = savedMenu.id!!
                this.groupId = itemRequest.groupId
                this.roleId = itemRequest.roleId
                this.emoji = itemRequest.emoji
                this.label = itemRequest.label
                this.description = itemRequest.description
                this.displayOrder = itemRequest.displayOrder.takeIf { it > 0 } ?: index
                this.toggleable = itemRequest.toggleable
                this.requiredRoleIds = itemRequest.requiredRoleIds?.joinToString(",")
                this.active = true
            }
            menuItemRepository.save(item)
        }

        logger.info { "Created reaction role menu ${savedMenu.id} for guild $guildId" }
        return ReactionRoleActionResponse(
            success = true,
            id = savedMenu.id,
            message = "Menu created successfully"
        )
    }

    @Transactional
    fun updateMenu(guildId: Long, menuId: Long, request: UpdateReactionRoleMenuRequest): ReactionRoleActionResponse {
        val menu = menuRepository.findById(menuId).orElse(null)
            ?: return ReactionRoleActionResponse(false, null, "Menu not found")

        if (menu.guildId != guildId) {
            return ReactionRoleActionResponse(false, null, "Menu not found")
        }

        request.title?.let { menu.title = it }
        request.description?.let { menu.description = it }
        request.menuType?.let { menu.menuType = it }
        request.channelId?.let { menu.channelId = it }
        request.active?.let { menu.active = it }
        menu.updatedAt = Instant.now()

        menuRepository.save(menu)

        logger.info { "Updated reaction role menu $menuId for guild $guildId" }
        return ReactionRoleActionResponse(
            success = true,
            id = menuId,
            message = "Menu updated successfully"
        )
    }

    @Transactional
    fun deleteMenu(guildId: Long, menuId: Long): ReactionRoleActionResponse {
        val menu = menuRepository.findById(menuId).orElse(null)
            ?: return ReactionRoleActionResponse(false, null, "Menu not found")

        if (menu.guildId != guildId) {
            return ReactionRoleActionResponse(false, null, "Menu not found")
        }

        // Soft delete menu and all its items
        menu.active = false
        menu.updatedAt = Instant.now()
        menuRepository.save(menu)

        menuItemRepository.findByMenuIdOrderByDisplayOrderAsc(menuId).forEach { item ->
            item.active = false
            menuItemRepository.save(item)
        }

        logger.info { "Deleted reaction role menu $menuId for guild $guildId" }
        return ReactionRoleActionResponse(
            success = true,
            id = menuId,
            message = "Menu deleted successfully"
        )
    }

    // Menu Item operations

    @Transactional
    fun createMenuItem(guildId: Long, menuId: Long, request: CreateReactionRoleMenuItemRequest): ReactionRoleActionResponse {
        val menu = menuRepository.findById(menuId).orElse(null)
            ?: return ReactionRoleActionResponse(false, null, "Menu not found")

        if (menu.guildId != guildId) {
            return ReactionRoleActionResponse(false, null, "Menu not found")
        }

        val item = ReactionRoleMenuItem().apply {
            this.guildId = guildId
            this.menuId = menuId
            this.groupId = request.groupId
            this.roleId = request.roleId
            this.emoji = request.emoji
            this.label = request.label
            this.description = request.description
            this.displayOrder = request.displayOrder
            this.toggleable = request.toggleable
            this.requiredRoleIds = request.requiredRoleIds?.joinToString(",")
            this.active = true
        }

        val savedItem = menuItemRepository.save(item)

        logger.info { "Created menu item ${savedItem.id} for menu $menuId" }
        return ReactionRoleActionResponse(
            success = true,
            id = savedItem.id,
            message = "Menu item created successfully"
        )
    }

    @Transactional
    fun updateMenuItem(guildId: Long, itemId: Long, request: UpdateReactionRoleMenuItemRequest): ReactionRoleActionResponse {
        val item = menuItemRepository.findById(itemId).orElse(null)
            ?: return ReactionRoleActionResponse(false, null, "Menu item not found")

        if (item.guildId != guildId) {
            return ReactionRoleActionResponse(false, null, "Menu item not found")
        }

        request.groupId?.let { item.groupId = it }
        request.roleId?.let { item.roleId = it }
        request.emoji?.let { item.emoji = it }
        request.label?.let { item.label = it }
        request.description?.let { item.description = it }
        request.displayOrder?.let { item.displayOrder = it }
        request.toggleable?.let { item.toggleable = it }
        request.requiredRoleIds?.let { item.requiredRoleIds = it.joinToString(",") }
        request.active?.let { item.active = it }

        menuItemRepository.save(item)

        logger.info { "Updated menu item $itemId for guild $guildId" }
        return ReactionRoleActionResponse(
            success = true,
            id = itemId,
            message = "Menu item updated successfully"
        )
    }

    @Transactional
    fun deleteMenuItem(guildId: Long, itemId: Long): ReactionRoleActionResponse {
        val item = menuItemRepository.findById(itemId).orElse(null)
            ?: return ReactionRoleActionResponse(false, null, "Menu item not found")

        if (item.guildId != guildId) {
            return ReactionRoleActionResponse(false, null, "Menu item not found")
        }

        // Soft delete
        item.active = false
        menuItemRepository.save(item)

        logger.info { "Deleted menu item $itemId for guild $guildId" }
        return ReactionRoleActionResponse(
            success = true,
            id = itemId,
            message = "Menu item deleted successfully"
        )
    }

    // Group operations

    @Transactional(readOnly = true)
    fun getGroups(guildId: Long, page: Int, size: Int): ReactionRoleGroupListResponse {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val groupsPage = groupRepository.findByGuildId(guildId, pageable)

        return ReactionRoleGroupListResponse(
            groups = groupsPage.content.map { it.toGroupResponse() },
            total = groupsPage.totalElements,
            page = page,
            size = size
        )
    }

    @Transactional(readOnly = true)
    fun getGroup(guildId: Long, groupId: Long): ReactionRoleGroupResponse? {
        val group = groupRepository.findById(groupId).orElse(null) ?: return null
        if (group.guildId != guildId) return null
        return group.toGroupResponse()
    }

    @Transactional
    fun createGroup(guildId: Long, request: CreateReactionRoleGroupRequest): ReactionRoleActionResponse {
        // Check if group with same name already exists
        if (groupRepository.existsByGuildIdAndName(guildId, request.name)) {
            return ReactionRoleActionResponse(
                success = false,
                id = null,
                message = "Group with name '${request.name}' already exists"
            )
        }

        val group = ReactionRoleGroup().apply {
            this.guildId = guildId
            this.name = request.name
            this.description = request.description
            this.createdAt = Instant.now()
            this.active = true
        }

        val savedGroup = groupRepository.save(group)

        logger.info { "Created reaction role group ${savedGroup.id} for guild $guildId" }
        return ReactionRoleActionResponse(
            success = true,
            id = savedGroup.id,
            message = "Group created successfully"
        )
    }

    @Transactional
    fun updateGroup(guildId: Long, groupId: Long, request: UpdateReactionRoleGroupRequest): ReactionRoleActionResponse {
        val group = groupRepository.findById(groupId).orElse(null)
            ?: return ReactionRoleActionResponse(false, null, "Group not found")

        if (group.guildId != guildId) {
            return ReactionRoleActionResponse(false, null, "Group not found")
        }

        request.name?.let { newName ->
            if (newName != group.name && groupRepository.existsByGuildIdAndName(guildId, newName)) {
                return ReactionRoleActionResponse(
                    success = false,
                    id = null,
                    message = "Group with name '$newName' already exists"
                )
            }
            group.name = newName
        }
        request.description?.let { group.description = it }
        request.active?.let { group.active = it }
        group.updatedAt = Instant.now()

        groupRepository.save(group)

        logger.info { "Updated reaction role group $groupId for guild $guildId" }
        return ReactionRoleActionResponse(
            success = true,
            id = groupId,
            message = "Group updated successfully"
        )
    }

    @Transactional
    fun deleteGroup(guildId: Long, groupId: Long): ReactionRoleActionResponse {
        val group = groupRepository.findById(groupId).orElse(null)
            ?: return ReactionRoleActionResponse(false, null, "Group not found")

        if (group.guildId != guildId) {
            return ReactionRoleActionResponse(false, null, "Group not found")
        }

        // Soft delete group
        group.active = false
        group.updatedAt = Instant.now()
        groupRepository.save(group)

        // Clear groupId from all menu items in this group
        menuItemRepository.findByGroupIdAndActive(groupId, true).forEach { item ->
            item.groupId = null
            menuItemRepository.save(item)
        }

        logger.info { "Deleted reaction role group $groupId for guild $guildId" }
        return ReactionRoleActionResponse(
            success = true,
            id = groupId,
            message = "Group deleted successfully"
        )
    }

    // Discord operations

    fun requestPostMenu(guildId: Long, menuId: Long): PostMenuResponse {
        val menu = menuRepository.findById(menuId).orElse(null)
            ?: return PostMenuResponse(false, null, "Menu not found")

        if (menu.guildId != guildId) {
            return PostMenuResponse(false, null, "Menu not found")
        }

        val items = menuItemRepository.findByMenuIdAndActiveOrderByDisplayOrderAsc(menuId, true)
        if (items.isEmpty()) {
            return PostMenuResponse(false, null, "Menu has no active items")
        }

        val event = ReactionRoleCommandEvent(
            type = "POST_MENU",
            guildId = guildId.toString(),
            menuId = menuId,
            channelId = menu.channelId,
            title = menu.title,
            description = menu.description,
            menuType = menu.menuType,
            items = items.map { it.toItemResponse() }
        )

        kafkaTemplate.send("sablebot.commands.reactionrole", guildId.toString(), event)
        logger.info { "Post menu request sent for guild $guildId, menu $menuId" }

        return PostMenuResponse(
            success = true,
            messageId = null, // Will be updated by worker after posting
            message = "Menu post request sent to Discord bot"
        )
    }

    fun requestUpdateMenu(guildId: Long, menuId: Long): PostMenuResponse {
        val menu = menuRepository.findById(menuId).orElse(null)
            ?: return PostMenuResponse(false, null, "Menu not found")

        if (menu.guildId != guildId) {
            return PostMenuResponse(false, null, "Menu not found")
        }

        if (menu.messageId.isBlank()) {
            return PostMenuResponse(false, null, "Menu has not been posted yet")
        }

        val items = menuItemRepository.findByMenuIdAndActiveOrderByDisplayOrderAsc(menuId, true)
        if (items.isEmpty()) {
            return PostMenuResponse(false, null, "Menu has no active items")
        }

        val event = ReactionRoleCommandEvent(
            type = "UPDATE_MENU",
            guildId = guildId.toString(),
            menuId = menuId,
            channelId = menu.channelId,
            messageId = menu.messageId,
            title = menu.title,
            description = menu.description,
            menuType = menu.menuType,
            items = items.map { it.toItemResponse() }
        )

        kafkaTemplate.send("sablebot.commands.reactionrole", guildId.toString(), event)
        logger.info { "Update menu request sent for guild $guildId, menu $menuId" }

        return PostMenuResponse(
            success = true,
            messageId = menu.messageId,
            message = "Menu update request sent to Discord bot"
        )
    }

    // Extension functions for entity-to-DTO conversion

    private fun ReactionRoleMenu.toMenuResponse(items: List<ReactionRoleMenuItem>? = null): ReactionRoleMenuResponse {
        val menuItems = items ?: menuItemRepository.findByMenuIdAndActiveOrderByDisplayOrderAsc(this.id!!, true)
        return ReactionRoleMenuResponse(
            id = id!!,
            guildId = guildId.toString(),
            channelId = channelId,
            messageId = messageId,
            title = title,
            description = description,
            menuType = menuType,
            createdAt = createdAt,
            updatedAt = updatedAt,
            active = active,
            items = menuItems.map { it.toItemResponse() }
        )
    }

    private fun ReactionRoleMenuItem.toItemResponse() = ReactionRoleMenuItemResponse(
        id = id!!,
        menuId = menuId,
        guildId = guildId.toString(),
        groupId = groupId,
        roleId = roleId,
        emoji = emoji,
        label = label,
        description = description,
        displayOrder = displayOrder,
        toggleable = toggleable,
        requiredRoleIds = requiredRoleIds?.split(",")?.filter { it.isNotBlank() },
        active = active
    )

    private fun ReactionRoleGroup.toGroupResponse() = ReactionRoleGroupResponse(
        id = id!!,
        guildId = guildId.toString(),
        name = name,
        description = description,
        createdAt = createdAt,
        updatedAt = updatedAt,
        active = active
    )
}
