package ru.sablebot.api.kafka

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import ru.sablebot.api.websocket.GuildEventBroadcaster

@Component
class StatsEventConsumer(
    private val guildEventBroadcaster: GuildEventBroadcaster
) {
    private val logger = KotlinLogging.logger {}

    @KafkaListener(topics = ["sablebot.events.stats"], groupId = "sablebot-api-group")
    fun onStatsEvent(event: Map<String, Any>) {
        val guildId = event["guildId"]?.toString() ?: return
        logger.debug { "Received stats event for guild $guildId" }
        guildEventBroadcaster.broadcastStatsEvent(guildId, event)
    }
}
