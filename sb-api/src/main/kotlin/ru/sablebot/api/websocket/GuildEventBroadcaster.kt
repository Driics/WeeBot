package ru.sablebot.api.websocket

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service

@Service
class GuildEventBroadcaster(
    private val messagingTemplate: SimpMessagingTemplate
) {
    private val logger = KotlinLogging.logger {}

    fun broadcastModerationEvent(guildId: String, event: Any) {
        val destination = "/topic/guild.$guildId.moderation"
        messagingTemplate.convertAndSend(destination, event)
        logger.debug { "Broadcast moderation event to $destination" }
    }

    fun broadcastStatsEvent(guildId: String, event: Any) {
        val destination = "/topic/guild.$guildId.stats"
        messagingTemplate.convertAndSend(destination, event)
        logger.debug { "Broadcast stats event to $destination" }
    }

    fun broadcastAudioEvent(guildId: String, event: Any) {
        val destination = "/topic/guild.$guildId.audio"
        messagingTemplate.convertAndSend(destination, event)
        logger.debug { "Broadcast audio event to $destination" }
    }
}
