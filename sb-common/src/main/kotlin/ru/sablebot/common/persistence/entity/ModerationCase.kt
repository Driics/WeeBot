package ru.sablebot.common.persistence.entity

import jakarta.persistence.*
import ru.sablebot.common.model.ModerationCaseType
import ru.sablebot.common.persistence.entity.base.GuildEntity
import java.time.Instant

@Entity
@Table(
    name = "moderation_case",
    indexes = [
        Index(name = "idx_moderation_case_guild_target", columnList = "guild_id, target_id")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_moderation_case_guild_case_number", columnNames = ["guild_id", "case_number"])
    ]
)
class ModerationCase : GuildEntity() {

    @Column(name = "case_number", nullable = false)
    var caseNumber: Int = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    lateinit var actionType: ModerationCaseType

    @Column(name = "moderator_id", length = 21, nullable = false)
    var moderatorId: String = ""

    @Column(name = "moderator_name", nullable = false)
    var moderatorName: String = ""

    @Column(name = "target_id", length = 21, nullable = false)
    var targetId: String = ""

    @Column(name = "target_name", nullable = false)
    var targetName: String = ""

    @Column(name = "reason", columnDefinition = "TEXT")
    var reason: String? = null

    @Column(name = "duration")
    var duration: Long? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "active", nullable = false)
    var active: Boolean = true
}
