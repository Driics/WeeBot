package ru.sablebot.common.worker.modules.reactionroles.service

import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.sablebot.common.model.AuditActionType
import ru.sablebot.common.model.ReactionRoleMenuType
import ru.sablebot.common.persistence.entity.ReactionRoleGroup
import ru.sablebot.common.persistence.entity.ReactionRoleMenu
import ru.sablebot.common.persistence.entity.ReactionRoleMenuItem
import ru.sablebot.common.persistence.repository.ReactionRoleGroupRepository
import ru.sablebot.common.persistence.repository.ReactionRoleMenuItemRepository
import ru.sablebot.common.persistence.repository.ReactionRoleMenuRepository
import ru.sablebot.common.worker.modules.audit.service.AuditService
import java.time.Instant

@Service
open class ReactionRoleServiceImpl @Autowired constructor(
    private val menuRepository: ReactionRoleMenuRepository,
    private val menuItemRepository: ReactionRoleMenuItemRepository,
    private val groupRepository: ReactionRoleGroupRepository,
    private val auditService: AuditService
) : ReactionRoleService {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    // ===== Menu CRUD Operations =====

    @Transactional
    override fun createMenu(
        guildId: Long,
        channelId: String,
        messageId: String,
        title: String,
        description: String?,
        menuType: ReactionRoleMenuType
    ): ReactionRoleMenu {
        log.info { "Creating reaction role menu for guild $guildId in channel $channelId" }

        val menu = ReactionRoleMenu().apply {
            this.guildId = guildId
            this.channelId = channelId
            this.messageId = messageId
            this.title = title
            this.description = description
            this.menuType = menuType
            this.createdAt = Instant.now()
            this.active = true
        }

        return menuRepository.save(menu)
    }

    @Transactional(readOnly = true)
    override fun getMenu(menuId: Long): ReactionRoleMenu? {
        return menuRepository.findById(menuId).orElse(null)
    }

    @Transactional(readOnly = true)
    override fun getMenuByMessage(channelId: String, messageId: String): ReactionRoleMenu? {
        return menuRepository.findByChannelIdAndMessageId(channelId, messageId)
    }

    @Transactional(readOnly = true)
    override fun getActiveMenus(guildId: Long): List<ReactionRoleMenu> {
        return menuRepository.findByGuildIdAndActive(guildId, true)
    }

    @Transactional(readOnly = true)
    override fun getAllMenus(guildId: Long): List<ReactionRoleMenu> {
        return menuRepository.findAllByGuildId(guildId)
    }

    @Transactional
    override fun updateMenu(menu: ReactionRoleMenu): ReactionRoleMenu {
        menu.updatedAt = Instant.now()
        return menuRepository.save(menu)
    }

    @Transactional
    override fun deleteMenu(menuId: Long): Boolean {
        val menu = getMenu(menuId) ?: return false
        menu.active = false
        menu.updatedAt = Instant.now()
        menuRepository.save(menu)

        // Also soft-delete all items in the menu
        deleteMenuItems(menuId)

        log.info { "Deleted reaction role menu $menuId" }
        return true
    }

    // ===== Menu Item CRUD Operations =====

    @Transactional
    override fun createMenuItem(
        menuId: Long,
        guildId: Long,
        roleId: String,
        emoji: String?,
        label: String,
        description: String?,
        displayOrder: Int,
        toggleable: Boolean,
        requiredRoleIds: String?,
        groupId: Long?
    ): ReactionRoleMenuItem {
        log.info { "Creating menu item for menu $menuId with role $roleId" }

        val menuItem = ReactionRoleMenuItem().apply {
            this.menuId = menuId
            this.guildId = guildId
            this.roleId = roleId
            this.emoji = emoji
            this.label = label
            this.description = description
            this.displayOrder = displayOrder
            this.toggleable = toggleable
            this.requiredRoleIds = requiredRoleIds
            this.groupId = groupId
            this.active = true
        }

        return menuItemRepository.save(menuItem)
    }

    @Transactional(readOnly = true)
    override fun getMenuItem(itemId: Long): ReactionRoleMenuItem? {
        return menuItemRepository.findById(itemId).orElse(null)
    }

    @Transactional(readOnly = true)
    override fun getMenuItems(menuId: Long): List<ReactionRoleMenuItem> {
        return menuItemRepository.findByMenuIdOrderByDisplayOrderAsc(menuId)
    }

    @Transactional(readOnly = true)
    override fun getActiveMenuItems(menuId: Long): List<ReactionRoleMenuItem> {
        return menuItemRepository.findByMenuIdAndActiveOrderByDisplayOrderAsc(menuId, true)
    }

    @Transactional(readOnly = true)
    override fun getMenuItemsByGroup(groupId: Long): List<ReactionRoleMenuItem> {
        return menuItemRepository.findByGroupIdAndActive(groupId, true)
    }

    @Transactional
    override fun updateMenuItem(item: ReactionRoleMenuItem): ReactionRoleMenuItem {
        return menuItemRepository.save(item)
    }

    @Transactional
    override fun deleteMenuItem(itemId: Long): Boolean {
        val item = getMenuItem(itemId) ?: return false
        item.active = false
        menuItemRepository.save(item)

        log.info { "Deleted menu item $itemId" }
        return true
    }

    @Transactional
    override fun deleteMenuItems(menuId: Long) {
        val items = getMenuItems(menuId)
        items.forEach { item ->
            item.active = false
            menuItemRepository.save(item)
        }
        log.info { "Deleted ${items.size} menu items for menu $menuId" }
    }

    // ===== Group CRUD Operations =====

    @Transactional
    override fun createGroup(guildId: Long, name: String, description: String?): ReactionRoleGroup {
        log.info { "Creating reaction role group '$name' for guild $guildId" }

        val group = ReactionRoleGroup().apply {
            this.guildId = guildId
            this.name = name
            this.description = description
            this.createdAt = Instant.now()
            this.active = true
        }

        return groupRepository.save(group)
    }

    @Transactional(readOnly = true)
    override fun getGroup(groupId: Long): ReactionRoleGroup? {
        return groupRepository.findById(groupId).orElse(null)
    }

    @Transactional(readOnly = true)
    override fun getGroupByName(guildId: Long, name: String): ReactionRoleGroup? {
        return groupRepository.findByGuildIdAndName(guildId, name)
    }

    @Transactional(readOnly = true)
    override fun getActiveGroups(guildId: Long): List<ReactionRoleGroup> {
        return groupRepository.findByGuildIdAndActive(guildId, true)
    }

    @Transactional(readOnly = true)
    override fun getAllGroups(guildId: Long): List<ReactionRoleGroup> {
        return groupRepository.findAllByGuildId(guildId)
    }

    @Transactional
    override fun updateGroup(group: ReactionRoleGroup): ReactionRoleGroup {
        group.updatedAt = Instant.now()
        return groupRepository.save(group)
    }

    @Transactional
    override fun deleteGroup(groupId: Long): Boolean {
        val group = getGroup(groupId) ?: return false
        group.active = false
        group.updatedAt = Instant.now()
        groupRepository.save(group)

        log.info { "Deleted reaction role group $groupId" }
        return true
    }

    // ===== Validation Methods =====

    @Transactional(readOnly = true)
    override fun validateRoleRequirements(member: Member, item: ReactionRoleMenuItem): Boolean {
        val requiredRoleIds = item.requiredRoleIds
        if (requiredRoleIds.isNullOrBlank()) {
            return true
        }

        val requiredIds = requiredRoleIds.split(",").map { it.trim() }
        return requiredIds.all { roleId ->
            memberHasRole(member, roleId)
        }
    }

    @Transactional(readOnly = true)
    override fun validateRoleAssignment(
        member: Member,
        item: ReactionRoleMenuItem,
        role: Role
    ): ReactionRoleService.ValidationResult {
        // Check if bot can manage the role
        if (!member.guild.selfMember.canInteract(role)) {
            log.warn { "Bot cannot interact with role ${role.id}" }
            return ReactionRoleService.ValidationResult(
                success = false,
                errorMessage = "Bot cannot manage this role (role is higher than bot's highest role)"
            )
        }

        // Check if member meets role requirements
        if (!validateRoleRequirements(member, item)) {
            log.warn { "Member ${member.id} does not meet requirements for role ${role.id}" }
            return ReactionRoleService.ValidationResult(
                success = false,
                errorMessage = "You do not meet the requirements for this role"
            )
        }

        return ReactionRoleService.ValidationResult(success = true)
    }

    @Transactional(readOnly = true)
    override fun memberHasRole(member: Member, roleId: String): Boolean {
        return member.roles.any { it.id == roleId }
    }

    // ===== Role Assignment Methods =====

    @Transactional
    override fun handleRoleInteraction(
        member: Member,
        item: ReactionRoleMenuItem,
        channel: TextChannel?,
        author: Member?
    ): ReactionRoleService.RoleInteractionResult {
        val guild = member.guild
        val role = getRole(guild, item.roleId)

        if (role == null) {
            log.warn { "Role ${item.roleId} not found in guild ${guild.id}" }
            return ReactionRoleService.RoleInteractionResult(
                success = false,
                action = ReactionRoleService.RoleInteractionResult.Action.ERROR,
                errorMessage = "Role not found"
            )
        }

        // Validate the assignment
        val validation = validateRoleAssignment(member, item, role)
        if (!validation.success) {
            return ReactionRoleService.RoleInteractionResult(
                success = false,
                action = ReactionRoleService.RoleInteractionResult.Action.VALIDATION_FAILED,
                errorMessage = validation.errorMessage
            )
        }

        val hasRole = memberHasRole(member, item.roleId)

        return when {
            hasRole && item.toggleable -> {
                // Remove the role if toggleable
                val removed = removeRole(member, role)
                if (removed) {
                    logRoleAction(guild, member, role, channel, author, added = false)
                    ReactionRoleService.RoleInteractionResult(
                        success = true,
                        action = ReactionRoleService.RoleInteractionResult.Action.ROLE_REMOVED
                    )
                } else {
                    ReactionRoleService.RoleInteractionResult(
                        success = false,
                        action = ReactionRoleService.RoleInteractionResult.Action.ERROR,
                        errorMessage = "Failed to remove role"
                    )
                }
            }

            hasRole && !item.toggleable -> {
                // Role already exists and is not toggleable
                ReactionRoleService.RoleInteractionResult(
                    success = false,
                    action = ReactionRoleService.RoleInteractionResult.Action.ROLE_ALREADY_EXISTS,
                    errorMessage = "You already have this role"
                )
            }

            else -> {
                // Add the role
                // If the item is in an exclusive group, remove other roles from the group first
                if (item.groupId != null) {
                    removeGroupRoles(member, item.groupId!!)
                }

                val assigned = assignRole(member, role)
                if (assigned) {
                    logRoleAction(guild, member, role, channel, author, added = true)
                    ReactionRoleService.RoleInteractionResult(
                        success = true,
                        action = ReactionRoleService.RoleInteractionResult.Action.ROLE_ADDED
                    )
                } else {
                    ReactionRoleService.RoleInteractionResult(
                        success = false,
                        action = ReactionRoleService.RoleInteractionResult.Action.ERROR,
                        errorMessage = "Failed to assign role"
                    )
                }
            }
        }
    }

    @Transactional
    override fun assignRole(member: Member, role: Role): Boolean {
        return try {
            member.guild.addRoleToMember(member, role).complete()
            log.info { "Assigned role ${role.id} to member ${member.id} in guild ${member.guild.id}" }
            true
        } catch (e: Exception) {
            log.error(e) { "Failed to assign role ${role.id} to member ${member.id}" }
            false
        }
    }

    @Transactional
    override fun removeRole(member: Member, role: Role): Boolean {
        return try {
            member.guild.removeRoleFromMember(member, role).complete()
            log.info { "Removed role ${role.id} from member ${member.id} in guild ${member.guild.id}" }
            true
        } catch (e: Exception) {
            log.error(e) { "Failed to remove role ${role.id} from member ${member.id}" }
            false
        }
    }

    @Transactional
    override fun removeGroupRoles(member: Member, groupId: Long): Int {
        val groupItems = getMenuItemsByGroup(groupId)
        var removedCount = 0

        groupItems.forEach { item ->
            val role = getRole(member.guild, item.roleId)
            if (role != null && memberHasRole(member, item.roleId)) {
                if (removeRole(member, role)) {
                    removedCount++
                }
            }
        }

        if (removedCount > 0) {
            log.info { "Removed $removedCount roles from group $groupId for member ${member.id}" }
        }

        return removedCount
    }

    // ===== Utility Methods =====

    @Transactional(readOnly = true)
    override fun menuExistsForMessage(channelId: String, messageId: String): Boolean {
        return menuRepository.existsByChannelIdAndMessageId(channelId, messageId)
    }

    @Transactional(readOnly = true)
    override fun countActiveMenus(guildId: Long): Int {
        return menuRepository.countByGuildIdAndActive(guildId, true)
    }

    override fun getRole(guild: Guild, roleId: String): Role? {
        return try {
            guild.getRoleById(roleId)
        } catch (e: Exception) {
            log.error(e) { "Failed to get role $roleId from guild ${guild.id}" }
            null
        }
    }

    // ===== Private Helper Methods =====

    private fun logRoleAction(
        guild: Guild,
        member: Member,
        role: Role,
        channel: TextChannel?,
        author: Member?,
        added: Boolean
    ) {
        val actionType = if (added) {
            AuditActionType.MEMBER_ROLE_ASSIGN
        } else {
            AuditActionType.MEMBER_ROLE_REMOVE
        }

        auditService
            .log(guild.idLong, actionType)
            .withUser(author)
            .withTargetUser(member)
            .withChannel(channel)
            .withAttribute("role_id", role.id)
            .withAttribute("role_name", role.name)
            .withAttribute("reaction_role", "true")
            .save()
    }
}
