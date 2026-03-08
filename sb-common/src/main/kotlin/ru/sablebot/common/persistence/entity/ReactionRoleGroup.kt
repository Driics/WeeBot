package ru.sablebot.common.persistence.entity

import jakarta.persistence.*
import ru.sablebot.common.persistence.entity.base.GuildEntity
import java.time.Instant

@Entity
@Table(
    name = "reaction_role_group",
    indexes = [
        Index(name = "idx_reaction_role_group_guild", columnList = "guild_id")
    ]
)
class ReactionRoleGroup : GuildEntity() {

    @Column(name = "name", nullable = false)
    var name: String = ""

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at")
    var updatedAt: Instant? = null

    @Column(name = "active", nullable = false)
    var active: Boolean = true
}
