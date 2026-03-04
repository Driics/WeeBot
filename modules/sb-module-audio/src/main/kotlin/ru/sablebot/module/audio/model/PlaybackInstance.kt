package ru.sablebot.module.audio.model

import dev.arbjerg.lavalink.client.player.LavalinkPlayer
import net.dv8tion.jda.api.entities.Member
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class PlaybackInstance(
    val guildId: Long,
    val player: LavalinkPlayer
) {
    private val playlist = Collections.synchronizedList(mutableListOf<TrackRequest>())

    var mode: RepeatMode = RepeatMode.NONE

    var cursor: Int = -1
        private set

    var volume: Int = 100
    var activeFilter: FilterPreset? = null
    var twentyFourSeven: Boolean = false

    var playlistId: Long? = null
    var playlistUuid: String? = null
    var activeTime: Long = System.currentTimeMillis()
        private set

    private val stopped = AtomicBoolean(false)

    @Volatile
    var lastKnownPositionMs: Long = 0
        private set

    @Synchronized
    fun reset() {
        mode = RepeatMode.NONE
        playlist.clear()
        cursor = -1
        stopped.set(false)
        activeFilter = null
        tick()
    }

    @Synchronized
    fun tick() {
        activeTime = System.currentTimeMillis()
    }

    /**
     * Enqueue a track. Returns the track to start if the queue was empty.
     * Network calls (player.play) should be done outside this synchronized block.
     */
    @Synchronized
    fun enqueue(request: TrackRequest): TrackRequest? {
        tick()
        playlist.add(request)

        val shouldStart = (cursor == -1 || cursor >= playlist.size)
        if (shouldStart) {
            mode = RepeatMode.NONE
            cursor = 0
            return playlist[cursor]
        }
        return null
    }

    /**
     * Calculates next track to play based on repeat mode.
     */
    @Synchronized
    fun nextToPlay(): TrackRequest? {
        val current = currentOrNull() ?: return null

        return when (mode) {
            RepeatMode.CURRENT -> {
                current.reset()
                current
            }

            RepeatMode.ALL, RepeatMode.NONE -> {
                if (cursor < playlist.size - 1) {
                    cursor += 1
                    playlist[cursor]
                } else if (mode == RepeatMode.ALL && playlist.isNotEmpty()) {
                    cursor = 0
                    playlist.forEach { it.reset() }
                    playlist[cursor]
                } else null
            }
        }
    }

    @Synchronized
    fun stop(): Boolean = stopped.compareAndSet(false, true)

    @Synchronized
    fun removeByIndex(index: Int): TrackRequest? {
        if (index !in playlist.indices || index == cursor) return null
        val removed = playlist.removeAt(index)
        if (index < cursor) cursor -= 1
        return removed
    }

    @Synchronized
    fun shuffleUpcoming(): Boolean {
        if (playlist.isEmpty()) return false
        val start = cursor + 1
        if (start >= playlist.size) return false

        val upcoming = playlist.subList(start, playlist.size)
        val shuffled = upcoming.toMutableList()
        shuffled.shuffle()
        for (i in shuffled.indices) {
            upcoming[i] = shuffled[i]
        }
        return true
    }

    /**
     * Move a track from one position to another in the queue.
     * Positions are relative to the full playlist (0-indexed).
     */
    @Synchronized
    fun moveTo(from: Int, to: Int): Boolean {
        if (from !in playlist.indices || to !in playlist.indices) return false
        if (from == cursor || to == cursor) return false
        if (from == to) return false

        val track = playlist.removeAt(from)
        playlist.add(to, track)

        // Adjust cursor if it was between the moved positions
        cursor = when {
            from < cursor && to >= cursor -> cursor - 1
            from > cursor && to <= cursor -> cursor + 1
            else -> cursor
        }
        return true
    }

    /**
     * Jump to a specific track index. Returns the track to play or null if invalid.
     */
    @Synchronized
    fun skipTo(index: Int): TrackRequest? {
        if (index !in playlist.indices) return null
        cursor = index
        val track = playlist[cursor]
        track.reset()
        return track
    }

    /**
     * Clear the queue but keep the currently playing track.
     * Returns the number of removed tracks.
     */
    @Synchronized
    fun clear(): Int {
        if (playlist.isEmpty()) return 0
        val current = currentOrNull()
        val removedCount = playlist.size - (if (current != null) 1 else 0)

        if (current != null) {
            playlist.clear()
            playlist.add(current)
            cursor = 0
        } else {
            playlist.clear()
            cursor = -1
        }
        return removedCount
    }

    @Synchronized
    fun currentOrNull(): TrackRequest? =
        if (cursor < 0 || cursor >= playlist.size || playlist.isEmpty()) null else playlist[cursor]

    @Synchronized
    fun queueSnapshot(): List<TrackRequest> {
        val current = currentOrNull()
        val result = ArrayList<TrackRequest>()
        if (current != null) result += current
        result += onGoing()
        return result.toList()
    }

    @Synchronized
    fun queueSnapshot(member: Member): List<TrackRequest> {
        val memberId = member.user.idLong
        return queueSnapshot().filter { it.memberId == memberId }
    }

    @Synchronized
    fun queueSize(): Int = playlist.size

    @Synchronized
    fun upcomingCount(): Int {
        if (cursor < 0 || playlist.isEmpty()) return 0
        return (playlist.size - cursor - 1).coerceAtLeast(0)
    }

    fun updatePositionFromLavalink(positionMs: Long) {
        lastKnownPositionMs = positionMs.coerceAtLeast(0)
    }

    private fun onGoing(): List<TrackRequest> =
        if (cursor < 0 || playlist.isEmpty() || cursor == playlist.lastIndex) emptyList()
        else playlist.subList(cursor + 1, playlist.size).toList()
}
