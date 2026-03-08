package ru.sablebot.module.audio.model

import dev.arbjerg.lavalink.protocol.v4.Message.EmittedEvent.TrackEndEvent.AudioTrackEndReason

enum class EndReason(vararg val reasons: AudioTrackEndReason) {
    LISTENED(AudioTrackEndReason.FINISHED),
    SKIPPED,
    STOPPED(AudioTrackEndReason.STOPPED),
    SHUTDOWN,
    ERROR(AudioTrackEndReason.LOAD_FAILED, AudioTrackEndReason.CLEANUP);

    companion object {
        fun getForLavaPlayer(lavaReason: AudioTrackEndReason): EndReason? {
            entries.forEach { reason ->
                if (reason.reasons.contains(lavaReason)) {
                    return reason
                }
            }
            return null
        }
    }
}
