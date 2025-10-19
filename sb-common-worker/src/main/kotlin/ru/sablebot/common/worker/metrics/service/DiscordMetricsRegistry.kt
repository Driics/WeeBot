package ru.sablebot.common.worker.metrics.service

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import ru.sablebot.common.worker.shared.service.DiscordService

@Component
class DiscordMetricsRegistry(
    private val discordService: DiscordService
) {
    companion object {
        const val GAUGE_GUILDS: String = "discord.guilds"
        const val GAUGE_USERS: String = "discord.users"
        const val GAUGE_CHANNELS: String = "discord.channels"
        const val GAUGE_TEXT_CHANNELS: String = "discord.textChannels"
        const val GAUGE_VOICE_CHANNELS: String = "discord.voiceChannels"
        const val GAUGE_PING: String = "discord.average.ping"
    }

    private val shardManager
        get() = discordService.shardManager

    @Bean
    fun gaugeGuilds() = MeterBinder { registry ->
        Gauge.builder(GAUGE_GUILDS, shardManager) {
            runCatching { it.guildCache.size().toDouble() }.getOrElse { 0.0 }
        }
            .description("Number of Discord guilds")
            .register(registry)
    }

    @Bean
    fun gaugeUsers() = MeterBinder { registry ->
        Gauge.builder(GAUGE_USERS, shardManager) {
            runCatching { it.userCache.size().toDouble() }.getOrElse { 0.0 }
        }
            .description("Number of Discord users")
            .register(registry)
    }

    @Bean
    fun gaugePing() = MeterBinder { registry ->
        Gauge.builder(GAUGE_PING, shardManager) {
            runCatching { it.averageGatewayPing }.getOrElse { 0.0 }
        }
            .description("Average gateway ping in milliseconds")
            .baseUnit("ms")
            .register(registry)
    }

    @Bean
    fun gaugeVoiceChannelCount() = MeterBinder { registry ->
        Gauge.builder(GAUGE_VOICE_CHANNELS, shardManager) {
            runCatching { it.voiceChannelCache.size().toDouble() }.getOrElse { 0.0 }
        }
            .description("Number of voice channels")
            .register(registry)
    }

    @Bean
    fun gaugeTextChannelCount() = MeterBinder { registry ->
        Gauge.builder(GAUGE_TEXT_CHANNELS, shardManager) {
            runCatching { it.textChannelCache.size().toDouble() }.getOrElse { 0.0 }
        }
            .description("Number of text channels")
            .register(registry)
    }

    @Bean
    fun gaugeChannelCount() = MeterBinder { registry ->
        Gauge.builder(GAUGE_CHANNELS, shardManager) {
            runCatching {
                (it.voiceChannelCache.size() + it.textChannelCache.size()).toDouble()
            }.getOrElse { 0.0 }
        }
            .description("Total number of channels (voice + text)")
            .register(registry)
    }
}