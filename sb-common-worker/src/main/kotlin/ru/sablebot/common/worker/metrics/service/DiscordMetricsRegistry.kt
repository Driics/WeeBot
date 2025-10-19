package ru.sablebot.common.worker.metrics.service

import com.codahale.metrics.annotation.CachedGauge
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.sablebot.common.worker.shared.service.DiscordService
import java.util.concurrent.TimeUnit

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

    private val shardManager by lazy { discordService.shardManager }

    @get:CachedGauge(
        name = GAUGE_GUILDS,
        absolute = true,
        timeout = 15,
        timeoutUnit = TimeUnit.SECONDS
    )
    val guildCount by lazy { shardManager.guildCache.size() }

    @get:CachedGauge(
        name = GAUGE_USERS,
        absolute = true,
        timeout = 15,
        timeoutUnit = TimeUnit.SECONDS
    )
    val usersCount by lazy { shardManager.userCache.size() }

    @get:CachedGauge(
        name = GAUGE_PING,
        absolute = true,
        timeout = 15,
        timeoutUnit = TimeUnit.SECONDS
    )
    val averagePing by lazy { shardManager.averageGatewayPing }

    @get:CachedGauge(
        name = GAUGE_VOICE_CHANNELS,
        absolute = true,
        timeout = 15,
        timeoutUnit = TimeUnit.SECONDS
    )
    val voiceChannelsCount by lazy { shardManager.voiceChannelCache.size() }

    @get:CachedGauge(
        name = GAUGE_TEXT_CHANNELS,
        absolute = true,
        timeout = 15,
        timeoutUnit = TimeUnit.SECONDS
    )
    val textChannelsCount by lazy { shardManager.textChannelCache.size() }

    @get:CachedGauge(
        name = GAUGE_CHANNELS,
        absolute = true,
        timeout = 15,
        timeoutUnit = TimeUnit.SECONDS
    )
    val channelsCount by lazy { voiceChannelsCount + textChannelsCount }
}