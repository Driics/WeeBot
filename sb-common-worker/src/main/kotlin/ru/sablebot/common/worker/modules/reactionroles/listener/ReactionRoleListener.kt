package ru.sablebot.common.worker.modules.reactionroles.listener

import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.sablebot.common.support.CoroutineLauncher
import ru.sablebot.common.worker.event.listeners.DiscordEventListener
import ru.sablebot.common.worker.modules.reactionroles.service.ReactionRoleService

/**
 * Listener for reaction role events.
 * Handles both emoji reactions and button interactions for self-assignable roles.
 */
@Component
class ReactionRoleListener @Autowired constructor(
    private val reactionRoleService: ReactionRoleService,
    private val coroutineLauncher: CoroutineLauncher
) : DiscordEventListener() {

    companion object {
        private val log = KotlinLogging.logger {}
        private const val REACTION_ROLE_BUTTON_PREFIX = "reaction_role:"
    }

    /**
     * Handles reaction add events for reaction role menus.
     * When a user adds a reaction to a reaction role message, assign them the corresponding role.
     */
    override fun onMessageReactionAdd(event: MessageReactionAddEvent) {
        // Ignore bot reactions
        if (event.user?.isBot == true) {
            return
        }

        coroutineLauncher.launchMessageJob(event) {
            try {
                val channelId = event.channel.id
                val messageId = event.messageId
                val emoji = event.emoji.asReactionCode

                log.debug { "Reaction added: channel=$channelId, message=$messageId, emoji=$emoji, user=${event.userId}" }

                // Check if this message has a reaction role menu
                val menu = reactionRoleService.getMenuByMessage(channelId, messageId) ?: return@launchMessageJob

                if (!menu.active) {
                    log.debug { "Menu ${menu.id} is inactive, ignoring reaction" }
                    return@launchMessageJob
                }

                // Find the menu item matching this emoji
                val menuItems = reactionRoleService.getActiveMenuItems(menu.id!!)
                val menuItem = menuItems.find { it.emoji == emoji }

                if (menuItem == null) {
                    log.debug { "No menu item found for emoji $emoji in menu ${menu.id}" }
                    return@launchMessageJob
                }

                // Get the member who reacted
                val guild = event.guild
                val member = guild.getMemberById(event.userId)

                if (member == null) {
                    log.warn { "Could not find member ${event.userId} in guild ${guild.id}" }
                    return@launchMessageJob
                }

                // Get the channel as TextChannel for audit logging
                val channel = event.channel as? TextChannel

                // Handle the role interaction
                val result = reactionRoleService.handleRoleInteraction(
                    member = member,
                    item = menuItem,
                    channel = channel,
                    author = null  // Self-service, no moderator involved
                )

                when (result.action) {
                    ReactionRoleService.RoleInteractionResult.Action.ROLE_ADDED -> {
                        log.info { "Assigned role ${menuItem.roleId} to member ${member.id} via reaction in guild ${guild.id}" }
                    }
                    ReactionRoleService.RoleInteractionResult.Action.ROLE_REMOVED -> {
                        log.info { "Removed role ${menuItem.roleId} from member ${member.id} via reaction in guild ${guild.id}" }
                    }
                    ReactionRoleService.RoleInteractionResult.Action.ROLE_ALREADY_EXISTS -> {
                        log.debug { "Member ${member.id} already has role ${menuItem.roleId}" }
                        // Optionally remove the reaction if the role is not toggleable
                        if (!menuItem.toggleable) {
                            event.reaction.removeReaction(event.user!!).queue(
                                { log.debug { "Removed duplicate reaction from member ${member.id}" } },
                                { error -> log.warn(error) { "Failed to remove reaction from member ${member.id}" } }
                            )
                        }
                    }
                    ReactionRoleService.RoleInteractionResult.Action.VALIDATION_FAILED -> {
                        log.warn { "Role validation failed for member ${member.id}: ${result.errorMessage}" }
                        // Remove the reaction since they can't get this role
                        event.reaction.removeReaction(event.user!!).queue(
                            { log.debug { "Removed reaction from member ${member.id} due to validation failure" } },
                            { error -> log.warn(error) { "Failed to remove reaction from member ${member.id}" } }
                        )
                    }
                    ReactionRoleService.RoleInteractionResult.Action.ERROR -> {
                        log.error { "Error handling role interaction for member ${member.id}: ${result.errorMessage}" }
                    }
                }

            } catch (e: Exception) {
                log.error(e) { "Error handling reaction add event" }
            }
        }
    }

    /**
     * Handles reaction remove events for toggleable reaction roles.
     * When a user removes their reaction, remove the corresponding role if toggleable.
     */
    override fun onMessageReactionRemove(event: MessageReactionRemoveEvent) {
        // Ignore bot reactions
        if (event.user?.isBot == true) {
            return
        }

        coroutineLauncher.launchMessageJob(event) {
            try {
                val channelId = event.channel.id
                val messageId = event.messageId
                val emoji = event.emoji.asReactionCode

                log.debug { "Reaction removed: channel=$channelId, message=$messageId, emoji=$emoji, user=${event.userId}" }

                // Check if this message has a reaction role menu
                val menu = reactionRoleService.getMenuByMessage(channelId, messageId) ?: return@launchMessageJob

                if (!menu.active) {
                    return@launchMessageJob
                }

                // Find the menu item matching this emoji
                val menuItems = reactionRoleService.getActiveMenuItems(menu.id!!)
                val menuItem = menuItems.find { it.emoji == emoji }

                if (menuItem == null) {
                    return@launchMessageJob
                }

                // Only remove role if the menu item is toggleable
                if (!menuItem.toggleable) {
                    log.debug { "Menu item ${menuItem.id} is not toggleable, ignoring reaction removal" }
                    return@launchMessageJob
                }

                // Get the member who removed the reaction
                val guild = event.guild
                val member = guild.getMemberById(event.userId)

                if (member == null) {
                    log.warn { "Could not find member ${event.userId} in guild ${guild.id}" }
                    return@launchMessageJob
                }

                // Get the role
                val role = reactionRoleService.getRole(guild, menuItem.roleId)

                if (role == null) {
                    log.warn { "Role ${menuItem.roleId} not found in guild ${guild.id}" }
                    return@launchMessageJob
                }

                // Check if member has the role before trying to remove it
                if (!reactionRoleService.memberHasRole(member, menuItem.roleId)) {
                    log.debug { "Member ${member.id} does not have role ${menuItem.roleId}, nothing to remove" }
                    return@launchMessageJob
                }

                // Remove the role
                val removed = reactionRoleService.removeRole(member, role)

                if (removed) {
                    log.info { "Removed role ${menuItem.roleId} from member ${member.id} via reaction removal in guild ${guild.id}" }
                } else {
                    log.warn { "Failed to remove role ${menuItem.roleId} from member ${member.id}" }
                }

            } catch (e: Exception) {
                log.error(e) { "Error handling reaction remove event" }
            }
        }
    }

    /**
     * Handles button interaction events for reaction role menus.
     * When a user clicks a button on a reaction role message, assign them the corresponding role.
     */
    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val componentId = event.componentId

        // Only handle reaction role buttons
        if (!componentId.startsWith(REACTION_ROLE_BUTTON_PREFIX)) {
            return
        }

        coroutineLauncher.launchMessageJob(event) {
            try {
                val channelId = event.channel.id
                val messageId = event.messageId
                val menuItemIdStr = componentId.removePrefix(REACTION_ROLE_BUTTON_PREFIX)
                val menuItemId = menuItemIdStr.toLongOrNull()

                if (menuItemId == null) {
                    log.warn { "Invalid menu item ID in button component: $componentId" }
                    event.reply("Invalid button configuration.")
                        .setEphemeral(true)
                        .queue()
                    return@launchMessageJob
                }

                log.debug { "Button clicked: channel=$channelId, message=$messageId, menuItemId=$menuItemId, user=${event.user.id}" }

                // Get the menu item
                val menuItem = reactionRoleService.getMenuItem(menuItemId)

                if (menuItem == null || !menuItem.active) {
                    log.warn { "Menu item $menuItemId not found or inactive" }
                    event.reply("This role menu is no longer active.")
                        .setEphemeral(true)
                        .queue()
                    return@launchMessageJob
                }

                // Verify the menu is still active
                val menu = reactionRoleService.getMenu(menuItem.menuId)

                if (menu == null || !menu.active) {
                    log.warn { "Menu ${menuItem.menuId} not found or inactive" }
                    event.reply("This role menu is no longer active.")
                        .setEphemeral(true)
                        .queue()
                    return@launchMessageJob
                }

                // Get the guild and member
                val guild = event.guild ?: run {
                    log.warn { "Button interaction outside of guild context" }
                    event.reply("This feature is only available in servers.")
                        .setEphemeral(true)
                        .queue()
                    return@launchMessageJob
                }

                val member = event.member ?: run {
                    log.warn { "Could not get member from button interaction" }
                    event.reply("Could not identify you as a server member.")
                        .setEphemeral(true)
                        .queue()
                    return@launchMessageJob
                }

                // Get the channel as TextChannel for audit logging
                val channel = event.channel as? TextChannel

                // Handle the role interaction
                val result = reactionRoleService.handleRoleInteraction(
                    member = member,
                    item = menuItem,
                    channel = channel,
                    author = null  // Self-service, no moderator involved
                )

                // Send ephemeral response to the user
                val responseMessage = when (result.action) {
                    ReactionRoleService.RoleInteractionResult.Action.ROLE_ADDED -> {
                        log.info { "Assigned role ${menuItem.roleId} to member ${member.id} via button in guild ${guild.id}" }
                        "✅ Role **${menuItem.label}** has been added to you!"
                    }
                    ReactionRoleService.RoleInteractionResult.Action.ROLE_REMOVED -> {
                        log.info { "Removed role ${menuItem.roleId} from member ${member.id} via button in guild ${guild.id}" }
                        "✅ Role **${menuItem.label}** has been removed from you!"
                    }
                    ReactionRoleService.RoleInteractionResult.Action.ROLE_ALREADY_EXISTS -> {
                        log.debug { "Member ${member.id} already has role ${menuItem.roleId}" }
                        "ℹ️ You already have the role **${menuItem.label}**."
                    }
                    ReactionRoleService.RoleInteractionResult.Action.VALIDATION_FAILED -> {
                        log.warn { "Role validation failed for member ${member.id}: ${result.errorMessage}" }
                        "❌ ${result.errorMessage ?: "You cannot get this role."}"
                    }
                    ReactionRoleService.RoleInteractionResult.Action.ERROR -> {
                        log.error { "Error handling role interaction for member ${member.id}: ${result.errorMessage}" }
                        "❌ An error occurred while assigning the role. Please try again later."
                    }
                }

                event.reply(responseMessage)
                    .setEphemeral(true)
                    .queue()

            } catch (e: Exception) {
                log.error(e) { "Error handling button interaction event" }
                event.reply("❌ An unexpected error occurred.")
                    .setEphemeral(true)
                    .queue()
            }
        }
    }
}
