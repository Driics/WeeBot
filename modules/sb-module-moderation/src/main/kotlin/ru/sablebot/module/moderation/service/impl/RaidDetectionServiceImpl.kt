package ru.sablebot.module.moderation.service.impl

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import org.springframework.stereotype.Service
import ru.sablebot.common.model.AutoModActionType
import ru.sablebot.module.moderation.service.IAutoModConfigService
import ru.sablebot.module.moderation.service.IModerationService
import ru.sablebot.module.moderation.service.IRaidDetectionService
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

@Service
class RaidDetectionServiceImpl(
    private val autoModConfigService: IAutoModConfigService,
    private val moderationService: IModerationService
) : IRaidDetectionService {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    private val joinCache = Caffeine.newBuilder()
        .expireAfterWrite(60, TimeUnit.SECONDS)
        .build<Long, MutableList<Long>>()

    override suspend fun onMemberJoin(member: Member) {
        val guild = member.guild
        val config = autoModConfigService.getByGuildId(guild.idLong) ?: return

        if (!config.antiRaidEnabled) return

        val selfMember = guild.selfMember

        // Check account age
        val accountAge = ChronoUnit.DAYS.between(member.timeCreated, OffsetDateTime.now())
        if (accountAge < config.antiRaidMinAccountAgeDays) {
            log.info { "Raid detection: new account ${member.user.id} (${accountAge}d old) in guild ${guild.id}" }
            runCatching {
                executeRaidAction(config.antiRaidAction, guild, member, selfMember,
                    "Auto-mod: account too new (${accountAge}d < ${config.antiRaidMinAccountAgeDays}d)")
            }.onFailure { error ->
                log.error(error) { "Failed to execute raid action on new account ${member.user.id} in guild ${guild.id}" }
            }
            return
        }

        // Check join rate — determine if threshold exceeded without holding lock during suspend call
        val raidDetected = checkJoinRate(guild.idLong, config.antiRaidWindowSeconds, config.antiRaidJoinThreshold)

        if (raidDetected) {
            log.warn { "Raid detected in guild ${guild.id}: join surge within ${config.antiRaidWindowSeconds}s" }
            runCatching {
                executeRaidAction(config.antiRaidAction, guild, member, selfMember,
                    "Auto-mod: raid detection (join surge)")
            }.onFailure { error ->
                log.error(error) { "Failed to execute raid action for ${member.user.id} in guild ${guild.id}" }
            }
        }
    }

    private fun checkJoinRate(guildId: Long, windowSeconds: Int, threshold: Int): Boolean {
        val now = System.currentTimeMillis()
        val windowMs = windowSeconds * 1000L

        val timestamps = joinCache.get(guildId) { mutableListOf() }!!
        synchronized(timestamps) {
            timestamps.add(now)
            timestamps.removeAll { now - it > windowMs }

            if (timestamps.size >= threshold) {
                timestamps.clear()
                return true
            }
        }
        return false
    }

    private suspend fun executeRaidAction(
        action: AutoModActionType,
        guild: Guild,
        target: Member,
        selfMember: Member,
        reason: String
    ) {
        when (action) {
            AutoModActionType.KICK -> {
                moderationService.kick(guild, target, selfMember, reason)
            }
            AutoModActionType.BAN -> {
                moderationService.ban(guild, target, selfMember, reason, null, null)
            }
            else -> {
                log.warn { "Unsupported raid action type: $action for guild ${guild.id}" }
            }
        }
    }
}
