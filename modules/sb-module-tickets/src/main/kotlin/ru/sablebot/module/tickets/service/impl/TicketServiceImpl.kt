package ru.sablebot.module.tickets.service.impl

import dev.minn.jda.ktx.coroutines.await
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.sablebot.common.model.TicketStatus
import ru.sablebot.common.persistence.entity.Ticket
import ru.sablebot.common.persistence.entity.TicketMessage
import ru.sablebot.common.persistence.entity.base.NamedReference
import ru.sablebot.common.persistence.repository.TicketMessageRepository
import ru.sablebot.common.persistence.repository.TicketRepository
import ru.sablebot.module.tickets.service.ITicketService
import java.time.Instant

@Service
open class TicketServiceImpl(
    private val ticketRepository: TicketRepository,
    private val ticketMessageRepository: TicketMessageRepository,
    private val meterRegistry: MeterRegistry
) : ITicketService {

    companion object {
        private val log = KotlinLogging.logger { }
    }

    private fun recordAction(type: String) {
        meterRegistry.counter("sablebot.tickets.actions", "type", type).increment()
    }

    @Transactional
    override suspend fun createTicket(
        guild: Guild,
        user: User,
        subject: String,
        category: String?,
        initialMessage: String?
    ): Ticket {
        recordAction("create")

        val ticketNumber = ticketRepository.findMaxTicketNumber(guild.idLong) + 1

        val ticket = Ticket().apply {
            this.guildId = guild.idLong
            this.ticketNumber = ticketNumber
            this.status = TicketStatus.OPEN
            this.userId = user.id
            this.userName = user.effectiveName
            this.category = category
            this.subject = subject
            this.channelId = "" // Will be set by the caller after channel creation
            this.createdAt = Instant.now()
            this.updatedAt = Instant.now()
            this.active = true
        }

        val savedTicket = ticketRepository.save(ticket)

        log.info { "Created ticket #${savedTicket.ticketNumber} for user ${user.effectiveName} (${user.id}) in guild ${guild.name} (${guild.id})" }

        // Add initial message if provided
        if (!initialMessage.isNullOrBlank()) {
            addMessage(guild, savedTicket, user, initialMessage, "", emptyList())
        }

        return savedTicket
    }

    @Transactional
    override suspend fun closeTicket(
        guild: Guild,
        ticket: Ticket,
        closedBy: Member,
        reason: String?
    ): Ticket {
        recordAction("close")

        ticket.status = TicketStatus.CLOSED
        ticket.closedAt = Instant.now()
        ticket.updatedAt = Instant.now()
        ticket.active = false

        val savedTicket = ticketRepository.save(ticket)

        // Add system message
        val closeMessage = buildString {
            append("Ticket closed by ${closedBy.effectiveName}")
            if (reason != null) {
                append(": $reason")
            }
        }
        addSystemMessage(guild, savedTicket, closeMessage)

        log.info { "Closed ticket #${ticket.ticketNumber} in guild ${guild.name} (${guild.id}) by ${closedBy.effectiveName} (${closedBy.id})" }

        return savedTicket
    }

    @Transactional
    override suspend fun reopenTicket(
        guild: Guild,
        ticket: Ticket,
        reopenedBy: Member
    ): Ticket {
        recordAction("reopen")

        ticket.status = TicketStatus.REOPENED
        ticket.closedAt = null
        ticket.updatedAt = Instant.now()
        ticket.active = true

        val savedTicket = ticketRepository.save(ticket)

        // Add system message
        addSystemMessage(guild, savedTicket, "Ticket reopened by ${reopenedBy.effectiveName}")

        log.info { "Reopened ticket #${ticket.ticketNumber} in guild ${guild.name} (${guild.id}) by ${reopenedBy.effectiveName} (${reopenedBy.id})" }

        return savedTicket
    }

    @Transactional
    override suspend fun claimTicket(
        guild: Guild,
        ticket: Ticket,
        staff: Member
    ): Ticket {
        recordAction("claim")

        ticket.status = TicketStatus.CLAIMED
        ticket.assignedStaffId = staff.id
        ticket.assignedStaffName = staff.effectiveName
        ticket.updatedAt = Instant.now()

        val savedTicket = ticketRepository.save(ticket)

        // Add system message
        addSystemMessage(guild, savedTicket, "Ticket claimed by ${staff.effectiveName}")

        log.info { "Claimed ticket #${ticket.ticketNumber} in guild ${guild.name} (${guild.id}) by ${staff.effectiveName} (${staff.id})" }

        return savedTicket
    }

    @Transactional
    override suspend fun unclaimTicket(
        guild: Guild,
        ticket: Ticket,
        staff: Member
    ): Ticket {
        recordAction("unclaim")

        ticket.status = TicketStatus.OPEN
        ticket.assignedStaffId = null
        ticket.assignedStaffName = null
        ticket.updatedAt = Instant.now()

        val savedTicket = ticketRepository.save(ticket)

        // Add system message
        addSystemMessage(guild, savedTicket, "Ticket unclaimed by ${staff.effectiveName}")

        log.info { "Unclaimed ticket #${ticket.ticketNumber} in guild ${guild.name} (${guild.id}) by ${staff.effectiveName} (${staff.id})" }

        return savedTicket
    }

    @Transactional
    override suspend fun addMessage(
        guild: Guild,
        ticket: Ticket,
        author: User,
        content: String,
        messageId: String,
        attachments: List<Map<String, String>>
    ): TicketMessage {
        val message = TicketMessage(
            guildId = guild.idLong,
            ticketId = ticket.id!!,
            messageId = messageId,
            author = NamedReference(author.id, author.effectiveName),
            content = content,
            isSystemMessage = false
        ).apply {
            this.attachments = attachments.toMutableList()
        }

        val savedMessage = ticketMessageRepository.save(message)

        // Update ticket's updatedAt timestamp
        ticket.updatedAt = Instant.now()
        ticketRepository.save(ticket)

        log.debug { "Added message to ticket #${ticket.ticketNumber} by ${author.effectiveName} (${author.id})" }

        return savedMessage
    }

    @Transactional
    override suspend fun addSystemMessage(
        guild: Guild,
        ticket: Ticket,
        content: String
    ): TicketMessage {
        val message = TicketMessage(
            guildId = guild.idLong,
            ticketId = ticket.id!!,
            messageId = "",
            author = NamedReference("0", "System"),
            content = content,
            isSystemMessage = true
        )

        val savedMessage = ticketMessageRepository.save(message)

        // Update ticket's updatedAt timestamp
        ticket.updatedAt = Instant.now()
        ticketRepository.save(ticket)

        log.debug { "Added system message to ticket #${ticket.ticketNumber}: $content" }

        return savedMessage
    }

    override fun getTicket(guildId: Long, ticketNumber: Int): Ticket? {
        return ticketRepository.findByGuildIdAndTicketNumber(guildId, ticketNumber)
    }

    override fun getTicketById(ticketId: Long): Ticket? {
        return ticketRepository.findByIdOrNull(ticketId)
    }

    override fun getUserTickets(guildId: Long, userId: String): List<Ticket> {
        return ticketRepository.findByGuildIdAndUserIdOrderByTicketNumberDesc(guildId, userId)
    }

    override fun getActiveUserTickets(guildId: Long, userId: String): List<Ticket> {
        return ticketRepository.findByGuildIdAndUserIdAndActive(guildId, userId, true)
    }

    override fun getTicketsByStatus(guildId: Long, status: TicketStatus): List<Ticket> {
        return ticketRepository.findByGuildIdAndStatus(guildId, status)
    }

    override fun getStaffTickets(guildId: Long, staffId: String): List<Ticket> {
        return ticketRepository.findByGuildIdAndAssignedStaffId(guildId, staffId)
    }

    override fun getTicketMessages(guildId: Long, ticketId: Long): List<TicketMessage> {
        return ticketMessageRepository.findByGuildIdAndTicketIdOrderByTimestampAsc(guildId, ticketId)
    }

    override fun countActiveUserTickets(guildId: Long, userId: String): Int {
        return ticketRepository.countByGuildIdAndUserIdAndActive(guildId, userId, true)
    }
}
