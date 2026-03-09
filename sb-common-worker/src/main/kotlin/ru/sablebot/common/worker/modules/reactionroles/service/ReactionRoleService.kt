package ru.sablebot.common.worker.modules.reactionroles.service

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import ru.sablebot.common.model.ReactionRoleMenuType
import ru.sablebot.common.persistence.entity.ReactionRoleGroup
import ru.sablebot.common.persistence.entity.ReactionRoleMenu
import ru.sablebot.common.persistence.entity.ReactionRoleMenuItem

/**
 * Service interface for managing reaction role menus, items, and groups.
 * Provides CRUD operations and validation logic for the reaction roles system.
 */
interface ReactionRoleService {

    // ===== Menu CRUD Operations =====

    /**
     * Creates a new reaction role menu.
     *
     * @param guildId the guild ID
     * @param channelId the channel ID where the menu message will be posted
     * @param messageId the message ID of the menu message
     * @param title the menu title
     * @param description optional menu description
     * @param menuType the menu type (REACTIONS, BUTTONS, or BOTH)
     * @return the created menu
     */
    fun createMenu(
        guildId: Long,
        channelId: String,
        messageId: String,
        title: String,
        description: String?,
        menuType: ReactionRoleMenuType
    ): ReactionRoleMenu

    /**
     * Gets a reaction role menu by its ID.
     *
     * @param menuId the menu ID
     * @return the menu or null if not found
     */
    fun getMenu(menuId: Long): ReactionRoleMenu?

    /**
     * Gets a reaction role menu by channel and message ID.
     *
     * @param channelId the channel ID
     * @param messageId the message ID
     * @return the menu or null if not found
     */
    fun getMenuByMessage(channelId: String, messageId: String): ReactionRoleMenu?

    /**
     * Gets all active reaction role menus for a guild.
     *
     * @param guildId the guild ID
     * @return list of active menus
     */
    fun getActiveMenus(guildId: Long): List<ReactionRoleMenu>

    /**
     * Gets all reaction role menus for a guild.
     *
     * @param guildId the guild ID
     * @return list of all menus
     */
    fun getAllMenus(guildId: Long): List<ReactionRoleMenu>

    /**
     * Updates an existing reaction role menu.
     *
     * @param menu the menu to update
     * @return the updated menu
     */
    fun updateMenu(menu: ReactionRoleMenu): ReactionRoleMenu

    /**
     * Soft-deletes a reaction role menu by marking it as inactive.
     *
     * @param menuId the menu ID
     * @return true if deleted successfully
     */
    fun deleteMenu(menuId: Long): Boolean

    // ===== Menu Item CRUD Operations =====

    /**
     * Creates a new reaction role menu item.
     *
     * @param menuId the menu ID
     * @param guildId the guild ID
     * @param roleId the role ID to assign
     * @param emoji optional emoji for reactions
     * @param label the button label
     * @param description optional item description
     * @param displayOrder the display order
     * @param toggleable whether the role can be toggled on/off
     * @param requiredRoleIds comma-separated required role IDs (prerequisites)
     * @param groupId optional group ID for exclusive groups
     * @return the created menu item
     */
    fun createMenuItem(
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
    ): ReactionRoleMenuItem

    /**
     * Gets a reaction role menu item by its ID.
     *
     * @param itemId the item ID
     * @return the menu item or null if not found
     */
    fun getMenuItem(itemId: Long): ReactionRoleMenuItem?

    /**
     * Gets all menu items for a specific menu, ordered by display order.
     *
     * @param menuId the menu ID
     * @return list of menu items
     */
    fun getMenuItems(menuId: Long): List<ReactionRoleMenuItem>

    /**
     * Gets all active menu items for a specific menu, ordered by display order.
     *
     * @param menuId the menu ID
     * @return list of active menu items
     */
    fun getActiveMenuItems(menuId: Long): List<ReactionRoleMenuItem>

    /**
     * Gets all menu items in a specific group.
     *
     * @param groupId the group ID
     * @return list of menu items in the group
     */
    fun getMenuItemsByGroup(groupId: Long): List<ReactionRoleMenuItem>

    /**
     * Updates an existing reaction role menu item.
     *
     * @param item the item to update
     * @return the updated item
     */
    fun updateMenuItem(item: ReactionRoleMenuItem): ReactionRoleMenuItem

    /**
     * Soft-deletes a reaction role menu item by marking it as inactive.
     *
     * @param itemId the item ID
     * @return true if deleted successfully
     */
    fun deleteMenuItem(itemId: Long): Boolean

    /**
     * Deletes all menu items for a specific menu.
     *
     * @param menuId the menu ID
     */
    fun deleteMenuItems(menuId: Long)

    // ===== Group CRUD Operations =====

    /**
     * Creates a new reaction role group for exclusive role selection.
     *
     * @param guildId the guild ID
     * @param name the group name
     * @param description optional group description
     * @return the created group
     */
    fun createGroup(guildId: Long, name: String, description: String?): ReactionRoleGroup

    /**
     * Gets a reaction role group by its ID.
     *
     * @param groupId the group ID
     * @return the group or null if not found
     */
    fun getGroup(groupId: Long): ReactionRoleGroup?

    /**
     * Gets a reaction role group by guild ID and name.
     *
     * @param guildId the guild ID
     * @param name the group name
     * @return the group or null if not found
     */
    fun getGroupByName(guildId: Long, name: String): ReactionRoleGroup?

    /**
     * Gets all active reaction role groups for a guild.
     *
     * @param guildId the guild ID
     * @return list of active groups
     */
    fun getActiveGroups(guildId: Long): List<ReactionRoleGroup>

    /**
     * Gets all reaction role groups for a guild.
     *
     * @param guildId the guild ID
     * @return list of all groups
     */
    fun getAllGroups(guildId: Long): List<ReactionRoleGroup>

    /**
     * Updates an existing reaction role group.
     *
     * @param group the group to update
     * @return the updated group
     */
    fun updateGroup(group: ReactionRoleGroup): ReactionRoleGroup

    /**
     * Soft-deletes a reaction role group by marking it as inactive.
     *
     * @param groupId the group ID
     * @return true if deleted successfully
     */
    fun deleteGroup(groupId: Long): Boolean

    // ===== Validation Methods =====

    /**
     * Validates if a member meets the role requirements to get a specific role.
     *
     * @param member the member
     * @param item the menu item containing role requirements
     * @return true if the member meets all requirements
     */
    fun validateRoleRequirements(member: Member, item: ReactionRoleMenuItem): Boolean

    /**
     * Validates if a member can get a role based on all requirements and constraints.
     *
     * @param member the member
     * @param item the menu item
     * @param role the role to assign
     * @return validation result with success flag and optional error message
     */
    fun validateRoleAssignment(member: Member, item: ReactionRoleMenuItem, role: Role): ValidationResult

    /**
     * Checks if a member has a specific role.
     *
     * @param member the member
     * @param roleId the role ID
     * @return true if the member has the role
     */
    fun memberHasRole(member: Member, roleId: String): Boolean

    // ===== Role Assignment Methods =====

    /**
     * Assigns or removes a role based on a menu item interaction.
     * Handles toggleable roles and exclusive groups.
     *
     * @param member the member who interacted
     * @param item the menu item that was selected
     * @param channel the channel where the interaction occurred
     * @param author the moderator who triggered the action (if applicable)
     * @return role assignment result with success flag and action taken
     */
    fun handleRoleInteraction(
        member: Member,
        item: ReactionRoleMenuItem,
        channel: TextChannel?,
        author: Member?
    ): RoleInteractionResult

    /**
     * Assigns a role to a member.
     *
     * @param member the member
     * @param role the role to assign
     * @return true if the role was assigned successfully
     */
    fun assignRole(member: Member, role: Role): Boolean

    /**
     * Removes a role from a member.
     *
     * @param member the member
     * @param role the role to remove
     * @return true if the role was removed successfully
     */
    fun removeRole(member: Member, role: Role): Boolean

    /**
     * Removes all roles in a group from a member (for exclusive groups).
     *
     * @param member the member
     * @param groupId the group ID
     * @return number of roles removed
     */
    fun removeGroupRoles(member: Member, groupId: Long): Int

    // ===== Utility Methods =====

    /**
     * Checks if a reaction role menu exists for a specific message.
     *
     * @param channelId the channel ID
     * @param messageId the message ID
     * @return true if a menu exists
     */
    fun menuExistsForMessage(channelId: String, messageId: String): Boolean

    /**
     * Counts the number of active menus for a guild.
     *
     * @param guildId the guild ID
     * @return the count of active menus
     */
    fun countActiveMenus(guildId: Long): Int

    /**
     * Gets a Discord role from a guild by role ID.
     *
     * @param guild the guild
     * @param roleId the role ID
     * @return the role or null if not found
     */
    fun getRole(guild: Guild, roleId: String): Role?

    // ===== Result Classes =====

    /**
     * Validation result for role assignment validation.
     */
    data class ValidationResult(
        val success: Boolean,
        val errorMessage: String? = null
    )

    /**
     * Result of a role interaction (reaction or button click).
     */
    data class RoleInteractionResult(
        val success: Boolean,
        val action: Action,
        val errorMessage: String? = null
    ) {
        enum class Action {
            ROLE_ADDED,
            ROLE_REMOVED,
            ROLE_ALREADY_EXISTS,
            VALIDATION_FAILED,
            ERROR
        }
    }
}
