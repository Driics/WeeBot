package ru.sablebot.module.audio.model

import dev.arbjerg.lavalink.client.player.LavalinkPlayer
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.managers.AudioManager
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class PlaybackInstance(
    val guildId: Long,
    val player: LavalinkPlayer
) {
    private val playlist = Collections.synchronizedList(mutableListOf<TrackRequest>())

    var audioManager: AudioManager? = null // можно вообще удалить в v4, если используешь DirectAudioController
    var mode: RepeatMode = RepeatMode.NONE
        private set

    var cursor: Int = -1
        private set

    var playlistId: Long? = null
    var playlistUuid: String? = null
    var activeTime: Long = System.currentTimeMillis()
        private set

    private val stopped = AtomicBoolean(false)

    // позицию корректнее вести через websocket playerUpdate(state.position).[web:48]
    @Volatile
    var lastKnownPositionMs: Long = 0
        private set

    @Synchronized
    fun reset() {
        mode = RepeatMode.NONE
        playlist.clear()
        cursor = -1
        stopped.set(false)
        tick()
        // stop -> Update Player Endpoint track=null[web:44]
        // Реальный вызов сделай снаружи (suspend), чтобы не мешать sync/locks.
    }

    @Synchronized
    fun tick() {
        activeTime = System.currentTimeMillis()
    }

    /**
     * Добавить в очередь и, если сейчас ничего не играет, вернуть трек который надо стартовать.
     * Сетевой вызов (player.play/update) делай в сервисе, чтобы не держать lock во время I/O.
     */
    @Synchronized
    fun enqueue(request: TrackRequest): TrackRequest? {
        tick()
        offer(request)

        val shouldStart = (cursor == -1 || cursor >= playlist.size) // пусто до этого
        if (shouldStart) {
            mode = RepeatMode.NONE
            cursor = 0
            return playlist[cursor]
        }
        return null
    }

    /**
     * Вычисляет следующий трек, который надо проиграть, согласно repeat mode.
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
    fun stopFlag(): Boolean = stopped.compareAndSet(false, true)

    @Synchronized
    fun removeByIndex(index: Int): TrackRequest? {
        if (index !in playlist.indices || index == cursor) return null
        val removed = playlist.removeAt(index)
        if (index < cursor) cursor -= 1
        return removed
    }

    @Synchronized
    fun setMode(newMode: RepeatMode) {
        mode = newMode
    }

    @Synchronized
    fun shuffleUpcoming(): Boolean {
        if (playlist.isEmpty()) return false
        val upcoming = onGoingMutable()
        if (upcoming.isEmpty()) return false
        upcoming.shuffle()
        return true
    }

    @Synchronized
    fun currentOrNull(): TrackRequest? =
        if (cursor < 0 || playlist.isEmpty()) null else playlist[cursor]

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

    fun updatePositionFromLavalink(positionMs: Long) {
        // дергай из websocket listener’а playerUpdate(state.position)[web:48]
        lastKnownPositionMs = positionMs.coerceAtLeast(0)
    }

    private fun offer(request: TrackRequest) {
        playlist.add(request)
    }

    private fun onGoing(): List<TrackRequest> =
        if (cursor < 0 || playlist.isEmpty() || cursor == playlist.lastIndex) emptyList()
        else playlist.subList(cursor + 1, playlist.size).toList()

    private fun onGoingMutable(): MutableList<TrackRequest> =
        if (cursor < 0 || playlist.isEmpty() || cursor == playlist.lastIndex) mutableListOf()
        else playlist.subList(cursor + 1, playlist.size)
}
