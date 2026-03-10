package ru.sablebot.common.persistence.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import ru.sablebot.common.persistence.entity.base.GuildEntity
import ru.sablebot.common.persistence.entity.base.NamedReference
import java.time.Instant

@Entity
@Table(
    name = "ticket_message",
    indexes = [
        Index(name = "idx_ticket_message_ticket", columnList = "ticket_id"),
        Index(name = "idx_ticket_message_timestamp", columnList = "timestamp"),
        Index(name = "idx_ticket_message_guild_ticket", columnList = "guild_id,ticket_id")
    ]
)
class TicketMessage(

    @Column(name = "message_id", length = 21, nullable = false)
    var messageId: String = "",

    @Column(name = "ticket_id", nullable = false)
    var ticketId: Long = 0,

    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "id", column = Column(name = "author_id", length = 21)),
        AttributeOverride(name = "name", column = Column(name = "author_name"))
    )
    var author: NamedReference = NamedReference(),

    @Column(name = "content", columnDefinition = "TEXT")
    var content: String = "",

    @Column(name = "timestamp", nullable = false)
    var timestamp: Instant = Instant.now(),

    @Column(name = "is_system_message", nullable = false)
    var isSystemMessage: Boolean = false,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attachments", columnDefinition = "jsonb")
    var attachments: MutableList<Map<String, String>> = mutableListOf()

) : GuildEntity() {

    constructor(
        guildId: Long,
        ticketId: Long,
        messageId: String,
        author: NamedReference,
        content: String,
        isSystemMessage: Boolean = false
    ) : this(
        messageId = messageId,
        ticketId = ticketId,
        author = author,
        content = content,
        timestamp = Instant.now(),
        isSystemMessage = isSystemMessage,
        attachments = mutableListOf()
    ) {
        this.guildId = guildId
    }
}
