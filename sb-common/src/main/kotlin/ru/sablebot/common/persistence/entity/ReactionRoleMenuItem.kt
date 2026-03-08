package ru.sablebot.common.persistence.entity

import jakarta.persistence.*
import ru.sablebot.common.persistence.entity.base.GuildEntity

@Entity
@Table(
    name = "reaction_role_menu_item",
    indexes = [
        Index(name = "idx_reaction_role_menu_item_menu", columnList = "menu_id"),
        Index(name = "idx_reaction_role_menu_item_guild", columnList = "guild_id"),
        Index(name = "idx_reaction_role_menu_item_group", columnList = "group_id")
    ]
)
class ReactionRoleMenuItem : GuildEntity() {

    @Column(name = "menu_id", nullable = false)
    var menuId: Long = 0

    @Column(name = "group_id")
    var groupId: Long? = null

    @Column(name = "role_id", length = 21, nullable = false)
    var roleId: String = ""

    @Column(name = "emoji", length = 100)
    var emoji: String? = null

    @Column(name = "label", nullable = false)
    var label: String = ""

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0

    @Column(name = "toggleable", nullable = false)
    var toggleable: Boolean = true

    @Column(name = "required_role_ids", columnDefinition = "TEXT")
    var requiredRoleIds: String? = null

    @Column(name = "active", nullable = false)
    var active: Boolean = true
}
