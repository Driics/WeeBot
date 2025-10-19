package ru.sablebot.common.worker.metrics.service

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
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
    }

    @Autowired
    private lateinit var discordService: DiscordService

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    private val shardManager
        get() = discordService.shardManager

    @Bean
    fun gaugeGuilds() = MeterBinder {
        Gauge.builder(GAUGE_GUILDS, shardManager) {
            runCatching { it.guildCache.size().toDouble() }.getOrElse { 0.0 }
        }
            .description("Number of Discord guilds")
            .register(meterRegistry)
    }.bindTo(meterRegistry)

    @Bean
    fun gaugeUsers() = MeterBinder {
        Gauge.builder(GAUGE_USERS, shardManager) {
            runCatching { it.userCache.size().toDouble() }.getOrElse { 0.0 }
        }
            .description("Number of Discord users")
            .register(meterRegistry)
    }.bindTo(meterRegistry)

    @Bean
    fun gaugePing() = MeterBinder {
        Gauge.builder(GAUGE_PING, shardManager) {
            runCatching { it.averageGatewayPing }.getOrElse { 0.0 }
        }
            .description("Average gateway ping in milliseconds")
            .baseUnit("ms")
            .register(meterRegistry)
    }.bindTo(meterRegistry)

    @Bean
    fun gaugeVoiceChannelCount() = MeterBinder {
        Gauge.builder(GAUGE_VOICE_CHANNELS, shardManager) {
            runCatching { it.voiceChannelCache.size().toDouble() }.getOrElse { 0.0 }
        }
            .description("Number of voice channels")
            .register(meterRegistry)
    }.bindTo(meterRegistry)

    @Bean
    fun gaugeTextChannelCount() = MeterBinder {
        Gauge.builder(GAUGE_TEXT_CHANNELS, shardManager) {
            runCatching { it.textChannelCache.size().toDouble() }.getOrElse { 0.0 }
        }
            .description("Number of text channels")
            .register(meterRegistry)
    }.bindTo(meterRegistry)

    @Bean
    fun gaugeChannelCount() = MeterBinder {
        Gauge.builder(GAUGE_CHANNELS, shardManager) {
            runCatching {
                (it.voiceChannelCache.size() + it.textChannelCache.size()).toDouble()
            }.getOrElse { 0.0 }
        }
            .description("Total number of channels (voice + text)")
            .register(meterRegistry)
    }.bindTo(meterRegistry)
}