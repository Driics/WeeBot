package ru.sablebot.common.persistence.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import ru.sablebot.common.model.TicketStatus
import ru.sablebot.common.persistence.entity.Ticket
import ru.sablebot.common.persistence.repository.base.GuildRepository

@Repository
interface TicketRepository : GuildRepository<Ticket> {

    @Query("SELECT COALESCE(MAX(t.ticketNumber), 0) FROM Ticket t WHERE t.guildId = :guildId")
    fun findMaxTicketNumber(guildId: Long): Int

    fun findByGuildIdAndTicketNumber(guildId: Long, ticketNumber: Int): Ticket?

    fun findByGuildIdAndUserId(guildId: Long, userId: String): List<Ticket>

    fun findByGuildIdAndUserIdOrderByTicketNumberDesc(guildId: Long, userId: String): List<Ticket>

    fun findByGuildIdAndUserIdAndActive(guildId: Long, userId: String, active: Boolean): List<Ticket>

    fun countByGuildIdAndUserIdAndActive(guildId: Long, userId: String, active: Boolean): Int

    fun findByGuildIdAndStatus(guildId: Long, status: TicketStatus): List<Ticket>

    fun findByGuildIdAndAssignedStaffId(guildId: Long, assignedStaffId: String): List<Ticket>

    fun findByGuildIdAndStatusAndActive(guildId: Long, status: TicketStatus, active: Boolean): List<Ticket>

    fun findByGuildId(guildId: Long, pageable: Pageable): Page<Ticket>

    fun findByGuildIdAndStatus(guildId: Long, status: TicketStatus, pageable: Pageable): Page<Ticket>

    fun findAllByGuildId(guildId: Long): List<Ticket>
}
