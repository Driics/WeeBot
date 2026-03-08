package ru.sablebot.common.persistence.entity

import jakarta.persistence.*
import ru.sablebot.common.model.ReactionRoleMenuType
import ru.sablebot.common.persistence.entity.base.GuildEntity
import java.time.Instant

@Entity
@Table(
    name = "reaction_role_menu",
    indexes = [
        Index(name = "idx_reaction_role_menu_guild", columnList = "guild_id"),
        Index(name = "idx_reaction_role_menu_message", columnList = "channel_id, message_id")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_reaction_role_menu_message", columnNames = ["channel_id", "message_id"])
    ]
)
class ReactionRoleMenu : GuildEntity() {

    @Column(name = "channel_id", length = 21, nullable = false)
    var channelId: String = ""

    @Column(name = "message_id", length = 21, nullable = false)
    var messageId: String = ""

    @Column(name = "title", nullable = false)
    var title: String = ""

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "menu_type", nullable = false)
    lateinit var menuType: ReactionRoleMenuType

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at")
    var updatedAt: Instant? = null

    @Column(name = "active", nullable = false)
    var active: Boolean = true
}
