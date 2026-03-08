package ru.sablebot.common.persistence.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import ru.sablebot.common.model.AutoModActionType
import ru.sablebot.common.model.LinkFilterMode
import ru.sablebot.common.persistence.entity.base.GuildEntity

@Entity
@Table(name = "automod_config")
class AutoModConfig(
    // Anti-Spam
    @Column(name = "anti_spam_enabled", nullable = false)
    var antiSpamEnabled: Boolean = false,

    @Column(name = "anti_spam_max_messages", nullable = false)
    var antiSpamMaxMessages: Int = 5,

    @Column(name = "anti_spam_window_seconds", nullable = false)
    var antiSpamWindowSeconds: Int = 5,

    @Enumerated(EnumType.STRING)
    @Column(name = "anti_spam_action", nullable = false)
    var antiSpamAction: AutoModActionType = AutoModActionType.MUTE,

    @Column(name = "anti_spam_mute_duration")
    var antiSpamMuteDuration: Long? = 300000L,

    // Anti-Raid
    @Column(name = "anti_raid_enabled", nullable = false)
    var antiRaidEnabled: Boolean = false,

    @Column(name = "anti_raid_join_threshold", nullable = false)
    var antiRaidJoinThreshold: Int = 10,

    @Column(name = "anti_raid_window_seconds", nullable = false)
    var antiRaidWindowSeconds: Int = 10,

    @Column(name = "anti_raid_min_account_age_days", nullable = false)
    var antiRaidMinAccountAgeDays: Int = 7,

    @Enumerated(EnumType.STRING)
    @Column(name = "anti_raid_action", nullable = false)
    var antiRaidAction: AutoModActionType = AutoModActionType.KICK,

    // Word Filter
    @Column(name = "word_filter_enabled", nullable = false)
    var wordFilterEnabled: Boolean = false,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "word_filter_patterns", columnDefinition = "JSONB")
    var wordFilterPatterns: MutableList<String> = mutableListOf(),

    @Enumerated(EnumType.STRING)
    @Column(name = "word_filter_action", nullable = false)
    var wordFilterAction: AutoModActionType = AutoModActionType.DELETE,

    // Link Filter
    @Column(name = "link_filter_enabled", nullable = false)
    var linkFilterEnabled: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(name = "link_filter_mode", nullable = false)
    var linkFilterMode: LinkFilterMode = LinkFilterMode.BLACKLIST,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "link_filter_domains", columnDefinition = "JSONB")
    var linkFilterDomains: MutableList<String> = mutableListOf(),

    @Enumerated(EnumType.STRING)
    @Column(name = "link_filter_action", nullable = false)
    var linkFilterAction: AutoModActionType = AutoModActionType.DELETE,

    // Mention Spam
    @Column(name = "mention_spam_enabled", nullable = false)
    var mentionSpamEnabled: Boolean = false,

    @Column(name = "mention_spam_threshold", nullable = false)
    var mentionSpamThreshold: Int = 5,

    @Enumerated(EnumType.STRING)
    @Column(name = "mention_spam_action", nullable = false)
    var mentionSpamAction: AutoModActionType = AutoModActionType.WARN,

    // General
    @Column(name = "dm_on_action", nullable = false)
    var dmOnAction: Boolean = false
) : GuildEntity() {
    constructor(guildId: Long) : this() {
        this.guildId = guildId
    }
}
