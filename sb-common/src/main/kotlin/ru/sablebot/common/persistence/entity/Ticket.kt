package ru.sablebot.common.persistence.entity

import jakarta.persistence.*
import ru.sablebot.common.model.TicketStatus
import ru.sablebot.common.persistence.entity.base.GuildEntity
import java.time.Instant

@Entity
@Table(
    name = "ticket",
    indexes = [
        Index(name = "idx_ticket_guild_user", columnList = "guild_id, user_id"),
        Index(name = "idx_ticket_guild_status", columnList = "guild_id, status"),
        Index(name = "idx_ticket_assigned_staff", columnList = "assigned_staff_id")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_ticket_guild_ticket_number", columnNames = ["guild_id", "ticket_number"])
    ]
)
class Ticket : GuildEntity() {

    @Column(name = "ticket_number", nullable = false)
    var ticketNumber: Int = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    lateinit var status: TicketStatus

    @Column(name = "user_id", length = 21, nullable = false)
    var userId: String = ""

    @Column(name = "user_name", nullable = false)
    var userName: String = ""

    @Column(name = "assigned_staff_id", length = 21)
    var assignedStaffId: String? = null

    @Column(name = "assigned_staff_name")
    var assignedStaffName: String? = null

    @Column(name = "category")
    var category: String? = null

    @Column(name = "subject", nullable = false)
    var subject: String = ""

    @Column(name = "channel_id", length = 21, nullable = false)
    var channelId: String = ""

    @Column(name = "thread_id", length = 21)
    var threadId: String? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    @Column(name = "closed_at")
    var closedAt: Instant? = null

    @Column(name = "active", nullable = false)
    var active: Boolean = true
}
