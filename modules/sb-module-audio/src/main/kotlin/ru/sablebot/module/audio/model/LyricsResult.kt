package ru.sablebot.module.audio.model

/**
 * Represents the result of a lyrics search from LRCLIB API.
 *
 * @property trackName The name of the track
 * @property artistName The name of the artist
 * @property plainLyrics Plain text lyrics without timestamps
 * @property syncedLyrics Time-synced lyrics in LRC format (nullable)
 */
data class LyricsResult(
    val trackName: String,
    val artistName: String,
    val plainLyrics: String?,
    val syncedLyrics: String?
)
