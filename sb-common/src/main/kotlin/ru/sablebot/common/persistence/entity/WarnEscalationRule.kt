package ru.sablebot.common.persistence.entity

import jakarta.persistence.*
import ru.sablebot.common.model.ModerationActionType
import ru.sablebot.common.persistence.entity.base.BaseEntity

@Entity
@Table(name = "warn_escalation_rule")
class WarnEscalationRule : BaseEntity() {

    @ManyToOne(
        fetch = FetchType.LAZY,
        cascade = [CascadeType.REFRESH, CascadeType.DETACH, CascadeType.MERGE]
    )
    @JoinColumn(name = "config_id", nullable = false)
    var config: ModerationConfig? = null

    @Column(name = "threshold", nullable = false)
    var threshold: Int = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    lateinit var actionType: ModerationActionType

    @Column(name = "duration")
    var duration: Long? = null
}
