package ru.sablebot.module.audio.model

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel

data class TrackRequest(
    val encodedTrack: String,      // base64 из Lavalink Track.encoded[web:2]
    val jda: JDA,
    val guildId: Long,
    val channelId: Long,
    val memberId: Long,

    var endMemberId: Long? = null,
    var resetMessage: Boolean = false,
    var resetOnResume: Boolean = false,
    var endReason: EndReason? = null,
    var timeCode: Long? = null,

    // опционально: мета для UI (title/uri/length), чтобы не дергать decodetrack
    val identifier: String? = null,
    val title: String? = null,
    val author: String? = null,
    val uri: String? = null,
    val sourceName: String? = null,
    val lengthMs: Long? = null,
    val isStream: Boolean = false,
    val isSeekable: Boolean? = null,
    val artworkUrl: String? = null,
    val isrc: String? = null
) {
    fun reset() {
        endReason = null
        endMemberId = null
        // В v4 encodedTrack уже “готовый”, клонить как AudioTrack не нужно.[web:2]
    }

    fun channel(): TextChannel? = jda.getTextChannelById(channelId)

    fun member(): Member? {
        val guild: Guild = jda.getGuildById(guildId) ?: return null
        return guild.getMemberById(memberId)
    }
}
