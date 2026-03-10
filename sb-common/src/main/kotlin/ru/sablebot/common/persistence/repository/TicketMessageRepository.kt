package ru.sablebot.common.persistence.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import ru.sablebot.common.persistence.entity.TicketMessage
import ru.sablebot.common.persistence.repository.base.GuildRepository

@Repository
interface TicketMessageRepository : GuildRepository<TicketMessage> {

    fun findByGuildIdAndTicketId(guildId: Long, ticketId: Long): List<TicketMessage>

    fun findByGuildIdAndTicketIdOrderByTimestampAsc(guildId: Long, ticketId: Long): List<TicketMessage>

    fun findByGuildIdAndTicketIdOrderByTimestampDesc(guildId: Long, ticketId: Long): List<TicketMessage>

    fun findByGuildIdAndTicketId(guildId: Long, ticketId: Long, pageable: Pageable): Page<TicketMessage>

    fun countByGuildIdAndTicketId(guildId: Long, ticketId: Long): Long

    fun findByGuildIdAndMessageId(guildId: Long, messageId: String): TicketMessage?
}
