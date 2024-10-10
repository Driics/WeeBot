package ru.driics.sablebot.common.worker.shared.service

import dev.minn.jda.ktx.interactions.commands.updateCommands
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.ExceptionEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent
import net.dv8tion.jda.api.events.session.SessionResumeEvent
import net.dv8tion.jda.api.hooks.IEventManager
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder
import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jmx.export.MBeanExporter
import org.springframework.stereotype.Service
import ru.driics.sablebot.common.configuration.CommonProperties
import ru.driics.sablebot.common.worker.command.service.CommandsHolderService
import ru.driics.sablebot.common.worker.configuration.WorkerProperties
import javax.security.auth.login.LoginException


@Service
open class DiscordServiceImpl @Autowired constructor(
    private val eventManager: IEventManager,
    private val workerProperties: WorkerProperties,
    private val mBeanExporter: MBeanExporter,
    private val commonProperties: CommonProperties,
    private val holderService: CommandsHolderService
) : ListenerAdapter(), DiscordService {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    @PostConstruct
    fun init() {
        val token = workerProperties.discord.token
        require(token.isNotBlank()) { "No Discord token provided" }

        try {
            RestAction.setPassContext(false)
            val shardManagerBuilder = DefaultShardManagerBuilder.createDefault(token)
                .setEventManagerProvider { eventManager }
                .addEventListeners(this)
                .setEnableShutdownHook(false)

            _shardManager = shardManagerBuilder.build()
        } catch (e: LoginException) {
            log.error(e) { "Couldn't login bot with specified token" }
            // Handle the exception appropriately, maybe re-attempt login or shutdown gracefully
        }
    }

    private lateinit var _shardManager: ShardManager
    override val shardManager: ShardManager
        get() = _shardManager

    override val jda: JDA
        get() = _shardManager.shards.iterator().next()

    override val selfUser: User
        get() = jda.selfUser

    @PreDestroy
    fun destroy() {
        shardManager.shutdown()
    }

    override fun onException(event: ExceptionEvent) {
        log.error(event.cause) { "JDA error" }
    }

    override fun onReady(event: ReadyEvent) {
//        mBeanExporter.registerManagedResource(JmxJDAMBean(event.jda))
        setUpStatus()
    }

    override fun onSessionResume(event: SessionResumeEvent) = setUpStatus()

    override fun onSessionDisconnect(event: SessionDisconnectEvent) {
        event.serviceCloseFrame?.let { frame ->
            log.warn { "WebSocket connection closed with code  ${frame.closeCode}: ${frame.closeReason}" }
        }
    }

    private fun setUpStatus() {
        shardManager.setStatus(OnlineStatus.IDLE)

        val playingStatus = workerProperties.discord.playingStatus
        if (playingStatus.isNotEmpty())
            shardManager.setActivity(Activity.customStatus(playingStatus + " " + jda.shardInfo.shardString))
    }

    override fun isConnected(guildId: Long): Boolean {
        if (guildId == 0L)
            return JDA.Status.CONNECTED == jda.status

        return getShard(guildId)?.let {
            JDA.Status.CONNECTED == it.status
        } ?: false
    }

    override fun getShard(guildId: Long): JDA? =
        shardManager.getShardById(((guildId shr 22) % workerProperties.discord.shardsTotal).toInt())

    override fun isSuperUser(user: User): Boolean =
        user.id == commonProperties.discord.superUserId

}