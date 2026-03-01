package ru.sablebot.common.worker.metrics.service

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.binder.MeterBinder
import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import ru.sablebot.common.worker.shared.service.DiscordService

@Component
class DiscordMetricsRegistry(
    private val discordService: DiscordService
) {
    companion object {
        const val GAUGE_GUILDS: String = "sablebot.discord.guilds"
        const val GAUGE_USERS: String = "sablebot.discord.users"
        const val GAUGE_CHANNELS: String = "sablebot.discord.channels"
        const val GAUGE_TEXT_CHANNELS: String = "sablebot.discord.text.channels"
        const val GAUGE_VOICE_CHANNELS: String = "sablebot.discord.voice.channels"
        const val GAUGE_PING: String = "sablebot.discord.average.ping"
    }

    private fun gauge(
        name: String,
        description: String,
        unit: String? = null,
        f: (ShardManager) -> Double
    ) = MeterBinder { registry ->
        Gauge.builder(name, discordService) { ds: DiscordService ->
            runCatching { f(ds.shardManager) }.getOrElse { 0.0 }
        }
            .description(description)
            .apply { if (unit != null) baseUnit(unit) }
            .strongReference(true)
            .register(registry)
    }

    @Bean
    fun gaugeGuilds() = gauge(GAUGE_GUILDS, "Number of Discord guilds") { it.guildCache.size().toDouble() }

    @Bean
    fun gaugeUsers() = gauge(GAUGE_USERS, "Number of Discord users") { it.userCache.size().toDouble() }

    @Bean
    fun gaugePing() = gauge(GAUGE_PING, "Average gateway ping in milliseconds", unit = "ms") { it.averageGatewayPing }

    @Bean
    fun gaugeVoiceChannelCount() =
        gauge(GAUGE_VOICE_CHANNELS, "Number of voice channels") { it.voiceChannelCache.size().toDouble() }

    @Bean
    fun gaugeTextChannelCount() =
        gauge(GAUGE_TEXT_CHANNELS, "Number of text channels") { it.textChannelCache.size().toDouble() }

    @Bean
    fun gaugeChannelCount() = gauge(GAUGE_CHANNELS, "Total number of channels (voice + text)") {
        (it.voiceChannelCache.size() + it.textChannelCache.size()).toDouble()
    }
}