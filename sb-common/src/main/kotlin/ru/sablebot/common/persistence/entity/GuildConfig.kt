package ru.sablebot.common.persistence.entity

import jakarta.persistence.Basic
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import ru.sablebot.common.model.InVoiceLink
import ru.sablebot.common.persistence.entity.base.GuildEntity

@Entity
@Table(name = "guild_config")
open class GuildConfig(

    @field:Size(max = 100)
    @Basic
    open var name: String? = null,

    @field:Size(max = 7)
    @Basic
    open var color: String? = null,

    @Column(name = "icon_url")
    open var iconUrl: String? = null,

    @field:NotEmpty
    @field:Size(max = 20)
    @Basic
    open var prefix: String = "",

    @Column(name = "is_help_private")
    open var privateHelp: Boolean? = null,

    @field:NotEmpty
    @field:Size(max = 10)
    @Basic
    open var locale: String = "",

    @field:NotEmpty
    @field:Size(max = 10)
    @Column(name = "command_locale")
    open var commandLocale: String = "",

    @Column(name = "time_zone")
    open var timeZone: String? = null,

    @Deprecated("No longer used")
    @Column(name = "is_assistant_enabled")
    open var assistantEnabled: Boolean = false,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    open var voiceLinks: List<InVoiceLink>? = null

) : GuildEntity() {
    constructor(guildId: Long) : this() {
        this.guildId = guildId
    }
}

