package ru.driics.sablebot.common.persistence.entity

import jakarta.persistence.*
import ru.driics.sablebot.common.persistence.entity.base.GuildEntity

@Entity
@Table(name = "member")
class LocalMember(
    @ManyToOne
    @JoinColumn(name = "user_id")
    var user: LocalUser? = null,

    @Column(name = "effective_name")
    var effectiveName: String? = null,

    @Column(name = "last_known_roles", columnDefinition = "jsonb")
    var lastKnownRoles: List<Long>? = null
) : GuildEntity() {

    val asMention: String
        get() = if (effectiveName.isNullOrEmpty()) {
            user?.asMention ?: ""
        } else {
            "<@!${user?.userId}>"
        }
}
