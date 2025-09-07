package ru.driics.sablebot.common.persistence.entity

import jakarta.persistence.*
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
        ]
    )
    @JoinColumn(name = "config_id")
    var config: ModerationConfig? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    lateinit var type: ModerationActionType

    @Column
    var count: Int = 0

    @Column
    var duration: Int = 0

    @Column(name = "assign_roles", columnDefinition = "jsonb")
    var assignRoles: List<Long>? = null

    @Column(name = "revoke_roles", columnDefinition = "jsonb")
    var revokeRoles: List<Long>? = null
}
