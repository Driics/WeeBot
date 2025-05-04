package ru.driics.sablebot.common.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import ru.driics.sablebot.common.persistence.entity.base.GuildEntity

@Entity
@Table(name = "member")
data class LocalMember(
    @ManyToOne
    var user: LocalUser = LocalUser(),
    @Column
    var effectiveName: String = "",
    // TODO: lastKnownRoles
) : GuildEntity() {
    @Transient
    val asMention: String = if (effectiveName.isEmpty()) user.asMention else "<@!${user.userId}>"
}