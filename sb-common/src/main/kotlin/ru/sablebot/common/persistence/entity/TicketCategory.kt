package ru.sablebot.common.persistence.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import ru.sablebot.common.persistence.entity.base.BaseEntity

@Entity
@Table(
    name = "ticket_category",
    indexes = [
        Index(name = "idx_ticket_category_config", columnList = "config_id")
    ]
)
class TicketCategory(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id", nullable = false)
    var config: TicketConfig? = null,

    @Column(name = "name", nullable = false)
    var name: String = "",

    @Column(name = "description")
    var description: String? = null,

    @Column(name = "emoji")
    var emoji: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "staff_role_ids", columnDefinition = "JSONB")
    var staffRoleIds: MutableList<String> = mutableListOf(),

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0
) : BaseEntity()
