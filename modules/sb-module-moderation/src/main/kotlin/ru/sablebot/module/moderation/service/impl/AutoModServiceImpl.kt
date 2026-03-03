package ru.sablebot.module.moderation.service.impl

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.entities.Message
import org.springframework.stereotype.Service
import ru.sablebot.common.model.AutoModActionType
import ru.sablebot.common.model.LinkFilterMode
import ru.sablebot.common.model.ModerationActionType
import ru.sablebot.common.persistence.entity.AutoModConfig
import ru.sablebot.common.worker.modules.moderation.model.ModerationActionRequest
import ru.sablebot.common.worker.modules.moderation.service.MuteService
import ru.sablebot.module.moderation.model.AutoModResult
import ru.sablebot.module.moderation.service.IAutoModConfigService
import ru.sablebot.module.moderation.service.IAutoModService
import ru.sablebot.module.moderation.service.IModerationService
import java.net.URI
import java.util.concurrent.TimeUnit

@Service
class AutoModServiceImpl(
    private val autoModConfigService: IAutoModConfigService,
    private val moderationService: IModerationService,
    private val muteService: MuteService
) : IAutoModService {

    companion object {
        private val log = KotlinLogging.logger {}
        private val URL_PATTERN = Regex("https?://[\\S]+")
    }

    private val spamCache = Caffeine.newBuilder()
        .expireAfterWrite(60, TimeUnit.SECONDS)
        .build<String, MutableList<Long>>()

    override suspend fun onMessage(message: Message) {
        if (message.author.isBot || message.author.isSystem) return
        if (!message.isFromGuild) return

        val member = message.member ?: return
        val guild = message.guild
        val config = autoModConfigService.getByGuildId(guild.idLong) ?: return

        val result = when {
            config.antiSpamEnabled -> checkSpam(message, config)
            else -> null
        } ?: when {
            config.wordFilterEnabled -> checkWordFilter(message, config)
            else -> null
        } ?: when {
            config.linkFilterEnabled -> checkLinkFilter(message, config)
            else -> null
        } ?: when {
            config.mentionSpamEnabled -> checkMentionSpam(message, config)
            else -> null
        }

        if (result != null) {
            executeAction(result)
        }
    }

    private fun checkSpam(message: Message, config: AutoModConfig): AutoModResult? {
        val key = "${message.guild.idLong}:${message.author.idLong}"
        val now = System.currentTimeMillis()
        val windowMs = config.antiSpamWindowSeconds * 1000L

        val timestamps = spamCache.get(key) { mutableListOf() }!!
        synchronized(timestamps) {
            timestamps.add(now)
            timestamps.removeAll { now - it > windowMs }

            if (timestamps.size >= config.antiSpamMaxMessages) {
                timestamps.clear()
                return AutoModResult(
                    trigger = "spam",
                    action = config.antiSpamAction,
                    message = message,
                    member = message.member!!,
                    reason = "Auto-mod: spam detection (${config.antiSpamMaxMessages} messages in ${config.antiSpamWindowSeconds}s)",
                    muteDuration = config.antiSpamMuteDuration,
                    deleteMessage = true
                )
            }
        }
        return null
    }

    private fun checkWordFilter(message: Message, config: AutoModConfig): AutoModResult? {
        val content = message.contentRaw
        for (pattern in config.wordFilterPatterns) {
            val regex = runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull() ?: continue
            if (regex.containsMatchIn(content)) {
                return AutoModResult(
                    trigger = "word_filter",
                    action = config.wordFilterAction,
                    message = message,
                    member = message.member!!,
                    reason = "Auto-mod: word filter match",
                    deleteMessage = true
                )
            }
        }
        return null
    }

    private fun checkLinkFilter(message: Message, config: AutoModConfig): AutoModResult? {
        val urls = URL_PATTERN.findAll(message.contentRaw).toList()
        if (urls.isEmpty()) return null

        val domains = urls.mapNotNull { match ->
            runCatching { URI(match.value).host?.lowercase() }.getOrNull()
        }

        if (domains.isEmpty()) return null

        val configDomains = config.linkFilterDomains.map { it.lowercase() }

        val matched = when (config.linkFilterMode) {
            LinkFilterMode.BLACKLIST -> domains.any { domain ->
                configDomains.any { blocked -> domain == blocked || domain.endsWith(".$blocked") }
            }
            LinkFilterMode.WHITELIST -> domains.any { domain ->
                configDomains.none { allowed -> domain == allowed || domain.endsWith(".$allowed") }
            }
        }

        if (matched) {
            return AutoModResult(
                trigger = "link_filter",
                action = config.linkFilterAction,
                message = message,
                member = message.member!!,
                reason = "Auto-mod: link filter violation",
                deleteMessage = true
            )
        }
        return null
    }

    private fun checkMentionSpam(message: Message, config: AutoModConfig): AutoModResult? {
        val mentionCount = message.mentions.users.size
        if (mentionCount >= config.mentionSpamThreshold) {
            return AutoModResult(
                trigger = "mention_spam",
                action = config.mentionSpamAction,
                message = message,
                member = message.member!!,
                reason = "Auto-mod: mention spam ($mentionCount mentions)",
                deleteMessage = true
            )
        }
        return null
    }

    private suspend fun executeAction(result: AutoModResult) {
        val guild = result.message.guild
        val member = result.member
        val selfMember = guild.selfMember

        if (result.deleteMessage) {
            result.message.delete().queue(null) { error ->
                log.error(error) { "Failed to delete message in guild ${guild.id}" }
            }
        }

        runCatching {
            when (result.action) {
                AutoModActionType.DELETE -> {
                    // Message already deleted above if deleteMessage is true
                }
                AutoModActionType.WARN -> {
                    moderationService.warn(guild, member, selfMember, result.reason)
                }
                AutoModActionType.MUTE -> {
                    val duration = result.muteDuration ?: 300_000L
                    val request = ModerationActionRequest.build {
                        type = ModerationActionType.MUTE
                        violator = member
                        moderator = selfMember
                        global = true
                        this.duration = duration
                        reason = result.reason
                    }
                    muteService.mute(request)
                }
                AutoModActionType.KICK -> {
                    moderationService.kick(guild, member, selfMember, result.reason)
                }
                AutoModActionType.BAN -> {
                    moderationService.ban(guild, member, selfMember, result.reason, null, null)
                }
            }
        }.onFailure { error ->
            log.error(error) { "Failed to execute auto-mod action ${result.action} for ${member.user.id} in guild ${guild.id}" }
        }
    }
}
