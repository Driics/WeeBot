package ru.sablebot.common.persistence.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import ru.sablebot.common.persistence.entity.base.GuildEntity

@Entity
@Table(name = "member")
class LocalMember(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    var user: LocalUser? = null,

    @Column(name = "effective_name")
    var effectiveName: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "last_known_roles")
    var lastKnownRoles: List<Long>? = null
) : GuildEntity() {

    val asMention: String
        get() = user?.asMention ?: ""
}
