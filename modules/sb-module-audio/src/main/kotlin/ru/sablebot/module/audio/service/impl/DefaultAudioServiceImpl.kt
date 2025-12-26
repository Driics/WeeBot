package ru.sablebot.module.audio.service.impl

import dev.arbjerg.lavalink.client.LavalinkClient
import dev.arbjerg.lavalink.client.LavalinkNode
import dev.arbjerg.lavalink.client.NodeOptions
import dev.arbjerg.lavalink.client.player.LavalinkPlayer
import dev.arbjerg.lavalink.libraries.jda.JDAVoiceUpdateListener
import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
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
    @param:Qualifier(CommonConfiguration.SCHEDULER) private val scheduler: TaskScheduler,
    private val workerProperties: WorkerProperties
) : ILavalinkV4AudioService {
    private val log = KotlinLogging.logger {}

    override lateinit var lavalink: LavalinkClient
        private set

    private lateinit var jdaProvider: (Int) -> JDA?

    private val registeredNodeUris = ConcurrentHashMap.newKeySet<URI>()

    override fun configure(discordService: DiscordService, builder: DefaultShardManagerBuilder) {
        val cfg = workerProperties.audio.lavalink
        if (!cfg.enabled) return

        this.jdaProvider = { shardId -> discordService.getShardById(shardId) }

        val availableShards = (0 until workerProperties.discord.shardsTotal)
            .mapNotNull { shardId ->
                discordService.getShardById(shardId)?.let { shardId to it }
            }
            .toMap()

        configureInternal(
            userId = discordService.selfUser.idLong,
            shardsTotal = workerProperties.discord.shardsTotal,
            shardById = { shardId -> shardId to availableShards[shardId]!! }
        )

        builder.setVoiceDispatchInterceptor(JDAVoiceUpdateListener(lavalink))
    }

    override fun player(guildId: Long): LavalinkPlayer? {
        val node = optimalNodeOrThrow()
        return node.getPlayer(guildId).block()
    }

    override fun connect(channel: VoiceChannel) {
        channel.jda.directAudioController.connect(channel)
    }

    override fun disconnect(guild: Guild) = guild.jda.directAudioController.disconnect(guild)

    override fun isConnected(guild: Guild): Boolean = guild.audioManager.isConnected

    override fun shutdown() {
        runCatching {
            lavalink.close()
            registeredNodeUris.clear()
        }.onFailure { e ->
            log.warn(e) { "Failed to shutdown Lavalink client" }
        }
    }

    private fun configureInternal(
        userId: Long, shardsTotal: Int, shardById: (Int) -> Pair<Int, JDA>
    ) {
        val cfg = workerProperties.audio.lavalink
        if (!cfg.enabled) return

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

    @Synchronized
    private fun lookUpDiscovery() {
        val discovery = workerProperties.audio.lavalink.discovery
        if (!discovery.enabled || discovery.serviceName.isBlank()) return

        try {
            val instances = discoveryClient.getInstances(discovery.serviceName)
            val instanceUris = instances.mapNotNull { instance ->
                val secure = instance.metadata["secure"]?.toBoolean() ?: false
                val protocol = if (secure) "wss" else "ws"
                runCatching { instance to URI("$protocol://${instance.host}:${instance.port}") }.getOrNull()
            }.toSet()
            val liveUris = instanceUris.map { it.second }.toSet()

            instanceUris.forEach { (instance, uri) ->
                runCatching {
                    if (registeredNodeUris.add(uri)) {
                        val instanceName = instance.metadata["instanceName"] ?: instance.instanceId
                        addOrReplaceNode(instanceName, uri, discovery.password, secure = false)
                        log.info { "Discovered Lavalink node $instanceName at $uri" }
                    }
                }.onFailure { e -> log.warn(e) { "Could not add node $instance" } }
            }

            val deadUris = registeredNodeUris.subtract(liveUris)
            deadUris.forEach { uri ->
                val removed = removeNodeByUri(uri)
                if (removed) registeredNodeUris.remove(uri)
            }
        } catch (e: Exception) {
            log.warn(e) { "Could not initialize Lavalink services" }
        }
    }

    private fun addOrReplaceNode(
        name: String,
        uri: URI,
        password: String,
        secure: Boolean,
    ) {
        val nodeOptions = NodeOptions.Builder().apply {
            setName(name)
            setServerUri(uri)
            setPassword(password)
        }.build()

        lavalink.addNode(nodeOptions)
    }

    private fun removeNodeByUri(uri: URI): Boolean =
        lavalink.nodes
            .firstOrNull { it.baseUri == uri.toString() }
            ?.let { runCatching { it.close() }.isSuccess }
            ?: false

    private fun optimalNodeOrThrow(): LavalinkNode =
        lavalink.nodes.filter { it.available }.minByOrNull { it.penalties.calculateTotal() }
            ?: error("No available Lavalink nodes")
}