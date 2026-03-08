package ru.sablebot.module.audio.service

interface IAudioSearchProvider {
    val providerName: String
    fun searchTrack(value: String): String
}