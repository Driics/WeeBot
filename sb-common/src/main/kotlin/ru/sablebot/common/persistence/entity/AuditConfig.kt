package ru.sablebot.common.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import ru.sablebot.common.model.AuditActionType
import ru.sablebot.common.persistence.entity.base.GuildEntity

@Entity
@Table(name = "audit_config")
open class AuditConfig(
    @Column
    open var enabled: Boolean = false,

    @Column
    open var forwardEnabled: Boolean = false,

    @Column
    open var forwardChannelId: Long = 0L,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "forward_actions")
    open var forwardActions: List<AuditActionType> = emptyList(),
) : GuildEntity() {
    constructor(guildId: Long) : this() {
        this.guildId = guildId
    }
}