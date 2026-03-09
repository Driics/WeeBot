package ru.sablebot.api.dto.event

import ru.sablebot.api.dto.reactionrole.ReactionRoleMenuItemResponse
import ru.sablebot.common.model.ReactionRoleMenuType

data class ModerationCommandEvent(
    val type: String,
    val guildId: String,
    val targetId: String,
    val reason: String?,
    val duration: Long? = null,
    val deleteMessageDays: Int? = null,
    val moderatorId: String,
    val moderatorName: String,
    val source: String = "dashboard"
)

data class GuildModerationEvent(
    val guildId: String,
    val type: String? = null,
    val caseNumber: Int? = null,
    val targetId: String? = null,
    val moderatorId: String? = null
)

data class GuildStatsEvent(
    val guildId: String,
    val metric: String? = null,
    val value: Any? = null
)

data class GuildAudioEvent(
    val guildId: String,
    val action: String? = null,
    val trackTitle: String? = null,
    val position: Long? = null
)

data class ReactionRoleCommandEvent(
    val type: String, // POST_MENU, UPDATE_MENU
    val guildId: String,
    val menuId: Long,
    val channelId: String,
    val messageId: String? = null,
    val title: String,
    val description: String?,
    val menuType: ReactionRoleMenuType,
    val items: List<ReactionRoleMenuItemResponse>
)
