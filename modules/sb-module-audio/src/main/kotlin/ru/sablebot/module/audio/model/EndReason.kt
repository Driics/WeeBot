package ru.sablebot.module.audio.model

/**
 * Reasons why a track ended playback.
 * In Lavalink v4, these are managed internally and mapped from TrackEndEvent reasons.
 */
enum class EndReason {
    /** Track finished playing normally */
    LISTENED,

    /** Track was skipped by user */
    SKIPPED,

    /** Track was stopped */
    STOPPED,

    /** Bot is shutting down */
    SHUTDOWN,

    /** Track failed to load or encountered an error */
    ERROR
}

