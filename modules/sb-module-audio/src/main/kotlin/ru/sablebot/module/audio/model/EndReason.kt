package ru.sablebot.module.audio.model

import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason

enum class EndReason(vararg val reasons: AudioTrackEndReason) {
    LISTENED(AudioTrackEndReason.FINISHED),
    SKIPPED,
    STOPPED(AudioTrackEndReason.STOPPED),
    SHUTDOWN,
    ERROR(AudioTrackEndReason.LOAD_FAILED, AudioTrackEndReason.CLEANUP);
}