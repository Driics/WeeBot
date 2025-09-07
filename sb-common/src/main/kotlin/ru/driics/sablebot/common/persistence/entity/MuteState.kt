package ru.driics.sablebot.common.persistence.entity

import jakarta.persistence.*
import ru.driics.sablebot.common.persistence.entity.base.MemberEntity
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Entity
@Table(name = "mute_state")
class MuteState @OptIn(ExperimentalTime::class) constructor(
    userId: String,
    guildId: Long,
    @Column
    val isGlobal: Boolean = false,
    @Column(name = "channel_id")
    val channelId: String? = null,
    @Column
    val reason: String? = "",
    @Column
    @Temporal(TemporalType.TIMESTAMP)
    val expire: Instant = Clock.System.now(),
) : MemberEntity(userId) {

    init {
        this.guildId = guildId
    }
}
