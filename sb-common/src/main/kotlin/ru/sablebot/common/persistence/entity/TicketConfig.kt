package ru.sablebot.common.persistence.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import ru.sablebot.common.persistence.entity.base.GuildEntity

@Entity
@Table(name = "ticket_config")
class TicketConfig(
    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = false,

    @Column(name = "support_channel_id", length = 21)
    var supportChannelId: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "staff_role_ids", columnDefinition = "JSONB")
    var staffRoleIds: MutableList<String> = mutableListOf(),

    @Column(name = "category_channel_id", length = 21)
    var categoryChannelId: String? = null,

    @Column(name = "transcript_channel_id", length = 21)
    var transcriptChannelId: String? = null,

    @Column(name = "max_tickets_per_user", nullable = false)
    var maxTicketsPerUser: Int = 1,

    @Column(name = "auto_close_inactive_days")
    var autoCloseInactiveDays: Int? = null,

    @Column(name = "dm_on_close", nullable = false)
    var dmOnClose: Boolean = true,

    @OneToMany(
        mappedBy = "config",
        cascade = [CascadeType.ALL],
        fetch = FetchType.LAZY,
        orphanRemoval = true
    )
    var categories: MutableList<TicketCategory> = mutableListOf()
) : GuildEntity() {
    constructor(guildId: Long) : this() {
        this.guildId = guildId
    }
}
