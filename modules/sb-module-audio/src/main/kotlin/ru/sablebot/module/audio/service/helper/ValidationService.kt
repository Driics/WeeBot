package ru.sablebot.module.audio.service.helper

import net.dv8tion.jda.api.entities.Member
import org.springframework.stereotype.Service
import ru.sablebot.common.model.exception.ValidationException
import ru.sablebot.common.persistence.entity.MusicConfig
import ru.sablebot.common.service.MusicConfigService
import ru.sablebot.module.audio.model.PlaybackInstance
import ru.sablebot.module.audio.model.TrackRequest
import ru.sablebot.module.audio.service.PlayerServiceV4
import java.util.*

@Service
class ValidationService(
    private val playerService: PlayerServiceV4,
    private val musicConfigService: MusicConfigService
) {
    private fun sameTrack(
        a: TrackRequest,
        b: TrackRequest,
    ): Boolean {
        return Objects.equals(a.uri, b.uri) &&
                Objects.equals(a.title, b.title) &&
                Objects.equals(a.lengthMs, b.lengthMs) &&
                Objects.equals(a.isStream, b.isStream) &&
                (a.encodedTrack == b.encodedTrack || aEncodedId(a) == aEncodedId(b))
    }

    private fun aEncodedId(r: TrackRequest): String? = r.uri ?: r.title

    @Throws(ValidationException::class)
    fun validateSingle(track: TrackRequest, requestedBy: Member) {
        val cfg: MusicConfig? = musicConfigService.get(requestedBy.guild)
        val queueLimit: Long? = cfg?.queueLimit
        val durationLimitMin: Long? = cfg?.durationLimit
        val duplicateLimit: Long? = cfg?.duplicateLimit

        val instance: PlaybackInstance? = playerService.get(requestedBy.guild)

        if (track.isStream == true && cfg == null) {
            throw ValidationException("discord.command.audio.queue.limits.streams")
        }

        if (instance != null) {
            if (queueLimit != null) {
                val userQueue = instance.queueSnapshot(requestedBy)
                if (userQueue.size >= queueLimit) {
                    throw ValidationException("discord.command.audio.queue.limits.items", queueLimit)
                }
            }

            if (duplicateLimit != null) {
                val duplicates = instance.queueSnapshot()
                    .count { queued -> sameTrack(queued, track) }
                if (duplicates >= duplicateLimit) {
                    throw ValidationException("discord.command.audio.queue.limits.duplicates", duplicateLimit)
                }
            }
        }

        if (track.isStream != true && durationLimitMin != null && (track.lengthMs ?: 0) >= durationLimitMin * 60_000) {
            throw ValidationException("discord.command.audio.queue.limits.duration", durationLimitMin)
        }
    }

    @Throws(ValidationException::class)
    fun filterPlaylist(
        tracks: List<TrackRequest>,
        requestedBy: Member,
        playlistRequested: Boolean // аналог playlistEnabled
    ): List<TrackRequest> {

        val cfg: MusicConfig? = musicConfigService.get(requestedBy.guild)
        val queueLimit: Long? = cfg?.queueLimit
        val durationLimitMin: Long? = cfg?.durationLimit
        val duplicateLimit: Long? = cfg?.duplicateLimit

        val instance: PlaybackInstance? = playerService.get(requestedBy.guild)

        if (!playlistRequested || cfg == null || cfg.playlistEnabled != true) {
            throw ValidationException("discord.command.audio.queue.limits.playlists")
        }

        var filtered = tracks

        // streams filter[web:2]
        if (filtered.isNotEmpty() && cfg.isStreamsEnabled.not()) {
            filtered = filtered.filter { it.isStream != true }
            if (filtered.isEmpty()) {
                throw ValidationException("discord.command.audio.queue.limits.streams")
            }
        }

        // duration filter[web:2]
        if (filtered.isNotEmpty() && durationLimitMin != null) {
            val maxMs = durationLimitMin * 60_000
            filtered = filtered.filter { it.isStream == true || (it.lengthMs ?: 0) < maxMs }
            if (filtered.isEmpty()) {
                throw ValidationException("discord.command.audio.queue.limits.duration.playlist", durationLimitMin)
            }
        }

        // duplicates filter vs current queue
        if (instance != null && filtered.isNotEmpty() && duplicateLimit != null) {
            val queue = instance.queueSnapshot()
            filtered = filtered.filter { candidate ->
                queue.count { queued -> sameTrack(queued, candidate) } < duplicateLimit
            }
            if (filtered.isEmpty()) {
                throw ValidationException("discord.command.audio.queue.limits.duplicates.playlist", duplicateLimit)
            }
        }

        if (filtered.isEmpty()) {
            throw ValidationException("discord.command.audio.queue.limits.playlistEmpty")
        }

        // queue slots per user
        if (instance != null && queueLimit != null) {
            val userQueue = instance.queueSnapshot(requestedBy)
            val availableSlots = queueLimit.toInt() - userQueue.size
            if (availableSlots <= 0) {
                throw ValidationException("discord.command.audio.queue.limits.items", queueLimit)
            }
            if (filtered.size > availableSlots) {
                filtered = filtered.take(availableSlots)
            }
        }

        return filtered
    }
}