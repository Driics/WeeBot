package ru.driics.sablebot.common.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import ru.driics.sablebot.common.model.AuditActionType
import ru.driics.sablebot.common.persistence.entity.base.GuildEntity

@Entity
@Table(name = "audit_config")
open class AuditConfig(
    @Column
    var enabled: Boolean = false,

    @Column
    var forwardEnabled: Boolean = false,

    @Column
    var forwardChannelId: Long? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "forward_actions")
    var forwardActions: List<AuditActionType> = emptyList(),
) : GuildEntity() {
    constructor(guildId: Long) : this() {
        this.guildId = guildId
    }
}