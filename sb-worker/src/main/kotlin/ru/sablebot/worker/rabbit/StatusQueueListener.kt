package ru.sablebot.worker.rabbit

import io.micrometer.core.instrument.MeterRegistry
import net.dv8tion.jda.api.JDA
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.sablebot.common.configuration.RabbitConfiguration
import ru.sablebot.common.model.status.ShardDto
import ru.sablebot.common.model.status.StatusDto

@Component
class StatusQueueListener : BaseQueueListener() {

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @RabbitListener(queues = [RabbitConfiguration.QUEUE_STATUS_REQUEST])
    fun getStatus(dummy: String): StatusDto {
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