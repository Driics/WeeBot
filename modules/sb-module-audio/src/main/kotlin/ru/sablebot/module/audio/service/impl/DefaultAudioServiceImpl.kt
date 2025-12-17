package ru.sablebot.module.audio.service.impl

import dev.arbjerg.lavalink.client.LavalinkClient
import dev.arbjerg.lavalink.libraries.jda.JDAVoiceUpdateListener
import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import ru.sablebot.common.configuration.CommonConfiguration
import ru.sablebot.common.worker.configuration.WorkerProperties
import ru.sablebot.common.worker.shared.service.DiscordService
import ru.sablebot.module.audio.service.ILavalinkV4AudioService
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

@Service
class DefaultAudioServiceImpl(
    private val discoveryClient: DiscoveryClient,
    @param:Qualifier(CommonConfiguration.SCHEDULER)
    private val scheduler: TaskScheduler,
    private val workerProperties: WorkerProperties
) : ILavalinkV4AudioService {
    private val log = KotlinLogging.logger {}

    override lateinit var lavalink: LavalinkClient
        private set

    private lateinit var jdaProvider: (Int) -> JDA?

    private val registeredNodeUris = ConcurrentHashMap.newKeySet<URI>()

    override fun configure(discordService: DiscordService, builder: DefaultShardManagerBuilder) {
        val cfg = workerProperties.audio.lavalink
        if (!cfg.enabled)
            return

        this.jdaProvider = { shardId -> discordService.getShardById(shardId) }

        configureInternal(
            userId = discordService.selfUser.idLong,
            shardsTotal = workerProperties.discord.shardsTotal,
            shardById = { shardId -> shardId to (discordService.getShardById(shardId) ?: return@configureInternal) }
        )

        builder.setVoiceDispatchInterceptor(JDAVoiceUpdateListener(lavalink))
    }

    private fun configureInternal(
        userId: Long,
        shardsTotal: Int,
        shardById: (Int) -> Pair<Int, JDA>
    ) {
        val cfg = workerProperties.audio.lavalink
        if (!cfg.enabled)
            return

        lavalink = LavalinkClient(userId)

        cfg.nodes.forEach { nodeCfg ->
            runCatching {
                val uri = URI(nodeCfg.url)
                addOrReplaceNode(
                    name = nodeCfg.name,
                    uri = uri,
                    password = nodeCfg.password,
                    secure = uri.scheme.equals("wss", ignoreCase = true)
                )
            }.onFailure { e ->
                log.warn("Could not add node ${nodeCfg.name} (${nodeCfg.url})", e)
            }
        }

        val discovery = cfg.discovery
        if (discovery != null && discovery.enabled && discovery.serviceName.isNotBlank()) {
            scheduler.scheduleWithFixedDelay(::lookUpDiscovery, 60_000L)
        }
    }

    override fun disconnect(guild: Guild) =
        guild.jda.directAudioController.disconnect(guild)

    override fun isConnected(guild: Guild): Boolean =
        guild.jda.directAudioController.isConnected(guild)
}