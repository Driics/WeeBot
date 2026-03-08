package ru.sablebot.api.kafka

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import ru.sablebot.api.websocket.GuildEventBroadcaster

@Component
class ModerationEventConsumer(
    private val guildEventBroadcaster: GuildEventBroadcaster
) {
    private val logger = KotlinLogging.logger {}

    @KafkaListener(topics = ["sablebot.events.moderation"], groupId = "sablebot-api-group")
    fun onModerationEvent(event: Map<String, Any>) {
        val guildId = event["guildId"]?.toString() ?: return
        logger.debug { "Received moderation event for guild $guildId" }
        guildEventBroadcaster.broadcastModerationEvent(guildId, event)
    }
}
