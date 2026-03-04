package ru.sablebot.common.worker.shared.service

import net.dv8tion.jda.api.hooks.VoiceDispatchInterceptor

interface AudioService {
    fun voiceInterceptor(): VoiceDispatchInterceptor? = null

    fun configure(discordService: DiscordService)

    fun onConfigured() {}
}
