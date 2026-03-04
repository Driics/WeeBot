package ru.sablebot.api.kafka

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import ru.sablebot.api.websocket.GuildEventBroadcaster

@Component
class AudioEventConsumer(
    private val guildEventBroadcaster: GuildEventBroadcaster
) {
    private val logger = KotlinLogging.logger {}

    @KafkaListener(topics = ["sablebot.events.audio"], groupId = "sablebot-api-group")
    fun onAudioEvent(event: Map<String, Any>) {
        val guildId = event["guildId"]?.toString() ?: return
        logger.debug { "Received audio event for guild $guildId" }
        guildEventBroadcaster.broadcastAudioEvent(guildId, event)
    }
}
