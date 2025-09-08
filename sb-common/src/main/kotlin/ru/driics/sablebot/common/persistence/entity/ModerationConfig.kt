package ru.driics.sablebot.common.persistence.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import ru.driics.sablebot.common.persistence.entity.base.GuildEntity

@Entity
@Table(name = "mod_config")
class ModerationConfig(
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    var roles: List<Long> = emptyList(),
    @Column(name = "public_colors")
    var publicColors: Boolean = false,
    @Column(name = "muted_role_id")
    var mutedRoleId: Long = 0,
    @Column(name = "cooldown_ignored")
    var coolDownIgnored: Boolean = false,
    @OneToMany(
        mappedBy = "config",
        cascade = [CascadeType.ALL],
        fetch = FetchType.EAGER,
        orphanRemoval = true)
    @OrderBy("count")
    var actions: MutableList<ModerationAction> = mutableListOf()
) : GuildEntity() {
    constructor(guildId: Long) : this() {
        this.guildId = guildId
    }
}