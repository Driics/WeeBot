package ru.sablebot.common.support

import com.google.common.util.concurrent.ThreadFactoryBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.*
import net.dv8tion.jda.api.events.Event
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Component
class CoroutineLauncher(
    @Autowired(required = false) private val meterRegistry: MeterRegistry? = null
) {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val coroutineMessageExecutor = ThreadPoolExecutor(
        Runtime.getRuntime().availableProcessors(),
        Runtime.getRuntime().availableProcessors() * 4,
        60L, TimeUnit.SECONDS,
        LinkedBlockingQueue(1000),
        ThreadFactoryBuilder()
            .setNameFormat("Message Executor Thread %d")
            .setDaemon(true)
            .build()
    )
    private val coroutineMessageDispatcher = coroutineMessageExecutor.asCoroutineDispatcher()

    val messageScope = CoroutineScope(SupervisorJob() + coroutineMessageDispatcher)

    init {
        meterRegistry?.let { registry ->
            Gauge.builder("sablebot.coroutine.executor.queue.size", coroutineMessageExecutor) {
                it.queue.size.toDouble()
            }.description("Coroutine executor queue depth").register(registry)

            Gauge.builder("sablebot.coroutine.executor.active", coroutineMessageExecutor) {
                it.activeCount.toDouble()
            }.description("Coroutine executor active threads").register(registry)

            Gauge.builder("sablebot.coroutine.executor.pool.size", coroutineMessageExecutor) {
                it.poolSize.toDouble()
            }.description("Coroutine executor pool size").register(registry)
        }
    }

    @PreDestroy
    fun shutdown() {
        messageScope.cancel("Spring context shutting down")
        coroutineMessageDispatcher.close()
    }

    fun launchMessageJob(event: Event, block: suspend CoroutineScope.() -> Unit) {
        val eventType = when (event) {
            is StringSelectInteractionEvent -> "StringSelect"
            is EntitySelectInteractionEvent -> "EntitySelect"
            is ButtonInteractionEvent -> "Button"
            is MessageReceivedEvent -> "Message"
            is SlashCommandInteractionEvent -> "Slash"
            is UserContextInteractionEvent -> "UserCmd"
            is MessageContextInteractionEvent -> "MsgCmd"
            is CommandAutoCompleteInteractionEvent -> "Autocomplete"
            else -> "Unknown"
        }

        val coroutineName = when (event) {
            is StringSelectInteractionEvent -> "StringSelect id=${event.componentId} userId=${event.user.id} channelId=${event.channel.id} guildId=${event.guild?.id}"
            is EntitySelectInteractionEvent -> "EntitySelect id=${event.componentId} userId=${event.user.id} channelId=${event.channel.id} guildId=${event.guild?.id}"
            is ButtonInteractionEvent -> "Button id=${event.componentId} userId=${event.user.id} channelId=${event.channel.id} guildId=${event.guild?.id}"
            is MessageReceivedEvent -> "Message id=${event.messageId} userId=${event.author.id} channelId=${event.channel.id} guildId=${event.guild.id}"
            is SlashCommandInteractionEvent -> "Slash ${event.fullCommandName} userId=${event.user.id} channelId=${event.channel.id} guildId=${event.guild?.id}"
            is UserContextInteractionEvent -> "UserCmd ${event.fullCommandName} userId=${event.user.id} channelId=${event.channel?.id} guildId=${event.guild?.id}"
            is MessageContextInteractionEvent -> "MsgCmd ${event.fullCommandName} userId=${event.user.id} channelId=${event.channel?.id} guildId=${event.guild?.id}"
            is CommandAutoCompleteInteractionEvent -> "Autocomplete ${event.fullCommandName} userId=${event.user.id} channelId=${event.channel.id} guildId=${event.guild?.id}"
            else -> "Unknown ${event::class.simpleName} userId=unknown"
        }

        meterRegistry?.counter("sablebot.coroutine.jobs.total", "event_type", eventType)?.increment()
        val sample = meterRegistry?.let { Timer.start(it) }
        val start = System.currentTimeMillis()
        val job = messageScope.launch(CoroutineName(coroutineName), block = block)

        job.invokeOnCompletion {
            sample?.stop(meterRegistry!!.timer("sablebot.coroutine.job.duration", "event_type", eventType))
            val diff = System.currentTimeMillis() - start
            if (diff >= 60_000) {
                logger.warn { "Message Coroutine $job took too long to process! ${diff}ms" }
            }
        }
    }
}