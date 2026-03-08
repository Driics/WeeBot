package ru.sablebot.api.dto.config

import ru.sablebot.common.model.AuditActionType
import ru.sablebot.common.model.AutoModActionType
import ru.sablebot.common.model.LinkFilterMode

data class GuildConfigResponse(
    val guildId: String,
    val general: GeneralConfigDto,
    val moderation: ModerationConfigDto,
    val music: MusicConfigDto,
    val audit: AuditConfigDto
)

data class GeneralConfigDto(
    val name: String?,
    val prefix: String,
    val locale: String,
    val commandLocale: String,
    val color: String?,
    val timeZone: String?,
    val privateHelp: Boolean?
)

data class ModerationConfigDto(
    val roles: List<String>,
    val publicColors: Boolean,
    val mutedRoleId: String?,
    val cooldownIgnored: Boolean,
    val modlogChannelId: String?,
    val antiSpam: AntiSpamConfigDto,
    val antiRaid: AntiRaidConfigDto,
    val wordFilter: WordFilterConfigDto,
    val linkFilter: LinkFilterConfigDto,
    val mentionSpam: MentionSpamConfigDto
)

data class AntiSpamConfigDto(
    val enabled: Boolean,
    val maxMessages: Int,
    val windowSeconds: Int,
    val action: AutoModActionType,
    val muteDuration: Long?
)

data class AntiRaidConfigDto(
    val enabled: Boolean,
    val joinThreshold: Int,
    val windowSeconds: Int,
    val minAccountAgeDays: Int,
    val action: AutoModActionType
)

data class WordFilterConfigDto(
    val enabled: Boolean,
    val patterns: List<String>,
    val action: AutoModActionType
)

data class LinkFilterConfigDto(
    val enabled: Boolean,
    val mode: LinkFilterMode,
    val domains: List<String>,
    val action: AutoModActionType
)

data class MentionSpamConfigDto(
    val enabled: Boolean,
    val threshold: Int,
    val action: AutoModActionType
)

data class MusicConfigDto(
    val channelId: String?,
    val textChannelId: String?,
    val voiceVolume: Int,
    val queueLimit: Long?,
    val durationLimit: Long?,
    val streamsEnabled: Boolean,
    val autoPlay: String?,
    val roles: List<String>,
    val showQueue: Boolean
)

data class AuditConfigDto(
    val enabled: Boolean,
    val forwardEnabled: Boolean,
    val forwardChannelId: String?,
    val forwardActions: List<AuditActionType>
)

data class UpdateGuildConfigRequest(
    val general: UpdateGeneralConfigDto? = null,
    val moderation: ModerationConfigDto? = null,
    val music: MusicConfigDto? = null,
    val audit: AuditConfigDto? = null
)

data class UpdateGeneralConfigDto(
    val prefix: String? = null,
    val locale: String? = null,
    val commandLocale: String? = null,
    val color: String? = null,
    val timeZone: String? = null,
    val privateHelp: Boolean? = null
)

data class RoleResponse(
    val id: String,
    val name: String,
    val color: Int,
    val position: Int
)

data class ChannelResponse(
    val id: String,
    val name: String,
    val type: Int,
    val position: Int
)
