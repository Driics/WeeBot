package ru.sablebot.common.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import ru.sablebot.common.persistence.entity.base.GuildEntity

@Entity
@Table(name = "music_config")
class MusicConfig(
    guildId: Long = 0L
) : GuildEntity() {

    init {
        this.guildId = guildId
    }

    @Column(name = "channel_id")
    var channelId: Long? = null

    @Column(name = "text_channel_id")
    var textChannelId: Long? = null

    @Column(name = "playlist_enabled")
    var playlistEnabled: Boolean? = null

    @Column(name = "auto_play")
    var autoPlay: String? = null

    @Column(name = "streams_enabled")
    var streamsEnabled: Boolean = false

    @Column(name = "user_join_enabled")
    var userJoinEnabled: Boolean = false

    @Column(name = "queue_limit")
    var queueLimit: Long? = null

    @Column(name = "duration_limit")
    var durationLimit: Long? = null

    @Column(name = "duplicate_limit")
    var duplicateLimit: Long? = null

    @Column(name = "voice_volume")
    var voiceVolume: Int = 0

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    var roles: List<Long>? = null

    @Column(name = "show_queue")
    var showQueue: Boolean = false

    @Column(name = "remove_messages")
    var removeMessages: Boolean = false

    @Column(name = "auto_refresh")
    var autoRefresh: Boolean = false
}
