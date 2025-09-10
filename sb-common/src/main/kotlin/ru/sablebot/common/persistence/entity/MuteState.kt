package ru.sablebot.common.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import ru.sablebot.common.persistence.entity.base.MemberEntity
import java.time.Instant

@Entity
@Table(
    name = "mute_state",
    indexes = [
        Index(name = "idx_mute_guild_user", columnList = "guild_id,user_id"),
        Index(name = "idx_mute_guild_user_channel", columnList = "guild_id,user_id,channel_id")
    ]
)
class MuteState @JvmOverloads constructor(
    userId: String = "",
    guildId: Long = 0,
    @Column
    var isGlobal: Boolean = false,
    @Column(name = "channel_id")
    var channelId: String? = null,
    @Column(nullable = false)
    var reason: String = "",
    @Column
    var expire: Instant = Instant.now(),
) : MemberEntity(userId) {

    init {
        this.guildId = guildId
    }
}
