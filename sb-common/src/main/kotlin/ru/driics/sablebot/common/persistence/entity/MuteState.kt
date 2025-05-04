package ru.driics.sablebot.common.persistence.entity

import jakarta.persistence.*
import ru.driics.sablebot.common.persistence.entity.base.MemberEntity
import java.util.*

@Entity
@Table(name = "mute_state")
class MuteState(
    @Column
    val global: Boolean = false,
    @Column(name = "channel_id")
    val channelId: String = "",
    @Column
    val reason: String = "",
    @Column
    @Temporal(TemporalType.TIMESTAMP)
    val expire: Date = Date(),
) : MemberEntity()