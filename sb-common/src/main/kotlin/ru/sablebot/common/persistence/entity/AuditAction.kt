package ru.sablebot.common.persistence.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import ru.sablebot.common.model.AuditActionType
import ru.sablebot.common.persistence.entity.base.GuildEntity
import ru.sablebot.common.persistence.entity.base.NamedReference
import java.time.Instant

@Entity
@Table(
    name = "audit_action",
    indexes = [
        Index(name = "idx_audit_action_guild_date", columnList = "guild_id,action_date"),
        Index(name = "idx_audit_action_type", columnList = "action_type")
    ]
)
class AuditAction(

    @Column(name = "action_date", nullable = false)
    var actionDate: Instant = Instant.now(),

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    var actionType: AuditActionType,

    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "id", column = Column(name = "source_user_id", length = 21)),
        AttributeOverride(name = "name", column = Column(name = "source_user_name"))
    )
    var user: NamedReference = NamedReference(),

    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "id", column = Column(name = "target_user_id", length = 21)),
        AttributeOverride(name = "name", column = Column(name = "target_user_name"))
    )
    var targetUser: NamedReference = NamedReference(),

    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "id", column = Column(name = "channel_id", length = 21)),
        AttributeOverride(name = "name", column = Column(name = "channel_name"))
    )
    var channel: NamedReference = NamedReference(),

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var attributes: MutableMap<String, Any?> = mutableMapOf()

) : GuildEntity() {

    constructor(guildId: Long, actionType: AuditActionType) : this(
        actionDate = Instant.now(),
        actionType = actionType,
        user = NamedReference(),
        targetUser = NamedReference(),
        channel = NamedReference(),
        attributes = mutableMapOf()
    ) {
        this.guildId = guildId
    }

    @Transient
    fun <T> getAttribute(key: String, type: Class<T>): T? {
        val value = attributes[key] ?: return null
        return if (type.isInstance(value)) type.cast(value) else null
    }
}
