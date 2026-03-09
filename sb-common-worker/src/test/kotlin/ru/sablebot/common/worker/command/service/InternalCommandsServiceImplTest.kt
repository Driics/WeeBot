package ru.sablebot.common.worker.command.service

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.mockk.*
import io.mockk.junit5.MockKExtension
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import ru.sablebot.common.support.CoroutineLauncher
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclaration
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.configuration.WorkerProperties
import ru.sablebot.common.worker.message.service.MessageService
import ru.sablebot.common.worker.shared.service.DiscordEntityAccessor
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class InternalCommandsServiceImplTest {

    private lateinit var workerProperties: WorkerProperties
    private lateinit var holderService: CommandsHolderService
    private lateinit var messageService: MessageService
    private lateinit var entityAccessor: DiscordEntityAccessor
    private lateinit var coroutineLauncher: CoroutineLauncher
    private lateinit var meterRegistry: MeterRegistry
    private lateinit var counter: Counter
    private lateinit var timer: Timer

    private lateinit var coolDownManager: CommandCoolDownManager
    private lateinit var service: InternalCommandsServiceImpl

    @BeforeEach
    fun setUp() {
        workerProperties = WorkerProperties()
        holderService = mockk(relaxed = true)
        messageService = mockk(relaxed = true)
        entityAccessor = mockk(relaxed = true)
        coroutineLauncher = mockk(relaxed = true)
        meterRegistry = mockk(relaxed = true)
        counter = mockk(relaxed = true)
        timer = mockk(relaxed = true)
        coolDownManager = mockk(relaxed = true)

        every { meterRegistry.counter(any(), *anyVararg()) } returns counter
        every { meterRegistry.timer(any(), *anyVararg()) } returns timer

        service = InternalCommandsServiceImpl(
            workerProperties,
            holderService,
            messageService,
            entityAccessor,
            coroutineLauncher,
            meterRegistry,
            coolDownManager
        )
    }

    private fun mockEvent(
        hasGuild: Boolean = true,
        commandName: String = "test",
        fullCommandName: String = "test"
    ): SlashCommandInteractionEvent {
        val guild = if (hasGuild) mockk<Guild>(relaxed = true) else null
        val channel = mockk<GuildMessageChannelUnion>(relaxed = true)
        val user = mockk<User>(relaxed = true) {
            every { id } returns "123"
        }
        val member = mockk<Member>(relaxed = true)

        return mockk<SlashCommandInteractionEvent>(relaxed = true) {
            every { this@mockk.guild } returns guild
            every { this@mockk.name } returns commandName
            every { this@mockk.fullCommandName } returns fullCommandName
            every { guildChannel } returns channel
            every { this@mockk.user } returns user
            every { this@mockk.member } returns member
            every { options } returns emptyList<OptionMapping>()
            every { isAcknowledged } returns false
        }
    }

    // --- sendCommand tests ---

    @Test
    fun `sendCommand returns false when guild is null`() {
        val event = mockEvent(hasGuild = false)

        val result = service.sendCommand(event)

        assertFalse(result)
        verify(exactly = 0) { holderService.getDslCommandByFullPath(any()) }
    }

    @Test
    fun `sendCommand routes to DSL command when found`() {
        val event = mockEvent(fullCommandName = "moderation ban")
        val executor = mockk<SlashCommandExecutor>(relaxed = true)
        val dslCommand = mockk<SlashCommandDeclaration>(relaxed = true) {
            every { name } returns "ban"
            every { this@mockk.executor } returns executor
            every { botPermissions } returns emptySet()
        }

        every { holderService.getDslCommandByFullPath("moderation ban") } returns dslCommand

        val result = service.sendCommand(event)

        assertTrue(result)
        verify { holderService.getDslCommandByFullPath("moderation ban") }
    }

    @Test
    fun `sendCommand returns false when no command found at all`() {
        val event = mockEvent(commandName = "unknown", fullCommandName = "unknown")

        every { holderService.getDslCommandByFullPath("unknown") } returns null
        every { holderService.getByLocale(localizedKey = "unknown", anyLocale = true) } returns null

        val result = service.sendCommand(event)

        assertFalse(result)
    }

    // --- executeDslCommand tests ---

    @Test
    fun `executeDslCommand checks bot permissions and replies when missing`() {
        val event = mockEvent(fullCommandName = "admin kick")
        val executor = mockk<SlashCommandExecutor>(relaxed = true)
        val dslCommand = mockk<SlashCommandDeclaration>(relaxed = true) {
            every { name } returns "kick"
            every { this@mockk.executor } returns executor
            every { botPermissions } returns setOf(Permission.KICK_MEMBERS)
        }

        // The guild's selfMember lacks the permission
        val selfMember = mockk<Member>(relaxed = true) {
            every { hasPermission(any<GuildChannel>(), *anyVararg()) } returns false
        }
        every { event.guild!!.selfMember } returns selfMember

        every { holderService.getDslCommandByFullPath("admin kick") } returns dslCommand

        val result = service.sendCommand(event)

        // Returns true because the event was handled (replied with error)
        assertTrue(result)
        // Should NOT launch a coroutine since permissions check failed
        verify(exactly = 0) { coroutineLauncher.launchMessageJob(any(), any()) }
    }

    @Test
    fun `executeDslCommand launches coroutine when permissions are satisfied`() {
        val event = mockEvent(fullCommandName = "music play")
        val executor = mockk<SlashCommandExecutor>(relaxed = true)
        val dslCommand = mockk<SlashCommandDeclaration>(relaxed = true) {
            every { name } returns "play"
            every { this@mockk.executor } returns executor
            every { botPermissions } returns setOf(Permission.MESSAGE_SEND)
        }

        // The guild's selfMember has the permission
        val selfMember = mockk<Member>(relaxed = true) {
            every { hasPermission(any<GuildChannel>(), *anyVararg()) } returns true
        }
        every { event.guild!!.selfMember } returns selfMember

        every { holderService.getDslCommandByFullPath("music play") } returns dslCommand

        val result = service.sendCommand(event)

        assertTrue(result)
        verify { coroutineLauncher.launchMessageJob(event, any()) }
    }

    @Test
    fun `executeDslCommand returns false when executor is null`() {
        val event = mockEvent(fullCommandName = "group")
        val dslCommand = mockk<SlashCommandDeclaration>(relaxed = true) {
            every { name } returns "group"
            every { executor } returns null
        }

        every { holderService.getDslCommandByFullPath("group") } returns dslCommand

        val result = service.sendCommand(event)

        // No executor means it returns false (not handled)
        assertFalse(result)
        verify(exactly = 0) { coroutineLauncher.launchMessageJob(any(), any()) }
    }

    // --- Metrics tests ---

    @Test
    fun `metrics are recorded on successful DSL execution`() {
        val event = mockEvent(fullCommandName = "test cmd")
        val executor = mockk<SlashCommandExecutor>(relaxed = true)
        val dslCommand = mockk<SlashCommandDeclaration>(relaxed = true) {
            every { name } returns "cmd"
            every { this@mockk.executor } returns executor
            every { botPermissions } returns emptySet()
        }

        every { holderService.getDslCommandByFullPath("test cmd") } returns dslCommand

        // Capture the coroutine block to execute it synchronously
        val blockSlot = slot<suspend kotlinx.coroutines.CoroutineScope.() -> Unit>()
        every { coroutineLauncher.launchMessageJob(any(), capture(blockSlot)) } just runs

        service.sendCommand(event)

        // Execute the captured coroutine block
        kotlinx.coroutines.test.runTest {
            blockSlot.captured.invoke(this)
        }

        // Verify success counter was incremented
        verify {
            meterRegistry.counter(
                InternalCommandsService.COMMANDS_EXECUTED_COUNTER,
                "command", "cmd", "type", "dsl", "outcome", "success"
            )
        }
        verify { counter.increment() }
    }

    @Test
    fun `metrics are recorded on error during DSL execution`() {
        val event = mockEvent(fullCommandName = "test fail")
        val executor = mockk<SlashCommandExecutor>(relaxed = true) {
            coEvery { execute(any(), any()) } throws RuntimeException("boom")
        }
        val dslCommand = mockk<SlashCommandDeclaration>(relaxed = true) {
            every { name } returns "fail"
            every { this@mockk.executor } returns executor
            every { botPermissions } returns emptySet()
        }

        every { holderService.getDslCommandByFullPath("test fail") } returns dslCommand

        val blockSlot = slot<suspend kotlinx.coroutines.CoroutineScope.() -> Unit>()
        every { coroutineLauncher.launchMessageJob(any(), capture(blockSlot)) } just runs

        service.sendCommand(event)

        // Execute the captured coroutine block
        kotlinx.coroutines.test.runTest {
            blockSlot.captured.invoke(this)
        }

        // Verify error counter was incremented
        verify {
            meterRegistry.counter(
                InternalCommandsService.COMMANDS_ERRORS_COUNTER,
                "command", "fail", "type", "dsl", "error_type", "unexpected"
            )
        }
        // Verify the executed counter recorded error outcome
        verify {
            meterRegistry.counter(
                InternalCommandsService.COMMANDS_EXECUTED_COUNTER,
                "command", "fail", "type", "dsl", "outcome", "error"
            )
        }
    }
}
