package ru.driics.sablebot.common.persistence.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.Type
import org.hibernate.type.SqlTypes
import ru.driics.sablebot.common.persistence.entity.base.GuildEntity

@Entity
@Table(name = "mod_config")
class ModerationConfig(
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    val roles: List<Long> = emptyList(),
    @Column(name = "public_colors")
    val publicColors: Boolean = false,
    @Column(name = "muted_role_id")
    val mutedRoleId: Long = 0,
    @Column(name = "cooldown_ignored")
    val coolDownIgnored: Boolean = false,
    @OneToMany(mappedBy = "config", cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    @OrderBy("count")
    val actions: List<ModerationAction> = emptyList()
) : GuildEntity() {
    constructor(guildId: Long) : this() {
        this.guildId = guildId
    }
}