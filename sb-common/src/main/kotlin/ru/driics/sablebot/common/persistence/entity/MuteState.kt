package ru.driics.sablebot.common.persistence.entity

import jakarta.persistence.*
import ru.driics.sablebot.common.persistence.entity.base.MemberEntity
import java.time.Instant

@Entity
@Table(name = "mute_state")
class MuteState constructor(
    userId: String,
    guildId: Long,
    @Column
    val isGlobal: Boolean = false,
    @Column(name = "channel_id")
    val channelId: String? = null,
    @Column
    val reason: String? = "",
    @Column
    var expire: Instant = Instant.now(),
) : MemberEntity(userId) {

    init {
        this.guildId = guildId
    }
}
