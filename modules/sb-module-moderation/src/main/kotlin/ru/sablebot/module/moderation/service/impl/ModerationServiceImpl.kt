package ru.sablebot.module.moderation.service.impl

import dev.minn.jda.ktx.coroutines.await
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.quartz.SimpleScheduleBuilder
import org.quartz.TriggerBuilder
import org.springframework.scheduling.quartz.SchedulerFactoryBean
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.sablebot.common.model.AuditActionType
import ru.sablebot.common.model.ModerationActionType
import ru.sablebot.common.model.ModerationCaseType
import ru.sablebot.common.persistence.entity.ModerationCase
import ru.sablebot.common.persistence.repository.ModerationCaseRepository
import ru.sablebot.common.service.ModerationConfigService
import ru.sablebot.common.worker.modules.audit.service.AuditService
import ru.sablebot.common.worker.modules.moderation.model.ModerationActionRequest
import ru.sablebot.common.worker.modules.moderation.service.MuteService
import ru.sablebot.module.moderation.job.UnBanJob
import ru.sablebot.common.utils.DurationParser
import ru.sablebot.module.moderation.service.IModerationService
import java.awt.Color
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit

@Service
open class ModerationServiceImpl(
    private val caseRepository: ModerationCaseRepository,
    private val configService: ModerationConfigService,
    private val auditService: AuditService,
    private val muteService: MuteService,
    private val schedulerFactoryBean: SchedulerFactoryBean,
    private val meterRegistry: MeterRegistry
) : IModerationService {

    companion object {
        private val log = KotlinLogging.logger { }

        private val COLOR_BAN = Color.decode("#FF686B")
        private val COLOR_UNBAN = Color.decode("#85EA8A")
        private val COLOR_KICK = Color.decode("#FFA154")
        private val COLOR_WARN = Color.decode("#FFCA59")
        private val COLOR_TIMEOUT = Color.decode("#FFCA59")
    }

    private fun recordAction(type: String) {
        meterRegistry.counter("sablebot.moderation.actions", "type", type).increment()
    }

    /**
     * Template method that encapsulates the common moderation action pipeline:
     * 1. Record metrics
     * 2. Execute pre-action hooks (e.g., DM target)
     * 3. Execute Discord API action
     * 4. Create moderation case
     * 5. Execute post-action hooks (e.g., scheduling)
     * 6. Log to audit service
     * 7. Send modlog embed
     * 8. Execute final hooks (e.g., escalation check)
     * 9. Return case
     */
    private suspend fun executeModerationAction(
        guild: Guild,
        moderator: Member,
        target: User,
        caseType: ModerationCaseType,
        auditActionType: AuditActionType,
        reason: String?,
        duration: Long? = null,
        metricType: String,
        preAction: (suspend () -> Unit)? = null,
        discordAction: suspend () -> Unit,
        postAction: (suspend (ModerationCase) -> Unit)? = null,
        finalHook: (suspend (ModerationCase) -> Unit)? = null
    ): ModerationCase {
        // Record metrics
        recordAction(metricType)

        // Pre-action hooks (e.g., DM target)
        preAction?.invoke()

        // Execute Discord action
        discordAction()

        // Create case
        val case = createCase(
            guildId = guild.idLong,
            actionType = caseType,
            moderator = moderator,
            target = target,
            reason = reason,
            duration = duration
        )

        // Post-action hooks (e.g., scheduling)
        postAction?.invoke(case)

        // Audit log
        val auditBuilder = auditService.log(guild, auditActionType)
            .withUser(moderator)
            .withTargetUser(target)
            .withAttribute("reason", reason)

        if (duration != null) {
            auditBuilder.withAttribute("duration", duration)
        }

        auditBuilder.save()

        // Send modlog embed
        sendModlogEmbed(guild, case)

        // Final hooks (e.g., escalation check)
        finalHook?.invoke(case)

        return case
    }

    override suspend fun ban(
        guild: Guild,
        target: Member,
        moderator: Member,
        reason: String?,
        duration: Long?,
        deleteDays: Int?
    ): ModerationCase {
        return executeModerationAction(
            guild = guild,
            moderator = moderator,
            target = target.user,
            caseType = ModerationCaseType.BAN,
            auditActionType = AuditActionType.MEMBER_BAN,
            reason = reason,
            duration = duration,
            metricType = "ban",
            preAction = {
                dmTarget(target.user, guild, "banned", reason, duration)
            },
            discordAction = {
                guild.ban(target, deleteDays ?: 0, TimeUnit.DAYS)
                    .reason(reason)
                    .await()
            },
            postAction = { case ->
                if (duration != null) {
                    scheduleUnBan(guild.id, target.user.id, duration)
                }
            }
        )
    }

    override suspend fun unban(
        guild: Guild,
        targetUser: User,
        moderator: Member,
        reason: String?
    ): ModerationCase {
        return executeModerationAction(
            guild = guild,
            moderator = moderator,
            target = targetUser,
            caseType = ModerationCaseType.UNBAN,
            auditActionType = AuditActionType.MEMBER_UNBAN,
            reason = reason,
            metricType = "unban",
            discordAction = {
                guild.unban(targetUser)
                    .reason(reason)
                    .await()
            },
            postAction = {
                removeUnBanSchedule(guild.id, targetUser.id)
            }
        )
    }

    override suspend fun kick(
        guild: Guild,
        target: Member,
        moderator: Member,
        reason: String?
    ): ModerationCase {
        return executeModerationAction(
            guild = guild,
            moderator = moderator,
            target = target.user,
            caseType = ModerationCaseType.KICK,
            auditActionType = AuditActionType.MEMBER_KICK,
            reason = reason,
            metricType = "kick",
            preAction = {
                dmTarget(target.user, guild, "kicked", reason, null)
            },
            discordAction = {
                guild.kick(target)
                    .reason(reason)
                    .await()
            }
        )
    }

    override suspend fun warn(
        guild: Guild,
        target: Member,
        moderator: Member,
        reason: String
    ): ModerationCase {
        recordAction("warn")
        dmTarget(target.user, guild, "warned", reason, null)

        val case = createCase(
            guildId = guild.idLong,
            actionType = ModerationCaseType.WARN,
            moderator = moderator,
            target = target.user,
            reason = reason
        )

        auditService.log(guild, AuditActionType.MEMBER_WARN)
            .withUser(moderator)
            .withTargetUser(target)
            .withAttribute("reason", reason)
            .save()

        sendModlogEmbed(guild, case)

        checkEscalation(guild, target, moderator)

        return case
    }

    override suspend fun timeout(
        guild: Guild,
        target: Member,
        moderator: Member,
        duration: Long,
        reason: String?
    ): ModerationCase {
        recordAction("timeout")
        dmTarget(target.user, guild, "timed out", reason, duration)

        target.timeoutFor(Duration.ofMillis(duration))
            .reason(reason)
            .await()

        val case = createCase(
            guildId = guild.idLong,
            actionType = ModerationCaseType.TIMEOUT,
            moderator = moderator,
            target = target.user,
            reason = reason,
            duration = duration
        )

        auditService.log(guild, AuditActionType.MEMBER_MUTE)
            .withUser(moderator)
            .withTargetUser(target)
            .withAttribute("reason", reason)
            .withAttribute("duration", duration)
            .save()

        sendModlogEmbed(guild, case)

        return case
    }

    override suspend fun removeTimeout(
        guild: Guild,
        target: Member,
        moderator: Member,
        reason: String?
    ): ModerationCase {
        recordAction("untimeout")
        target.removeTimeout()
            .reason(reason)
            .await()

        val case = createCase(
            guildId = guild.idLong,
            actionType = ModerationCaseType.UNTIMEOUT,
            moderator = moderator,
            target = target.user,
            reason = reason
        )

        auditService.log(guild, AuditActionType.MEMBER_UNMUTE)
            .withUser(moderator)
            .withTargetUser(target)
            .withAttribute("reason", reason)
            .save()

        sendModlogEmbed(guild, case)

        return case
    }

    override suspend fun purgeMessages(channel: TextChannel, count: Int, filterUser: User?): Int {
        recordAction("purge")
        val messages = channel.iterableHistory.takeAsync(count).await()

        val filtered = if (filterUser != null) {
            messages.filter { it.author.id == filterUser.id }
        } else {
            messages
        }

        if (filtered.isEmpty()) return 0

        if (filtered.size == 1) {
            filtered.first().delete().await()
        } else {
            channel.purgeMessages(filtered)
        }

        return filtered.size
    }

    override suspend fun lockChannel(channel: TextChannel, moderator: Member, reason: String?) {
        val publicRole = channel.guild.publicRole
        channel.upsertPermissionOverride(publicRole)
            .deny(Permission.MESSAGE_SEND)
            .reason(reason)
            .await()
    }

    override suspend fun unlockChannel(channel: TextChannel, moderator: Member) {
        val publicRole = channel.guild.publicRole
        val override = channel.getPermissionOverride(publicRole)
        if (override != null) {
            override.manager
                .clear(Permission.MESSAGE_SEND)
                .await()
        }
    }

    override fun getWarnings(guildId: Long, targetId: String): List<ModerationCase> {
        return caseRepository.findByGuildIdAndTargetIdAndActionTypeAndActive(
            guildId, targetId, ModerationCaseType.WARN, true
        )
    }

    @Transactional
    override fun clearWarnings(guildId: Long, targetId: String): Int {
        val warnings = caseRepository.findByGuildIdAndTargetIdAndActionTypeAndActive(
            guildId, targetId, ModerationCaseType.WARN, true
        )
        warnings.forEach { it.active = false }
        caseRepository.saveAll(warnings)
        return warnings.size
    }

    override fun getCase(guildId: Long, caseNumber: Int): ModerationCase? {
        return caseRepository.findByGuildIdAndCaseNumber(guildId, caseNumber)
    }

    override fun getModLog(guildId: Long, targetId: String): List<ModerationCase> {
        return caseRepository.findByGuildIdAndTargetIdOrderByCaseNumberDesc(guildId, targetId)
    }

    // --- Private helpers ---

    @Transactional
    open fun createCase(
        guildId: Long,
        actionType: ModerationCaseType,
        moderator: Member,
        target: User,
        reason: String?,
        duration: Long? = null
    ): ModerationCase {
        val nextCaseNumber = caseRepository.findMaxCaseNumber(guildId) + 1

        val case = ModerationCase().apply {
            this.guildId = guildId
            this.caseNumber = nextCaseNumber
            this.actionType = actionType
            this.moderatorId = moderator.user.id
            this.moderatorName = moderator.effectiveName
            this.targetId = target.id
            this.targetName = target.effectiveName
            this.reason = reason
            this.duration = duration
            this.createdAt = Instant.now()
            this.active = true
        }

        return caseRepository.save(case)
    }

    private fun scheduleUnBan(guildId: String, userId: String, duration: Long) {
        runCatching {
            removeUnBanSchedule(guildId, userId)

            val job = UnBanJob.createDetails(guildId, userId)
            val startTime = Date.from(Instant.now().plusMillis(duration))

            val trigger = TriggerBuilder.newTrigger()
                .startAt(startTime)
                .withSchedule(
                    SimpleScheduleBuilder.simpleSchedule()
                        .withMisfireHandlingInstructionFireNow()
                )
                .build()

            schedulerFactoryBean.scheduler.scheduleJob(job, trigger)
        }.onFailure { e ->
            log.error(e) { "Failed to schedule unban for user $userId in guild $guildId" }
        }
    }

    private fun removeUnBanSchedule(guildId: String, userId: String) {
        runCatching {
            val key = UnBanJob.getKey(guildId, userId)
            val scheduler = schedulerFactoryBean.scheduler
            if (scheduler.checkExists(key)) {
                scheduler.deleteJob(key)
            }
        }
    }

    private suspend fun checkEscalation(guild: Guild, target: Member, moderator: Member) {
        val config = configService.getByGuildId(guild.idLong) ?: return
        val rules = config.escalationRules
        if (rules.isEmpty()) return

        val activeWarns = caseRepository.countByGuildIdAndTargetIdAndActionTypeAndActive(
            guild.idLong, target.user.id, ModerationCaseType.WARN, true
        )

        val matchingRule = rules
            .filter { it.threshold <= activeWarns }
            .maxByOrNull { it.threshold }
            ?: return

        log.info { "Escalation triggered for ${target.user.id} in ${guild.id}: ${activeWarns} warns, action=${matchingRule.actionType}" }

        when (matchingRule.actionType) {
            ModerationActionType.MUTE -> {
                val request = ModerationActionRequest.build {
                    type = ModerationActionType.MUTE
                    violator = target
                    this.moderator = moderator
                    global = true
                    duration = matchingRule.duration
                    reason = "Auto-escalation: $activeWarns warnings reached"
                }
                muteService.mute(request)
            }

            ModerationActionType.KICK -> {
                kick(guild, target, moderator, "Auto-escalation: $activeWarns warnings reached")
            }

            ModerationActionType.BAN -> {
                ban(
                    guild, target, moderator,
                    "Auto-escalation: $activeWarns warnings reached",
                    matchingRule.duration, null
                )
            }

            else -> {
                log.warn { "Unsupported escalation action type: ${matchingRule.actionType}" }
            }
        }
    }

    private fun sendModlogEmbed(guild: Guild, case: ModerationCase) {
        val config = configService.getByGuildId(guild.idLong) ?: return
        val channelId = config.modlogChannelId ?: return
        val modlogChannel = guild.getTextChannelById(channelId) ?: return

        val color = when (case.actionType) {
            ModerationCaseType.BAN -> COLOR_BAN
            ModerationCaseType.UNBAN -> COLOR_UNBAN
            ModerationCaseType.KICK -> COLOR_KICK
            ModerationCaseType.WARN -> COLOR_WARN
            ModerationCaseType.MUTE -> COLOR_WARN
            ModerationCaseType.UNMUTE -> COLOR_UNBAN
            ModerationCaseType.TIMEOUT -> COLOR_TIMEOUT
            ModerationCaseType.UNTIMEOUT -> COLOR_UNBAN
        }

        val embed = EmbedBuilder().apply {
            setTitle("Case #${case.caseNumber} | ${case.actionType.name}")
            setColor(color)
            addField("Target", "${case.targetName} (<@${case.targetId}>)", true)
            addField("Moderator", "${case.moderatorName} (<@${case.moderatorId}>)", true)

            case.duration?.let { durationMs ->
                addField("Duration", DurationParser.format(durationMs), true)
            }

            addField("Reason", case.reason ?: "No reason provided", false)
            setTimestamp(case.createdAt)
        }.build()

        modlogChannel.sendMessageEmbeds(embed).queue(null) { error ->
            log.error(error) { "Failed to send modlog embed to channel $channelId in guild ${guild.id}" }
        }
    }

    private suspend fun dmTarget(user: User, guild: Guild, action: String, reason: String?, duration: Long?) {
        runCatching {
            val message = buildString {
                append("You have been **$action** in **${guild.name}**")
                if (duration != null) {
                    append(" for **${DurationParser.format(duration)}**")
                }
                append(".")
                if (!reason.isNullOrBlank()) {
                    append("\n**Reason:** $reason")
                }
            }
            user.openPrivateChannel().await()
                .sendMessage(message).await()
        }.onFailure {
            log.debug { "Could not DM user ${user.id} about $action action" }
        }
    }
}
