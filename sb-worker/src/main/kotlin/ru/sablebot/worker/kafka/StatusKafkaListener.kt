package ru.sablebot.worker.kafka

import io.micrometer.core.instrument.MeterRegistry
import net.dv8tion.jda.api.JDA
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.stereotype.Component
import ru.sablebot.common.configuration.KafkaConfiguration
import ru.sablebot.common.model.status.ShardDto
import ru.sablebot.common.model.status.StatusDto

@Component
class StatusKafkaListener(
    private val meterRegistry: MeterRegistry
) : BaseKafkaListener() {

    @KafkaListener(
        topics = [KafkaConfiguration.TOPIC_STATUS_REQUEST],
        groupId = "sablebot-status-group"
    )
    @SendTo
    fun getStatus(@Suppress("unused") dummy: String): StatusDto {
        val shardManager = discordService.shardManager

        return StatusDto(
            guildCount = getMetricGauge("discord.guilds"),
            userCount = getMetricGauge("discord.users"),
            textChannelCount = getMetricGauge("discord.text.channels"),
            voiceChannelCount = getMetricGauge("discord.voice.channels"),
            executedCommands = getMetricCounter("commands.executions"),
            uptimeDuration = getMetricGauge("process.uptime"),
            shards = shardManager.shards.map { shard ->
                ShardDto(
                    id = shard.shardInfo.shardId,
                    guilds = shard.guildCache.size(),
                    users = shard.userCache.size(),
                    channels = shard.textChannelCache.size() + shard.voiceChannelCache.size(),
                    ping = shard.gatewayPing,
                    connected = shard.status == JDA.Status.CONNECTED
                )
            }
        )
    }

    /**
     * Retrieves gauge value from Micrometer registry
     */
    private fun getMetricGauge(name: String): Long {
        return meterRegistry.find(name).gauge()?.value()?.toLong() ?: 0L
    }

    /**
     * Retrieves counter value from Micrometer registry
     */
    private fun getMetricCounter(name: String): Long {
        return meterRegistry.find(name).counter()?.count()?.toLong() ?: 0L
    }
}