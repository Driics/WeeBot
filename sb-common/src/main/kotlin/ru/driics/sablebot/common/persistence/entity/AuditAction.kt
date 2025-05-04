package ru.driics.sablebot.common.persistence.entity

import jakarta.persistence.*
import ru.driics.sablebot.common.persistence.entity.base.GuildEntity
import ru.driics.sablebot.common.persistence.entity.base.NamedReference
import java.util.*

@Entity
@Table(name = "audit_action")
class AuditAction(
    @Column(name = "action_date")
    @Temporal(TemporalType.TIMESTAMP)
    var actionDate: Date = Date(),
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
) : GuildEntity() {
    constructor(guildId: Long) : this() {
        this.guildId = guildId
    }
}