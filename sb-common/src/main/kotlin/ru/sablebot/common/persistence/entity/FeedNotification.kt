package ru.sablebot.common.persistence.entity

import jakarta.persistence.*
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import ru.sablebot.common.persistence.entity.base.BaseEntity
import java.time.Instant

@Entity
@Table(
    name = "feed_notification",
    indexes = [
        Index(name = "idx_feed_notification_feed_id", columnList = "feed_id"),
        Index(name = "idx_feed_notification_guild_id", columnList = "guild_id"),
        Index(name = "idx_feed_notification_external_item", columnList = "feed_id, external_item_id")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_feed_notification_feed_external", columnNames = ["feed_id", "external_item_id"])
    ]
)
open class FeedNotification(

    @Column(name = "feed_id", nullable = false)
    open var feedId: Long = 0,

    @field:NotEmpty
    @field:Size(max = 500)
    @Column(name = "external_item_id", nullable = false, length = 500)
    open var externalItemId: String = "",

    @field:NotNull
    @Column(name = "sent_at", nullable = false)
    open var sentAt: Instant = Instant.now(),

    @Column(name = "guild_id", nullable = false)
    open var guildId: Long = 0

) : BaseEntity() {
    constructor() : this(feedId = 0, externalItemId = "", sentAt = Instant.now(), guildId = 0)
}
