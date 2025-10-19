package ru.sablebot.worker.rabbit

import com.codahale.metrics.Counter
import com.codahale.metrics.Gauge
import com.codahale.metrics.Metric
import com.codahale.metrics.MetricRegistry
import net.dv8tion.jda.api.JDA
import org.springframework.amqp.rabbit.annotation.EnableRabbit
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.sablebot.common.configuration.RabbitConfiguration
import ru.sablebot.common.model.status.ShardDto
import ru.sablebot.common.model.status.StatusDto
import ru.sablebot.common.worker.metrics.service.DiscordMetricsRegistry
import kotlin.reflect.KClass

@EnableRabbit
@Component
class StatusQueueListener : BaseQueueListener() {

    @Autowired
    private lateinit var metricRegistry: MetricRegistry

    @RabbitListener(queues = [RabbitConfiguration.QUEUE_STATUS_REQUEST])
    fun getStatus(dummy: String): StatusDto {
        val metricMap = metricRegistry.metrics

        return StatusDto().apply {
            guildCount = getMetricGauge(metricMap, DiscordMetricsRegistry.GAUGE_GUILDS)
            userCount = getMetricGauge(metricMap, DiscordMetricsRegistry.GAUGE_USERS)
            textChannelCount = getMetricGauge(metricMap, DiscordMetricsRegistry.GAUGE_TEXT_CHANNELS)
            voiceChannelCount = getMetricGauge(metricMap, DiscordMetricsRegistry.GAUGE_VOICE_CHANNELS)
            activeConnections = 0 /* TODO */
            executedCommands = getMetricGauge(metricMap, "commands.executions.persist")
            uptimeDuration = getMetricGauge(metricMap, "jvm.uptime")

            shards = discordService.shardManager.shards
                .sortedBy { it.shardInfo.shardId }
                .map { shard ->
                    ShardDto().apply {
                        id = shard.shardInfo.shardId
                        guilds = shard.guildCache.size().toLong()
                        users = shard.userCache.size().toLong()
                        channels = (shard.textChannelCache.size() + shard.voiceChannelCache.size()).toLong()
                        ping = shard.gatewayPing
                        connected = shard.status == JDA.Status.CONNECTED
                    }
                }
        }
    }

    private fun getMetricGauge(metricMap: Map<String, Metric>, name: String): Long =
        getMetricValue(metricMap, name, Long::class, 0L)

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> getMetricValue(
        metricMap: Map<String, Metric>,
        name: String,
        type: KClass<T>,
        defaultValue: T
    ): T {
        val result = getMetricValue<Any>(metricMap, name, null)
        return if (result != null && type.isInstance(result)) {
            result as T
        } else {
            defaultValue
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getMetricValue(
        metricMap: Map<String, Metric>,
        name: String,
        valueExtractor: ((Any) -> T)?
    ): T? {
        val metric = metricMap[name] ?: return null

        val value = when (metric) {
            is Gauge<*> -> metric.value
            is Counter -> metric.count
            else -> null
        }

        return if (value != null && valueExtractor != null) {
            valueExtractor(value)
        } else {
            value as? T
        }
    }
}