package ru.sablebot.api.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.sablebot.api.dto.config.*
import ru.sablebot.common.persistence.entity.AutoModConfig
import ru.sablebot.common.persistence.repository.AutoModConfigRepository
import ru.sablebot.common.service.AuditConfigService
import ru.sablebot.common.service.ConfigService
import ru.sablebot.common.service.ModerationConfigService
import ru.sablebot.common.service.MusicConfigService

@Service
class GuildConfigApiService(
    private val configService: ConfigService,
    private val moderationConfigService: ModerationConfigService,
    private val musicConfigService: MusicConfigService,
    private val auditConfigService: AuditConfigService,
    private val autoModConfigRepository: AutoModConfigRepository
) {
    private val logger = KotlinLogging.logger {}

    @Transactional(readOnly = true)
    fun getGuildConfig(guildId: Long): GuildConfigResponse {
        val config = configService.getOrCreate(guildId)
        val modConfig = moderationConfigService.getOrCreate(guildId)
        val musicConfig = musicConfigService.getOrCreate(guildId)
        val auditConfig = auditConfigService.getOrCreate(guildId)
        val autoModConfig = autoModConfigRepository.findByGuildId(guildId) ?: AutoModConfig(guildId)

        return GuildConfigResponse(
            guildId = guildId.toString(),
            general = GeneralConfigDto(
                name = config.name,
                prefix = config.prefix,
                locale = config.locale,
                commandLocale = config.commandLocale,
                color = config.color,
                timeZone = config.timeZone,
                privateHelp = config.privateHelp
            ),
            moderation = ModerationConfigDto(
                roles = modConfig.roles.map { it.toString() },
                publicColors = modConfig.publicColors,
                mutedRoleId = modConfig.mutedRoleId?.toString(),
                cooldownIgnored = modConfig.cooldownIgnored,
                modlogChannelId = modConfig.modlogChannelId?.toString(),
                antiSpam = AntiSpamConfigDto(
                    enabled = autoModConfig.antiSpamEnabled,
                    maxMessages = autoModConfig.antiSpamMaxMessages,
                    windowSeconds = autoModConfig.antiSpamWindowSeconds,
                    action = autoModConfig.antiSpamAction,
                    muteDuration = autoModConfig.antiSpamMuteDuration
                ),
                antiRaid = AntiRaidConfigDto(
                    enabled = autoModConfig.antiRaidEnabled,
                    joinThreshold = autoModConfig.antiRaidJoinThreshold,
                    windowSeconds = autoModConfig.antiRaidWindowSeconds,
                    minAccountAgeDays = autoModConfig.antiRaidMinAccountAgeDays,
                    action = autoModConfig.antiRaidAction
                ),
                wordFilter = WordFilterConfigDto(
                    enabled = autoModConfig.wordFilterEnabled,
                    patterns = autoModConfig.wordFilterPatterns,
                    action = autoModConfig.wordFilterAction
                ),
                linkFilter = LinkFilterConfigDto(
                    enabled = autoModConfig.linkFilterEnabled,
                    mode = autoModConfig.linkFilterMode,
                    domains = autoModConfig.linkFilterDomains,
                    action = autoModConfig.linkFilterAction
                ),
                mentionSpam = MentionSpamConfigDto(
                    enabled = autoModConfig.mentionSpamEnabled,
                    threshold = autoModConfig.mentionSpamThreshold,
                    action = autoModConfig.mentionSpamAction
                )
            ),
            music = MusicConfigDto(
                channelId = musicConfig.channelId?.toString(),
                textChannelId = musicConfig.textChannelId?.toString(),
                voiceVolume = musicConfig.voiceVolume,
                queueLimit = musicConfig.queueLimit,
                durationLimit = musicConfig.durationLimit,
                streamsEnabled = musicConfig.streamsEnabled,
                autoPlay = musicConfig.autoPlay,
                roles = musicConfig.roles?.map { it.toString() } ?: emptyList(),
                showQueue = musicConfig.showQueue
            ),
            audit = AuditConfigDto(
                enabled = auditConfig.enabled,
                forwardEnabled = auditConfig.forwardEnabled,
                forwardChannelId = if (auditConfig.forwardChannelId != 0L) auditConfig.forwardChannelId.toString() else null,
                forwardActions = auditConfig.forwardActions
            )
        )
    }

    @Transactional
    fun updateGuildConfig(guildId: Long, request: UpdateGuildConfigRequest) {
        request.general?.let { general ->
            val config = configService.getOrCreate(guildId)
            general.prefix?.let { config.prefix = it }
            general.locale?.let { config.locale = it }
            general.commandLocale?.let { config.commandLocale = it }
            general.color?.let { config.color = it }
            general.timeZone?.let { config.timeZone = it }
            general.privateHelp?.let { config.privateHelp = it }
            configService.save(config)
        }

        request.moderation?.let { mod ->
            val modConfig = moderationConfigService.getOrCreate(guildId)
            modConfig.roles = mod.roles.map { it.toLong() }
            modConfig.publicColors = mod.publicColors
            modConfig.mutedRoleId = mod.mutedRoleId?.toLongOrNull()
            modConfig.cooldownIgnored = mod.cooldownIgnored
            modConfig.modlogChannelId = mod.modlogChannelId?.toLongOrNull()
            moderationConfigService.save(modConfig)

            val autoModConfig = autoModConfigRepository.findByGuildId(guildId) ?: AutoModConfig(guildId)
            autoModConfig.antiSpamEnabled = mod.antiSpam.enabled
            autoModConfig.antiSpamMaxMessages = mod.antiSpam.maxMessages
            autoModConfig.antiSpamWindowSeconds = mod.antiSpam.windowSeconds
            autoModConfig.antiSpamAction = mod.antiSpam.action
            autoModConfig.antiSpamMuteDuration = mod.antiSpam.muteDuration
            autoModConfig.antiRaidEnabled = mod.antiRaid.enabled
            autoModConfig.antiRaidJoinThreshold = mod.antiRaid.joinThreshold
            autoModConfig.antiRaidWindowSeconds = mod.antiRaid.windowSeconds
            autoModConfig.antiRaidMinAccountAgeDays = mod.antiRaid.minAccountAgeDays
            autoModConfig.antiRaidAction = mod.antiRaid.action
            autoModConfig.wordFilterEnabled = mod.wordFilter.enabled
            autoModConfig.wordFilterPatterns = mod.wordFilter.patterns.toMutableList()
            autoModConfig.wordFilterAction = mod.wordFilter.action
            autoModConfig.linkFilterEnabled = mod.linkFilter.enabled
            autoModConfig.linkFilterMode = mod.linkFilter.mode
            autoModConfig.linkFilterDomains = mod.linkFilter.domains.toMutableList()
            autoModConfig.linkFilterAction = mod.linkFilter.action
            autoModConfig.mentionSpamEnabled = mod.mentionSpam.enabled
            autoModConfig.mentionSpamThreshold = mod.mentionSpam.threshold
            autoModConfig.mentionSpamAction = mod.mentionSpam.action
            autoModConfigRepository.save(autoModConfig)
        }

        request.music?.let { music ->
            val musicConfig = musicConfigService.getOrCreate(guildId)
            musicConfig.channelId = music.channelId?.toLongOrNull()
            musicConfig.textChannelId = music.textChannelId?.toLongOrNull()
            musicConfig.voiceVolume = music.voiceVolume
            musicConfig.queueLimit = music.queueLimit
            musicConfig.durationLimit = music.durationLimit
            musicConfig.streamsEnabled = music.streamsEnabled
            musicConfig.autoPlay = music.autoPlay
            musicConfig.roles = music.roles.map { it.toLong() }
            musicConfig.showQueue = music.showQueue
            musicConfigService.save(musicConfig)
        }

        request.audit?.let { audit ->
            val auditConfig = auditConfigService.getOrCreate(guildId)
            auditConfig.enabled = audit.enabled
            auditConfig.forwardEnabled = audit.forwardEnabled
            auditConfig.forwardChannelId = audit.forwardChannelId?.toLongOrNull() ?: 0L
            auditConfig.forwardActions = audit.forwardActions
            auditConfigService.save(auditConfig)
        }

        logger.info { "Updated config for guild $guildId" }
    }
}
