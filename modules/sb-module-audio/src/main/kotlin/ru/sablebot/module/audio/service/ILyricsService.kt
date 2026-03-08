package ru.sablebot.module.audio.service

import ru.sablebot.module.audio.model.LyricsResult

interface ILyricsService {
    suspend fun searchLyrics(query: String): LyricsResult?
    suspend fun getLyrics(trackName: String, artistName: String): LyricsResult?
}
