package ru.driics.sablebot.common.persistence.entity

import jakarta.persistence.*
import jakarta.validation.constraints.Min
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import ru.driics.sablebot.common.model.ModerationActionType
import ru.driics.sablebot.common.persistence.entity.base.BaseEntity

@Entity
@Table(name = "mod_action")
class ModerationAction : BaseEntity() {

    @ManyToOne(
        cascade = [
            CascadeType.REFRESH,
            CascadeType.DETACH,
            CascadeType.MERGE,
            CascadeType.PERSIST
        ],
    )
    @JoinColumn(name = "config_id")
    var config: ModerationConfig? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    lateinit var type: ModerationActionType

    @field:Min(0)
    @Column(name = "strike_count", nullable = false)
    var strikeCount: Int = 0

    @field:Min(0)
    @Column(nullable = false)
    var duration: Int = 0

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "assign_roles")
    var assignRoles: List<Long>? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "revoke_roles")
    var revokeRoles: List<Long>? = null
}
