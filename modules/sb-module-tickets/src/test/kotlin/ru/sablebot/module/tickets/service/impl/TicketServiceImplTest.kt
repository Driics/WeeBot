package ru.sablebot.module.tickets.service.impl

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.*
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import ru.sablebot.common.model.TicketStatus
import ru.sablebot.common.persistence.entity.Ticket
import ru.sablebot.common.persistence.repository.TicketMessageRepository
import ru.sablebot.common.persistence.repository.TicketRepository
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExtendWith(MockKExtension::class)
class TicketServiceImplTest {

    private lateinit var ticketRepository: TicketRepository
    private lateinit var ticketMessageRepository: TicketMessageRepository
    private lateinit var meterRegistry: MeterRegistry
    private lateinit var counter: Counter

    private lateinit var service: TicketServiceImpl

    @BeforeEach
    fun setUp() {
        ticketRepository = mockk()
        ticketMessageRepository = mockk()
        meterRegistry = mockk()
        counter = mockk(relaxed = true)

        every { meterRegistry.counter(any(), *anyVararg()) } returns counter

        service = TicketServiceImpl(
            ticketRepository, ticketMessageRepository, meterRegistry
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
            every { this@mockk.id } returns userId.toString()
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

    // --- createTicket() tests ---

    @Test
    fun `createTicket generates sequential ticket numbers`() = runTest {
        val guild = mockGuild(guildId = 1L)
        val user = mockUser(userId = 100L, effectiveName = "User1")

        // First ticket should be #1
        every { ticketRepository.findMaxTicketNumber(1L) } returns 0
        every { ticketRepository.save(any<Ticket>()) } answers {
            firstArg<Ticket>().apply { this.id = 1L }
        }

        val ticket1 = service.createTicket(guild, user, "First issue", null, null)

        assertEquals(1, ticket1.ticketNumber)
        assertEquals(TicketStatus.OPEN, ticket1.status)
        assertEquals("100", ticket1.userId)

        // Second ticket should be #2
        every { ticketRepository.findMaxTicketNumber(1L) } returns 1
        every { ticketRepository.save(any<Ticket>()) } answers {
            firstArg<Ticket>().apply { this.id = 2L }
        }

        val ticket2 = service.createTicket(guild, user, "Second issue", null, null)

        assertEquals(2, ticket2.ticketNumber)

        // Verify metrics
        verify(exactly = 2) { meterRegistry.counter("sablebot.tickets.actions", "type", "create") }
    }

    @Test
    fun `createTicket sets all required fields correctly`() = runTest {
        val guild = mockGuild(guildId = 1L)
        val user = mockUser(userId = 100L, effectiveName = "TestUser")

        every { ticketRepository.findMaxTicketNumber(1L) } returns 5
        every { ticketRepository.save(any<Ticket>()) } answers {
            firstArg<Ticket>().apply { this.id = 999L }
        }

        val ticket = service.createTicket(
            guild = guild,
            user = user,
            subject = "Help with bot",
            category = "Technical Support",
            initialMessage = null
        )

        assertEquals(6, ticket.ticketNumber)
        assertEquals(1L, ticket.guildId)
        assertEquals("100", ticket.userId)
        assertEquals("TestUser", ticket.userName)
        assertEquals("Help with bot", ticket.subject)
        assertEquals("Technical Support", ticket.category)
        assertEquals(TicketStatus.OPEN, ticket.status)
        assertEquals(true, ticket.active)
        assertNotNull(ticket.createdAt)
        assertNotNull(ticket.updatedAt)
    }

    @Test
    fun `createTicket with category stores category name`() = runTest {
        val guild = mockGuild(guildId = 1L)
        val user = mockUser(userId = 100L)

        every { ticketRepository.findMaxTicketNumber(1L) } returns 0
        every { ticketRepository.save(any<Ticket>()) } answers {
            firstArg<Ticket>().apply { this.id = 1L }
        }

        val ticket = service.createTicket(
            guild = guild,
            user = user,
            subject = "Category test",
            category = "General",
            initialMessage = null
        )

        assertEquals("General", ticket.category)
    }

    // --- Per-user limit tests ---

    @Test
    fun `countActiveUserTickets returns correct count`() {
        val guildId = 1L
        val userId = "100"

        // User has 2 active tickets
        every { ticketRepository.countByGuildIdAndUserIdAndActive(guildId, userId, true) } returns 2

        val count = service.countActiveUserTickets(guildId, userId)

        assertEquals(2, count)
        verify { ticketRepository.countByGuildIdAndUserIdAndActive(guildId, userId, true) }
    }

    @Test
    fun `countActiveUserTickets returns zero when user has no tickets`() {
        val guildId = 1L
        val userId = "100"

        every { ticketRepository.countByGuildIdAndUserIdAndActive(guildId, userId, true) } returns 0

        val count = service.countActiveUserTickets(guildId, userId)

        assertEquals(0, count)
    }

    @Test
    fun `countActiveUserTickets only counts active tickets`() {
        val guildId = 1L
        val userId = "100"

        // User has 3 active tickets (closed tickets not included)
        every { ticketRepository.countByGuildIdAndUserIdAndActive(guildId, userId, true) } returns 3

        val count = service.countActiveUserTickets(guildId, userId)

        assertEquals(3, count)
        // Verify only active=true was queried
        verify { ticketRepository.countByGuildIdAndUserIdAndActive(guildId, userId, true) }
    }

    @Test
    fun `getActiveUserTickets returns only active tickets`() {
        val guildId = 1L
        val userId = "100"

        val activeTickets = listOf(
            Ticket().apply {
                this.id = 1L
                this.guildId = guildId
                this.userId = userId
                this.active = true
                this.status = TicketStatus.OPEN
            },
            Ticket().apply {
                this.id = 2L
                this.guildId = guildId
                this.userId = userId
                this.active = true
                this.status = TicketStatus.CLAIMED
            }
        )

        every { ticketRepository.findByGuildIdAndUserIdAndActive(guildId, userId, true) } returns activeTickets

        val result = service.getActiveUserTickets(guildId, userId)

        assertEquals(2, result.size)
        assertEquals(true, result.all { it.active })
    }

    // --- closeTicket() tests ---

    @Test
    fun `closeTicket sets active to false`() = runTest {
        val guild = mockGuild(guildId = 1L)
        val staff = mockMember(userId = 200L, effectiveName = "StaffMember")

        val ticket = Ticket().apply {
            this.id = 1L
            this.guildId = 1L
            this.ticketNumber = 1
            this.status = TicketStatus.OPEN
            this.active = true
        }

        every { ticketRepository.save(any<Ticket>()) } answers { firstArg() }
        every { ticketMessageRepository.save(any()) } answers { firstArg() }

        val closedTicket = service.closeTicket(guild, ticket, staff, "Issue resolved")

        assertEquals(TicketStatus.CLOSED, closedTicket.status)
        assertEquals(false, closedTicket.active)
        assertNotNull(closedTicket.closedAt)

        // Verify metrics
        verify { meterRegistry.counter("sablebot.tickets.actions", "type", "close") }
    }

    @Test
    fun `closeTicket does not count toward active ticket limit`() = runTest {
        val guild = mockGuild(guildId = 1L)
        val staff = mockMember(userId = 200L)

        val ticket = Ticket().apply {
            this.id = 1L
            this.guildId = 1L
            this.userId = "100"
            this.ticketNumber = 1
            this.status = TicketStatus.OPEN
            this.active = true
        }

        every { ticketRepository.save(any<Ticket>()) } answers { firstArg() }
        every { ticketMessageRepository.save(any()) } answers { firstArg() }

        service.closeTicket(guild, ticket, staff, null)

        // After closing, verify the ticket is marked inactive
        assertEquals(false, ticket.active)

        // Verify that countActiveUserTickets would not count this
        every { ticketRepository.countByGuildIdAndUserIdAndActive(1L, "100", true) } returns 0

        val count = service.countActiveUserTickets(1L, "100")
        assertEquals(0, count)
    }

    // --- reopenTicket() tests ---

    @Test
    fun `reopenTicket sets active to true`() = runTest {
        val guild = mockGuild(guildId = 1L)
        val staff = mockMember(userId = 200L, effectiveName = "StaffMember")

        val ticket = Ticket().apply {
            this.id = 1L
            this.guildId = 1L
            this.ticketNumber = 1
            this.status = TicketStatus.CLOSED
            this.active = false
        }

        every { ticketRepository.save(any<Ticket>()) } answers { firstArg() }
        every { ticketMessageRepository.save(any()) } answers { firstArg() }

        val reopenedTicket = service.reopenTicket(guild, ticket, staff)

        assertEquals(TicketStatus.REOPENED, reopenedTicket.status)
        assertEquals(true, reopenedTicket.active)
        assertEquals(null, reopenedTicket.closedAt)

        // Verify metrics
        verify { meterRegistry.counter("sablebot.tickets.actions", "type", "reopen") }
    }

    @Test
    fun `reopenTicket counts toward active ticket limit again`() = runTest {
        val guild = mockGuild(guildId = 1L)
        val staff = mockMember(userId = 200L)

        val ticket = Ticket().apply {
            this.id = 1L
            this.guildId = 1L
            this.userId = "100"
            this.ticketNumber = 1
            this.status = TicketStatus.CLOSED
            this.active = false
        }

        every { ticketRepository.save(any<Ticket>()) } answers { firstArg() }
        every { ticketMessageRepository.save(any()) } answers { firstArg() }

        service.reopenTicket(guild, ticket, staff)

        // After reopening, verify the ticket is marked active
        assertEquals(true, ticket.active)

        // Verify that countActiveUserTickets would count this
        every { ticketRepository.countByGuildIdAndUserIdAndActive(1L, "100", true) } returns 1

        val count = service.countActiveUserTickets(1L, "100")
        assertEquals(1, count)
    }

    // --- claimTicket() tests ---

    @Test
    fun `claimTicket updates status and assigns staff`() = runTest {
        val guild = mockGuild(guildId = 1L)
        val staff = mockMember(userId = 200L, effectiveName = "StaffMember")

        val ticket = Ticket().apply {
            this.id = 1L
            this.guildId = 1L
            this.ticketNumber = 1
            this.status = TicketStatus.OPEN
        }

        every { ticketRepository.save(any<Ticket>()) } answers { firstArg() }
        every { ticketMessageRepository.save(any()) } answers { firstArg() }

        val claimedTicket = service.claimTicket(guild, ticket, staff)

        assertEquals(TicketStatus.CLAIMED, claimedTicket.status)
        assertEquals("200", claimedTicket.assignedStaffId)
        assertEquals("StaffMember", claimedTicket.assignedStaffName)

        // Verify metrics
        verify { meterRegistry.counter("sablebot.tickets.actions", "type", "claim") }
    }

    // --- unclaimTicket() tests ---

    @Test
    fun `unclaimTicket removes staff assignment`() = runTest {
        val guild = mockGuild(guildId = 1L)
        val staff = mockMember(userId = 200L, effectiveName = "StaffMember")

        val ticket = Ticket().apply {
            this.id = 1L
            this.guildId = 1L
            this.ticketNumber = 1
            this.status = TicketStatus.CLAIMED
            this.assignedStaffId = "200"
            this.assignedStaffName = "StaffMember"
        }

        every { ticketRepository.save(any<Ticket>()) } answers { firstArg() }
        every { ticketMessageRepository.save(any()) } answers { firstArg() }

        val unclaimedTicket = service.unclaimTicket(guild, ticket, staff)

        assertEquals(TicketStatus.OPEN, unclaimedTicket.status)
        assertEquals(null, unclaimedTicket.assignedStaffId)
        assertEquals(null, unclaimedTicket.assignedStaffName)

        // Verify metrics
        verify { meterRegistry.counter("sablebot.tickets.actions", "type", "unclaim") }
    }

    // --- Edge cases for rate limiting ---

    @Test
    fun `multiple users can each have tickets up to their limit`() {
        val guildId = 1L
        val user1Id = "100"
        val user2Id = "200"

        // User1 has 2 active tickets
        every { ticketRepository.countByGuildIdAndUserIdAndActive(guildId, user1Id, true) } returns 2

        // User2 has 3 active tickets
        every { ticketRepository.countByGuildIdAndUserIdAndActive(guildId, user2Id, true) } returns 3

        val count1 = service.countActiveUserTickets(guildId, user1Id)
        val count2 = service.countActiveUserTickets(guildId, user2Id)

        assertEquals(2, count1)
        assertEquals(3, count2)
    }

    @Test
    fun `tickets from different guilds do not interfere`() {
        val guild1Id = 1L
        val guild2Id = 2L
        val userId = "100"

        // Same user in different guilds
        every { ticketRepository.countByGuildIdAndUserIdAndActive(guild1Id, userId, true) } returns 2
        every { ticketRepository.countByGuildIdAndUserIdAndActive(guild2Id, userId, true) } returns 1

        val count1 = service.countActiveUserTickets(guild1Id, userId)
        val count2 = service.countActiveUserTickets(guild2Id, userId)

        assertEquals(2, count1)
        assertEquals(1, count2)
    }
}
