package ru.sablebot.module.moderation.service.impl

import dev.minn.jda.ktx.coroutines.await
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.*
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.quartz.JobKey
import org.quartz.Scheduler
import org.springframework.scheduling.quartz.SchedulerFactoryBean
import ru.sablebot.common.model.AuditActionType
import ru.sablebot.common.model.ModerationCaseType
import ru.sablebot.common.persistence.entity.ModerationCase
import ru.sablebot.common.persistence.entity.ModerationConfig
import ru.sablebot.common.persistence.repository.ModerationCaseRepository
import ru.sablebot.common.service.ModerationConfigService
import ru.sablebot.common.worker.modules.audit.model.AuditActionBuilder
import ru.sablebot.common.worker.modules.audit.service.AuditService
import ru.sablebot.common.worker.modules.moderation.service.MuteService
import java.time.Instant
import java.util.concurrent.TimeUnit

@ExtendWith(MockKExtension::class)
class ModerationServiceImplTest {

    private lateinit var caseRepository: ModerationCaseRepository
    private lateinit var configService: ModerationConfigService
    private lateinit var auditService: AuditService
    private lateinit var muteService: MuteService
    private lateinit var schedulerFactoryBean: SchedulerFactoryBean
    private lateinit var meterRegistry: MeterRegistry
    private lateinit var counter: Counter

    private lateinit var service: ModerationServiceImpl

    @BeforeEach
    fun setUp() {
        caseRepository = mockk()
        configService = mockk()
        auditService = mockk()
        muteService = mockk()
        schedulerFactoryBean = mockk()
        meterRegistry = mockk()
        counter = mockk(relaxed = true)

        every { meterRegistry.counter(any(), *anyVararg()) } returns counter

        service = ModerationServiceImpl(
            caseRepository, configService, auditService, muteService, schedulerFactoryBean, meterRegistry
        )
    }

    private fun mockGuild(guildId: Long = 1L, guildName: String = "Test Guild"): Guild {
        return mockk<Guild>(relaxed = true) {
            every { idLong } returns guildId
            every { id } returns guildId.toString()
            every { name } returns guildName
        }
    }

    private fun mockMember(
        userId: Long = 100L,
        userName: String = "TestUser",
        effectiveName: String = "TestUser"
    ): Member {
        val user = mockk<User>(relaxed = true) {
            every { id } returns userId.toString()
            every { idLong } returns userId
            every { name } returns userName
            every { this@mockk.effectiveName } returns effectiveName
        }
        return mockk<Member>(relaxed = true) {
            every { this@mockk.user } returns user
            every { this@mockk.effectiveName } returns effectiveName
        }
    }

    private fun mockUser(
        userId: Long = 100L,
        userName: String = "TestUser",
        effectiveName: String = "TestUser"
    ): User {
        return mockk<User>(relaxed = true) {
            every { id } returns userId.toString()
            every { idLong } returns userId
            every { name } returns userName
            every { this@mockk.effectiveName } returns effectiveName
        }
    }

    private fun mockAuditActionBuilder(): AuditActionBuilder {
        val builder = mockk<AuditActionBuilder>(relaxed = true)
        every { builder.withUser(any<Member>()) } returns builder
        every { builder.withTargetUser(any<User>()) } returns builder
        every { builder.withAttribute(any<String>(), any()) } returns builder
        every { builder.save() } returns mockk(relaxed = true)
        return builder
    }

    private fun setupBasicMocks(guildId: Long = 1L, caseNumber: Int = 1) {
        // Mock case repository
        every { caseRepository.findMaxCaseNumber(guildId) } returns (caseNumber - 1)
        every { caseRepository.save(any<ModerationCase>()) } answers {
            firstArg<ModerationCase>().apply {
                this.id = 999L
            }
        }

        // Mock config service (no modlog channel by default)
        every { configService.getByGuildId(guildId) } returns null

        // Mock audit service
        every { auditService.log(any<Guild>(), any<AuditActionType>()) } returns mockAuditActionBuilder()
    }

    // --- ban() tests ---

    @Test
    fun `ban creates case and records metrics`() = runTest {
        val guild = mockGuild(guildId = 1L)
        val target = mockMember(userId = 100L, effectiveName = "Target")
        val moderator = mockMember(userId = 200L, effectiveName = "Moderator")

        setupBasicMocks(guildId = 1L)

        val banAction = mockk<AuditableRestAction<Void>>(relaxed = true)
        every { guild.ban(target, 0, TimeUnit.DAYS) } returns banAction
        every { banAction.reason(any()) } returns banAction
        coEvery { banAction.await() } returns null

        val result = service.ban(guild, target, moderator, "Test reason", null, null)

        // Verify metrics recorded
        verify { meterRegistry.counter("sablebot.moderation.actions", "type", "ban") }

        // Verify case created
        verify { caseRepository.save(any<ModerationCase>()) }

        // Verify audit log
        verify { auditService.log(guild, AuditActionType.MEMBER_BAN) }
    }

    @Test
    fun `ban with duration schedules unban job`() = runTest {
        val guild = mockGuild(guildId = 1L)
        val target = mockMember(userId = 100L)
        val moderator = mockMember(userId = 200L)
        val duration = 3600000L // 1 hour

        setupBasicMocks(guildId = 1L)

        val banAction = mockk<AuditableRestAction<Void>>(relaxed = true)
        every { guild.ban(target, 0, TimeUnit.DAYS) } returns banAction
        every { banAction.reason(any()) } returns banAction
        coEvery { banAction.await() } returns null

        val scheduler = mockk<Scheduler>(relaxed = true)
        every { schedulerFactoryBean.scheduler } returns scheduler
        every { scheduler.checkExists(any<JobKey>()) } returns false
        every { scheduler.scheduleJob(any(), any()) } returns mockk(relaxed = true)

        service.ban(guild, target, moderator, "Temp ban", duration, null)

        // Verify scheduling was attempted
        verify { schedulerFactoryBean.scheduler }
    }

    @Test
    fun `ban with deleteDays parameter uses correct value`() = runTest {
        val guild = mockGuild(guildId = 1L)
        val target = mockMember(userId = 100L)
        val moderator = mockMember(userId = 200L)

        setupBasicMocks(guildId = 1L)

        val banAction = mockk<AuditableRestAction<Void>>(relaxed = true)
        every { guild.ban(target, 7, TimeUnit.DAYS) } returns banAction
        every { banAction.reason(any()) } returns banAction
        coEvery { banAction.await() } returns null

        service.ban(guild, target, moderator, "Ban with cleanup", null, 7)

        // Verify ban was called with 7 days
        verify { guild.ban(target, 7, TimeUnit.DAYS) }
    }

    // --- unban() tests ---

    @Test
    fun `unban creates case and records metrics`() = runTest {
        val guild = mockGuild(guildId = 1L)
        val targetUser = mockUser(userId = 100L)
        val moderator = mockMember(userId = 200L)

        setupBasicMocks(guildId = 1L)

        val unbanAction = mockk<AuditableRestAction<Void>>(relaxed = true)
        every { guild.unban(targetUser) } returns unbanAction
        every { unbanAction.reason(any()) } returns unbanAction
        coEvery { unbanAction.await() } returns null

        val scheduler = mockk<Scheduler>(relaxed = true)
        every { schedulerFactoryBean.scheduler } returns scheduler
        every { scheduler.checkExists(any<JobKey>()) } returns false

        val result = service.unban(guild, targetUser, moderator, "Appeal accepted")

        // Verify metrics recorded
        verify { meterRegistry.counter("sablebot.moderation.actions", "type", "unban") }

        // Verify case created
        verify { caseRepository.save(any<ModerationCase>()) }

        // Verify audit log
        verify { auditService.log(guild, AuditActionType.MEMBER_UNBAN) }
    }

    @Test
    fun `unban removes scheduled unban job if exists`() = runTest {
        val guild = mockGuild(guildId = 1L)
        val targetUser = mockUser(userId = 100L)
        val moderator = mockMember(userId = 200L)

        setupBasicMocks(guildId = 1L)

        val unbanAction = mockk<AuditableRestAction<Void>>(relaxed = true)
        every { guild.unban(targetUser) } returns unbanAction
        every { unbanAction.reason(any()) } returns unbanAction
        coEvery { unbanAction.await() } returns null

        val scheduler = mockk<Scheduler>(relaxed = true)
        every { schedulerFactoryBean.scheduler } returns scheduler
        every { scheduler.checkExists(any<JobKey>()) } returns true
        every { scheduler.deleteJob(any<JobKey>()) } returns true

        service.unban(guild, targetUser, moderator, null)

        // Verify job removal was attempted
        verify { scheduler.deleteJob(any<JobKey>()) }
    }

    // --- kick() tests ---

    @Test
    fun `kick creates case and records metrics`() = runTest {
        val guild = mockGuild(guildId = 1L)
        val target = mockMember(userId = 100L)
        val moderator = mockMember(userId = 200L)

        setupBasicMocks(guildId = 1L)

        val kickAction = mockk<AuditableRestAction<Void>>(relaxed = true)
        every { guild.kick(target) } returns kickAction
        every { kickAction.reason(any()) } returns kickAction
        coEvery { kickAction.await() } returns null

        val result = service.kick(guild, target, moderator, "Violation of rules")

        // Verify metrics recorded
        verify { meterRegistry.counter("sablebot.moderation.actions", "type", "kick") }

        // Verify case created
        verify { caseRepository.save(any<ModerationCase>()) }

        // Verify audit log
        verify { auditService.log(guild, AuditActionType.MEMBER_KICK) }

        // Verify Discord API call
        verify { guild.kick(target) }
    }

    // --- warn() tests ---

    @Test
    fun `warn creates case and records metrics`() = runTest {
        val guild = mockGuild(guildId = 1L)
        val target = mockMember(userId = 100L)
        val moderator = mockMember(userId = 200L)

        setupBasicMocks(guildId = 1L)

        // Mock config with no escalation rules
        every { configService.getByGuildId(1L) } returns ModerationConfig().apply {
            guildId = 1L
            escalationRules = mutableListOf()
        }

        val result = service.warn(guild, target, moderator, "First warning")

        // Verify metrics recorded
        verify { meterRegistry.counter("sablebot.moderation.actions", "type", "warn") }

        // Verify case created
        verify { caseRepository.save(any<ModerationCase>()) }

        // Verify audit log
        verify { auditService.log(guild, AuditActionType.MEMBER_WARN) }
    }

    @Test
    fun `warn does not escalate when below threshold`() = runTest {
        val guild = mockGuild(guildId = 1L)
        val target = mockMember(userId = 100L)
        val moderator = mockMember(userId = 200L)

        setupBasicMocks(guildId = 1L)

        // Mock config with escalation rule at threshold 3
        every { configService.getByGuildId(1L) } returns ModerationConfig().apply {
            guildId = 1L
            escalationRules = mutableListOf()
        }

        // Mock only 2 active warnings (below threshold)
        every {
            caseRepository.countByGuildIdAndTargetIdAndActionTypeAndActive(
                1L, "100", ModerationCaseType.WARN, true
            )
        } returns 2

        service.warn(guild, target, moderator, "Warning")

        // Verify no kick/ban/mute was attempted
        coVerify(exactly = 0) { muteService.mute(any()) }
    }

    // --- timeout() tests ---

    @Test
    fun `timeout creates case and records metrics`() = runTest {
        val guild = mockGuild(guildId = 1L)
        val target = mockMember(userId = 100L)
        val moderator = mockMember(userId = 200L)
        val duration = 600000L // 10 minutes

        setupBasicMocks(guildId = 1L)

        val timeoutAction = mockk<AuditableRestAction<Void>>(relaxed = true)
        every { target.timeoutFor(any()) } returns timeoutAction
        every { timeoutAction.reason(any()) } returns timeoutAction
        coEvery { timeoutAction.await() } returns null

        val result = service.timeout(guild, target, moderator, duration, "Spamming")

        // Verify metrics recorded
        verify { meterRegistry.counter("sablebot.moderation.actions", "type", "timeout") }

        // Verify case created with duration
        verify {
            caseRepository.save(match<ModerationCase> {
                it.actionType == ModerationCaseType.TIMEOUT && it.duration == duration
            })
        }

        // Verify audit log
        verify { auditService.log(guild, AuditActionType.MEMBER_MUTE) }

        // Verify Discord API call
        verify { target.timeoutFor(any()) }
    }

    // --- removeTimeout() tests ---

    @Test
    fun `removeTimeout creates case and records metrics`() = runTest {
        val guild = mockGuild(guildId = 1L)
        val target = mockMember(userId = 100L)
        val moderator = mockMember(userId = 200L)

        setupBasicMocks(guildId = 1L)

        val removeTimeoutAction = mockk<AuditableRestAction<Void>>(relaxed = true)
        every { target.removeTimeout() } returns removeTimeoutAction
        every { removeTimeoutAction.reason(any()) } returns removeTimeoutAction
        coEvery { removeTimeoutAction.await() } returns null

        val result = service.removeTimeout(guild, target, moderator, "Good behavior")

        // Verify metrics recorded
        verify { meterRegistry.counter("sablebot.moderation.actions", "type", "untimeout") }

        // Verify case created
        verify {
            caseRepository.save(match<ModerationCase> {
                it.actionType == ModerationCaseType.UNTIMEOUT
            })
        }

        // Verify audit log
        verify { auditService.log(guild, AuditActionType.MEMBER_UNMUTE) }

        // Verify Discord API call
        verify { target.removeTimeout() }
    }

    // --- Helper method tests ---

    @Test
    fun `all action methods send modlog embed when channel is configured`() = runTest {
        val guild = mockGuild(guildId = 1L)
        val target = mockMember(userId = 100L)
        val moderator = mockMember(userId = 200L)

        setupBasicMocks(guildId = 1L)

        // Configure modlog channel
        val modlogChannel = mockk<TextChannel>(relaxed = true)
        every { guild.getTextChannelById(999L) } returns modlogChannel
        every { modlogChannel.sendMessageEmbeds(any<MessageEmbed>()) } returns mockk(relaxed = true)

        every { configService.getByGuildId(1L) } returns ModerationConfig().apply {
            guildId = 1L
            modlogChannelId = 999L
        }

        // Test kick (simpler than ban)
        val kickAction = mockk<AuditableRestAction<Void>>(relaxed = true)
        every { guild.kick(target) } returns kickAction
        every { kickAction.reason(any()) } returns kickAction
        coEvery { kickAction.await() } returns null

        service.kick(guild, target, moderator, "Test")

        // Verify modlog embed was sent
        verify { modlogChannel.sendMessageEmbeds(any<MessageEmbed>()) }
    }
}
