package ru.sablebot.common.worker.metrics.service

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.sablebot.common.worker.shared.service.DiscordService

@Component
class DiscordMetricsRegistry {
    companion object {
        const val GAUGE_GUILDS: String = "discord.guilds"
        const val GAUGE_USERS: String = "discord.users"
        const val GAUGE_CHANNELS: String = "discord.channels"
        const val GAUGE_TEXT_CHANNELS: String = "discord.textChannels"
        const val GAUGE_VOICE_CHANNELS: String = "discord.voiceChannels"
        const val GAUGE_PING: String = "discord.average.ping"

        const val COUNTER_COMMANDS_EXECUTIONS_PERSIST = "commands.executions.persist"
        const val GAUGE_JVM_UPTIME = "jvm.uptime"
    }

    @Autowired
    private lateinit var discordService: DiscordService

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    private val shardManager by lazy { discordService.shardManager }

    @PostConstruct
    fun registerMetrics() {
        // Register guild count gauge
        Gauge.builder(GAUGE_GUILDS, shardManager) {
            runCatching { it.guildCache.size().toDouble() }.getOrElse { 0.0 }
        }
            .description("Number of Discord guilds")
            .register(meterRegistry)

        // Register user count gauge
        Gauge.builder(GAUGE_USERS, shardManager) {
            runCatching { it.userCache.size().toDouble() }.getOrElse { 0.0 }
        }
            .description("Number of Discord users")
            .register(meterRegistry)

        // Register average ping gauge
        Gauge.builder(GAUGE_PING, shardManager) {
            runCatching { it.averageGatewayPing }.getOrElse { 0.0 }
        }
            .description("Average gateway ping in milliseconds")
            .baseUnit("ms")
            .register(meterRegistry)

        // Register voice channels count gauge
        Gauge.builder(GAUGE_VOICE_CHANNELS, shardManager) {
            runCatching { it.voiceChannelCache.size().toDouble() }.getOrElse { 0.0 }
        }
            .description("Number of voice channels")
            .register(meterRegistry)

        // Register text channels count gauge
        Gauge.builder(GAUGE_TEXT_CHANNELS, shardManager) {
            runCatching { it.textChannelCache.size().toDouble() }.getOrElse { 0.0 }
        }
            .description("Number of text channels")
            .register(meterRegistry)

        // Register total channels count gauge
        Gauge.builder(GAUGE_CHANNELS, shardManager) {
            runCatching {
                (it.voiceChannelCache.size() + it.textChannelCache.size()).toDouble()
            }.getOrElse { 0.0 }
        }
            .description("Total number of channels (voice + text)")
            .register(meterRegistry)
    }
}