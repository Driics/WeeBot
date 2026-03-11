package ru.sablebot.module.audio.service.impl

import dev.arbjerg.lavalink.client.LavalinkClient
import dev.arbjerg.lavalink.client.LavalinkNode
import dev.arbjerg.lavalink.client.NodeOptions
import dev.arbjerg.lavalink.client.player.LavalinkPlayer
import dev.arbjerg.lavalink.libraries.jda.JDAVoiceUpdateListener
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.hooks.VoiceDispatchInterceptor
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import ru.sablebot.common.configuration.CommonConfiguration
import ru.sablebot.common.worker.configuration.WorkerProperties
import ru.sablebot.common.worker.shared.service.DiscordService
import ru.sablebot.module.audio.service.ILavalinkV4AudioService
import java.net.URI
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

@Service
class DefaultAudioServiceImpl(
    private val discoveryClient: DiscoveryClient,
    @param:Qualifier(CommonConfiguration.SCHEDULER) private val scheduler: TaskScheduler,
    private val workerProperties: WorkerProperties,
    private val meterRegistry: MeterRegistry
) : ILavalinkV4AudioService {
    private val log = KotlinLogging.logger {}

    override lateinit var lavalink: LavalinkClient

    private lateinit var jdaProvider: (Int) -> JDA?

    private val registeredNodeUris = ConcurrentHashMap.newKeySet<URI>()
    private val onConfiguredCallbacks = mutableListOf<() -> Unit>()

    init {
        val cfg = workerProperties.audio.lavalink
        if (cfg.enabled) {
            val userId = extractUserIdFromToken(workerProperties.discord.token)
            lavalink = LavalinkClient(userId)

            Gauge.builder("sablebot.lavalink.nodes.available", lavalink) {
                it.nodes.count { n -> n.available }.toDouble()
            }
                .description("Number of available Lavalink nodes")
                .register(meterRegistry)

            Gauge.builder("sablebot.audio.lavalink.ready", this) {
                if (it.hasAvailableNode()) 1.0 else 0.0
            }
                .description("Lavalink node availability status")
                .register(meterRegistry)
        }
    }

    override fun voiceInterceptor(): VoiceDispatchInterceptor? {
        if (!::lavalink.isInitialized) return null
        return JDAVoiceUpdateListener(lavalink)
    }

    override fun configure(discordService: DiscordService) {
        val cfg = workerProperties.audio.lavalink
        if (!cfg.enabled) return

        this.jdaProvider = { shardId -> discordService.getShardById(shardId) }

        runBlocking {
            addNodes()
        }
    }

    private fun extractUserIdFromToken(token: String): Long {
        val encoded = token.split(".").firstOrNull() ?: error("Invalid Discord token format")
        val decoded = String(Base64.getDecoder().decode(encoded))
        return decoded.toLong()
    }

    override fun player(guildId: Long): LavalinkPlayer {
        val node = optimalNodeOrThrow()
        return node.getPlayer(guildId).block()
            ?: error("Failed to retrieve player for guild $guildId")
    }

    override fun connect(channel: VoiceChannel) {
        channel.jda.directAudioController.connect(channel)
    }

    override suspend fun connectAndWait(channel: VoiceChannel): Boolean {
        connect(channel)

        val guild = channel.guild
        try {
            withTimeout(10_000) {
                while (!isConnected(guild)) {
                    delay(100)
                }
                // Post-connection buffer for Lavalink voice state processing
                delay(200)
            }
            return true
        } catch (e: Exception) {
            log.warn(e) { "Connection timeout for guild ${guild.idLong} in channel ${channel.name}" }
            return false
        }
    }

    override fun disconnect(guild: Guild) = guild.jda.directAudioController.disconnect(guild)

    override fun isConnected(guild: Guild): Boolean = guild.audioManager.isConnected

    override fun addOnConfiguredCallback(callback: () -> Unit) {
        onConfiguredCallbacks.add(callback)
    }

    override fun onConfigured() {
        if (!::lavalink.isInitialized) return
        onConfiguredCallbacks.forEach { callback ->
            runCatching { callback() }.onFailure { e ->
                log.error(e) { "Error in onConfigured callback" }
            }
        }
    }

    override fun shutdown() {
        runCatching {
            lavalink.close()
            registeredNodeUris.clear()
        }.onFailure { e ->
            log.warn(e) { "Failed to shutdown Lavalink client" }
        }
    }

    private suspend fun addNodes() {
        val cfg = workerProperties.audio.lavalink

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
                meterRegistry.counter("sablebot.lavalink.node.failures").increment()
                log.warn("Could not add node ${nodeCfg.name} (${nodeCfg.url})", e)
            }
        }

        val discovery = cfg.discovery
        if (discovery != null && discovery.enabled && discovery.serviceName.isNotBlank()) {
            scheduler.scheduleWithFixedDelay(::lookUpDiscovery, 60_000L)
        }

        // Wait for at least one node to become available with 30s timeout
        try {
            withTimeout(30_000) {
                while (!hasAvailableNode()) {
                    delay(500)
                }
                log.info { "Lavalink nodes ready: ${lavalink.nodes.count { it.available }} node(s) available" }
            }
        } catch (e: Exception) {
            log.warn { "Lavalink node readiness timeout after 30s: ${lavalink.nodes.size} node(s) configured, 0 available" }
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
                }.onFailure { e ->
                    meterRegistry.counter("sablebot.lavalink.node.failures").increment()
                    log.warn(e) { "Could not add node $instance" }
                }
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

    private fun hasAvailableNode(): Boolean =
        lavalink.nodes.any { it.available }

    override fun isReady(): Boolean =
        ::lavalink.isInitialized && hasAvailableNode()
}