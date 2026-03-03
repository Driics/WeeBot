package ru.sablebot.module.moderation.service

import net.dv8tion.jda.api.entities.Message

interface IAutoModService {
    suspend fun onMessage(message: Message)
}
