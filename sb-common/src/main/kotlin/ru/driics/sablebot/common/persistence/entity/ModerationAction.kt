package ru.driics.sablebot.common.persistence.entity

import jakarta.persistence.*
import ru.driics.sablebot.common.persistence.entity.base.BaseEntity

@Entity
@Table(name = "mod_action")
class ModerationAction(
    @ManyToOne(cascade = [CascadeType.REFRESH, CascadeType.DETACH, CascadeType.MERGE, CascadeType.PERSIST])
    @JoinColumn(name = "config_id")
    var config: ModerationConfig = ModerationConfig(),
    @Column
    var count: Int = 0,
    @Column
    var duration: Int = 0,
    //TODO: assignRoles, revokeRoles, ActionType
) : BaseEntity()