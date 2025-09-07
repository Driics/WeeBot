package ru.driics.sablebot.common.worker.modules.moderation.service

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.PermissionOverride
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import org.quartz.JobKey
import org.quartz.Scheduler
import org.quartz.SimpleScheduleBuilder
import org.quartz.TriggerBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.quartz.SchedulerFactoryBean
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.driics.sablebot.common.model.AuditActionType
import ru.driics.sablebot.common.model.ModerationActionType
import ru.driics.sablebot.common.persistence.entity.ModerationConfig
import ru.driics.sablebot.common.persistence.entity.MuteState
import ru.driics.sablebot.common.persistence.repository.MuteStateRepository
import ru.driics.sablebot.common.service.ModerationConfigService
import ru.driics.sablebot.common.service.TransactionHandler
import ru.driics.sablebot.common.worker.jobs.UnMuteJob
import ru.driics.sablebot.common.worker.modules.audit.model.AuditActionBuilder
import ru.driics.sablebot.common.worker.modules.audit.provider.ModerationAuditForwardProvider.Companion.DURATION_MS_ATTR
import ru.driics.sablebot.common.worker.modules.audit.provider.ModerationAuditForwardProvider.Companion.GLOBAL_ATTR
import ru.driics.sablebot.common.worker.modules.audit.provider.ModerationAuditForwardProvider.Companion.REASON_ATTR
import ru.driics.sablebot.common.worker.modules.audit.service.AuditService
import ru.driics.sablebot.common.worker.modules.moderation.model.ModerationActionRequest
import java.awt.Color
import java.time.Instant
import java.time.ZoneId
import java.util.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant

@Service
open class MuteServiceImpl @Autowired constructor(
    private val muteStateRepository: MuteStateRepository,
    private val schedulerFactoryBean: SchedulerFactoryBean,
    private val auditService: AuditService,
    private val configService: ModerationConfigService,
    private val transactionHandler: TransactionHandler
) : MuteService {
    companion object {
        const val MUTED_ROLE_NAME = "silence"
        const val PERMANENT_MUTE_YEARS = 150L
    }

    internal enum class PermissionMode {
        DENY, ALLOW, UNCHECKED
    }

    @Transactional
    override fun getMutedRole(guild: Guild): Role? = getMutedRole(guild, true)

    private fun getMutedRole(guild: Guild, updateable: Boolean): Role? {
        val moderationConfig = if (updateable) {
            configService.getOrCreate(guild.idLong)
        } else {
            configService.get(guild)
        }

        var role = moderationConfig?.mutedRoleId?.let(guild::getRoleById)
            ?: guild.getRolesByName(MUTED_ROLE_NAME, true).firstOrNull()

        if (updateable) {
            role = ensureValidMutedRole(guild, role, moderationConfig)
            setupChannelPermissions(guild, role)
        }

        return role
    }

    @Transactional
    override fun mute(request: ModerationActionRequest): Boolean {
        val actionBuilder = createAuditActionBuilderIfNeeded(request)
        val scheduleCallback = createScheduleCallback(request, actionBuilder)

        return when {
            request.global -> handleGlobalMute(request, scheduleCallback)
            else -> handleChannelMute(request, scheduleCallback)
        }
    }

    @Transactional
    override fun unmute(author: Member?, channel: TextChannel?, member: Member): Boolean {
        val guild = member.guild
        var wasUnmuted = false

        val mutedRole = getMutedRole(guild) ?: return false
        if (mutedRole in member.roles) {
            guild.removeRoleFromMember(member, mutedRole).queue()
            wasUnmuted = true
        }

        channel?.getPermissionOverride(member)?.let { overridden ->
            overridden.delete().queue()
            wasUnmuted = true
        }

        removeUnMuteSchedule(member, channel)

        if (wasUnmuted) {
            auditService
                .log(guild, AuditActionType.MEMBER_UNMUTE)
                .withUser(author)
                .withTargetUser(member)
                .withChannel(channel)
                .save()
        }

        return wasUnmuted
    }

    @Async
    @Transactional
    override fun refreshMute(member: Member) {
        val scheduler = schedulerFactoryBean.scheduler
        val muteStates = muteStateRepository.findAllByGuildIdAndUserId(
            member.guild.idLong,
            member.user.id
        )

        when {
            muteStates.isNotEmpty() -> {
                muteStates.filterNot { processState(member, it) }
                    .forEach(muteStateRepository::delete)
            }

            else -> handleJobBasedMuteRefresh(scheduler, member)
        }
    }

    @OptIn(ExperimentalTime::class)
    @Transactional
    override fun isMuted(member: Member, channel: TextChannel): Boolean {
        val mutedRole = getMutedRole(member.guild, updateable = false)
        if (mutedRole != null && mutedRole in member.roles) {
            return true
        }

        val now = Clock.System.now().toJavaInstant()
        return muteStateRepository
            .findAllByGuildIdAndUserId(member.guild.idLong, member.user.id)
            .any { state ->
                val expire = state.expire.toJavaInstant()
                val isNotExpired = now.isBefore(expire)
                val isRelevantChannel = state.isGlobal || state.channelId == channel.id

                isNotExpired && isRelevantChannel
            }
    }

    @Transactional
    override fun clearState(guildId: Long, userId: String, channelId: String?) {
        when (channelId) {
            null -> muteStateRepository.deleteByGuildIdAndUserId(guildId, userId)
            else -> muteStateRepository.deleteByGuildIdAndUserIdAndChannelId(guildId, userId, channelId)
        }
    }

    // [Helper region]

    private fun ensureValidMutedRole(
        guild: Guild,
        existingRole: Role?,
        moderationConfig: ModerationConfig?
    ): Role {
        val role = when {
            existingRole !== null && guild.selfMember.canInteract(existingRole) -> existingRole
            else -> createMutedRole(guild)
        }

        moderationConfig?.takeIf { it.mutedRoleId != role.idLong }?.let { config ->
            config.mutedRoleId = role.idLong
            configService.save(config)
        }

        return role
    }

    private fun createMutedRole(guild: Guild): Role =
        guild.createRole()
            .setColor(Color.GRAY)
            .setMentionable(false)
            .setName(MUTED_ROLE_NAME)
            .complete()

    private fun setupChannelPermissions(guild: Guild, role: Role) {
        val channelPermissions = listOf(
            guild.categories to Permission.MESSAGE_SEND,
            guild.textChannels to Permission.MESSAGE_SEND,
            guild.voiceChannels to Permission.VOICE_SPEAK
        )

        channelPermissions.forEach { (channels, permission) ->
            channels.forEach { channel ->
                checkPermission(channel, role, PermissionMode.DENY, permission)
            }
        }
    }

    private fun createAuditActionBuilderIfNeeded(request: ModerationActionRequest): AuditActionBuilder? =
        if (request.auditLogging) {
            auditService.log(request.guild, AuditActionType.MEMBER_MUTE)
                .withUser(request.moderator)
                .withTargetUser(request.violator)
                .withChannel(request.channel.takeUnless { request.global })
                .withAttribute(REASON_ATTR, request.reason)
                .withAttribute(DURATION_MS_ATTR, request.duration)
                .withAttribute(GLOBAL_ATTR, request.global)
        } else null

    private fun createScheduleCallback(
        request: ModerationActionRequest,
        actionBuilder: AuditActionBuilder?
    ): () -> Unit = {
        transactionHandler.runInTransaction {
            if (!request.stateless) {
                request.duration?.let { scheduleUnMute(request) }
                storeState(request)
            }
            actionBuilder?.save()
        }
    }

    private fun scheduleUnMute(request: ModerationActionRequest) {
        val violator = request.violator
            ?: throw IllegalArgumentException("Violator cannot be null when scheduling un-mute")

        runCatching {
            removeUnMuteSchedule(violator, request.channel)

            val job = UnMuteJob.createDetails(request.global, request.channel, violator)
            val startTime = request.duration?.let { duration ->
                Date.from(Instant.now().plusMillis(duration))
            } ?: Date()

            val trigger = TriggerBuilder.newTrigger()
                .startAt(startTime)
                .withSchedule(SimpleScheduleBuilder.simpleSchedule())
                .build()

            schedulerFactoryBean.scheduler.scheduleJob(job, trigger)
        }.onFailure { exception ->
            throw RuntimeException("Failed to schedule un-mute", exception)
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun storeState(request: ModerationActionRequest) {
        val violator = request.violator
            ?: throw IllegalArgumentException("Violator cannot be null when storing mute state")

        val expireInstant = request.duration?.let { duration ->
            Instant.now().plusMillis(duration)
        } ?: Instant.now()
            .atZone(ZoneId.systemDefault())
            .plusYears(PERMANENT_MUTE_YEARS)
            .toInstant()

        val state = MuteState(
            userId = violator.user.id,
            guildId = violator.guild.idLong,
            isGlobal = request.global,
            channelId = request.channel?.id,
            reason = request.reason ?: "",
            expire = kotlin.time.Instant.fromEpochMilliseconds(expireInstant.toEpochMilli())
        )

        muteStateRepository.save(state)
    }

    private fun handleChannelMute(
        request: ModerationActionRequest,
        onSuccess: () -> Unit
    ): Boolean {
        requireNotNull(request.violator) { "Violator cannot be null for channel mute" }

        val channel = request.channel ?: return false
        val override = channel.getPermissionOverride(request.violator)

        // Check if already muted in this channel
        if (Permission.MESSAGE_SEND in (override?.denied ?: emptySet())) {
            return false
        }

        when (override) {
            null -> channel.permissionContainer.upsertPermissionOverride(request.violator)
                .setDenied(Permission.MESSAGE_SEND)
                .queue { onSuccess() }

            else -> override.manager
                .deny(Permission.MESSAGE_SEND)
                .queue { onSuccess() }
        }

        return true
    }

    private fun handleJobBasedMuteRefresh(scheduler: Scheduler, member: Member) {
        runCatching {
            val key = UnMuteJob.getKey(member)
            if (!scheduler.checkExists(key)) return

            val detail = scheduler.getJobDetail(key) ?: return
            val data = detail.jobDataMap
            val isGlobal = data.getBoolean(UnMuteJob.ATTR_GLOBAL_ID)
            val channelId = data.getString(UnMuteJob.ATTR_CHANNEL_ID)
            val textChannel = channelId?.let(member.guild::getTextChannelById)

            if (isGlobal || textChannel != null) {
                val request = ModerationActionRequest.Builder().apply {
                    type = ModerationActionType.MUTE
                    channel = textChannel
                    violator = member
                    global = isGlobal
                    auditLogging = false
                }.build()

                mute(request)
            }
        }.onFailure {
            // Log error if needed, but don't propagate exceptions during refresh
        }
    }

    private fun removeUnMuteSchedule(member: Member, channel: TextChannel?) {
        val scheduler = schedulerFactoryBean.scheduler

        runCatching {
            removeJobIfExists(scheduler, UnMuteJob.getKey(member)) {
                muteStateRepository.deleteByGuildIdAndUserId(
                    member.guild.idLong,
                    member.user.id
                )
            }

            channel?.let { ch ->
                removeJobIfExists(scheduler, UnMuteJob.getKey(member, ch)) {
                    muteStateRepository.deleteByGuildIdAndUserIdAndChannelId(
                        member.guild.idLong,
                        member.user.id,
                        ch.id
                    )
                }
            }
        }
    }

    private inline fun removeJobIfExists(
        scheduler: Scheduler,
        key: JobKey,
        onJobExists: () -> Unit
    ) {
        if (scheduler.checkExists(key)) {
            scheduler.deleteJob(key)
            onJobExists()
        }
    }

    private fun handleGlobalMute(
        request: ModerationActionRequest,
        onSuccess: () -> Unit
    ): Boolean {
        val violator = request.violator ?: return false
        val guild = request.guild
        val mutedRole = getMutedRole(guild) ?: return false

        return when {
            mutedRole in violator.roles -> false
            else -> {
                guild.addRoleToMember(violator, mutedRole)
                    .queue { onSuccess() }
                true
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun processState(
        member: Member,
        muteState: MuteState
    ): Boolean {
        val now = Clock.System.now()
        val expire = muteState.expire

        if (now.toJavaInstant().isBefore(expire.toJavaInstant())) {
            return false
        }

        val textChannel = muteState.channelId?.let {
            member.guild.getTextChannelById(it)
        }

        if (!muteState.isGlobal && textChannel == null) {
            return false
        }

        val duration = expire - now

        val request = ModerationActionRequest.Builder().apply {
            type = ModerationActionType.MUTE
            channel = textChannel
            violator = member
            global = muteState.isGlobal
            this.duration = duration.inWholeMilliseconds
            reason = muteState.reason
            stateless = true
            auditLogging = false
        }.build()

        mute(request)
        return true
    }

    private fun checkPermission(
        channel: GuildChannel,
        role: Role,
        mode: PermissionMode,
        permission: Permission,
    ) {
        if (!channel.guild.selfMember.hasPermission(channel, Permission.MANAGE_PERMISSIONS))
            return

        val permissionOverride = channel.permissionContainer.getPermissionOverride(role)

        when (mode) {
            PermissionMode.DENY -> handleDenyPermission(channel, role, permission, permissionOverride)
            PermissionMode.ALLOW -> handleAllowPermission(channel, role, permission, permissionOverride)
            PermissionMode.UNCHECKED -> {}
        }
    }

    private fun handleAllowPermission(
        channel: GuildChannel,
        role: Role,
        permission: Permission,
        permissionOverride: PermissionOverride?
    ) {
        if (permissionOverride == null) {
            channel.permissionContainer.upsertPermissionOverride(role)
                .setAllowed(permission)
                .queue()
        } else if (!permissionOverride.allowed.contains(permission)) {
            permissionOverride.manager.grant(permission).queue()
        }
    }

    private fun handleDenyPermission(
        channel: GuildChannel,
        role: Role,
        permission: Permission,
        override: PermissionOverride?
    ) {
        if (override == null) {
            channel.permissionContainer.upsertPermissionOverride(role)
                .setDenied(permission)
                .queue()
        } else if (!override.denied.contains(permission)) {
            override.manager
                .deny(permission)
                .queue()
        }
    }
}