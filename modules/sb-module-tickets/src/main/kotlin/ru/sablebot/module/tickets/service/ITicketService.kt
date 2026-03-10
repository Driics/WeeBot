package ru.sablebot.module.tickets.service

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import ru.sablebot.common.model.TicketStatus
import ru.sablebot.common.persistence.entity.Ticket
import ru.sablebot.common.persistence.entity.TicketMessage

interface ITicketService {
    suspend fun createTicket(guild: Guild, user: User, subject: String, category: String?, initialMessage: String?): Ticket
    suspend fun closeTicket(guild: Guild, ticket: Ticket, closedBy: Member, reason: String?): Ticket
    suspend fun reopenTicket(guild: Guild, ticket: Ticket, reopenedBy: Member): Ticket
    suspend fun claimTicket(guild: Guild, ticket: Ticket, staff: Member): Ticket
    suspend fun unclaimTicket(guild: Guild, ticket: Ticket, staff: Member): Ticket
    suspend fun addMessage(guild: Guild, ticket: Ticket, author: User, content: String, messageId: String, attachments: List<Map<String, String>> = emptyList()): TicketMessage
    suspend fun addSystemMessage(guild: Guild, ticket: Ticket, content: String): TicketMessage
    fun getTicket(guildId: Long, ticketNumber: Int): Ticket?
    fun getTicketById(ticketId: Long): Ticket?
    fun getUserTickets(guildId: Long, userId: String): List<Ticket>
    fun getActiveUserTickets(guildId: Long, userId: String): List<Ticket>
    fun getTicketsByStatus(guildId: Long, status: TicketStatus): List<Ticket>
    fun getStaffTickets(guildId: Long, staffId: String): List<Ticket>
    fun getTicketMessages(guildId: Long, ticketId: Long): List<TicketMessage>
    fun countActiveUserTickets(guildId: Long, userId: String): Int
}
