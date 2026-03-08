package ru.sablebot.common.persistence.entity

import jakarta.persistence.*
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import ru.sablebot.common.persistence.entity.base.GuildEntity
import java.time.Instant

@Entity
@Table(name = "social_feed")
open class SocialFeed(

    @field:NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "feed_type", nullable = false, length = 20)
    open var feedType: FeedType = FeedType.REDDIT,

    @field:NotEmpty
    @field:Size(max = 255)
    @Column(name = "target_identifier", nullable = false)
    open var targetIdentifier: String = "",

    @Column(name = "target_channel_id", nullable = false)
    open var targetChannelId: Long = 0,

    @field:Positive
    @Column(name = "check_interval_minutes", nullable = false)
    open var checkIntervalMinutes: Int = 15,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "embed_config", columnDefinition = "json")
    open var embedConfig: Map<String, Any>? = null,

    @Column(name = "enabled", nullable = false)
    open var enabled: Boolean = true,

    @Column(name = "last_check_time")
    open var lastCheckTime: Instant? = null,

    @field:Size(max = 255)
    @Column(name = "last_item_id")
    open var lastItemId: String? = null

) : GuildEntity() {
    constructor(guildId: Long) : this() {
        this.guildId = guildId
    }
}

enum class FeedType {
    REDDIT,
    TWITCH,
    YOUTUBE
}
