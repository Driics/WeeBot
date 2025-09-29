package ru.sablebot.common.support

import com.google.common.util.concurrent.ThreadFactoryBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
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
import org.springframework.stereotype.Component
import java.util.concurrent.Executors

@Component
class CoroutineLauncher {
    companion object {
        private val logger = KotlinLogging.logger {}

        private val coroutineMessageExecutor = createThreadPool("Message Executor Thread %d")
        private val coroutineMessageDispatcher = coroutineMessageExecutor.asCoroutineDispatcher()
        private fun createThreadPool(name: String) =
            Executors.newCachedThreadPool(
                ThreadFactoryBuilder()
                    .setNameFormat(name)
                    .setDaemon(true)
                    .build()
            )
    }

    @PreDestroy
    fun shutdown() {
        messageScope.cancel("Spring context shutting down")
        coroutineMessageDispatcher.close()
    }

    val messageScope = CoroutineScope(SupervisorJob() + coroutineMessageDispatcher)

    @OptIn(DelicateCoroutinesApi::class)
    fun launchMessageJob(event: Event, block: suspend CoroutineScope.() -> Unit) {
        val coroutineName = when (event) {
            is StringSelectInteractionEvent -> "StringSelect id=${event.componentId} userId=${event.user.id} channelId=${event.channel.id} guildId=${event.guild?.id}"
            is EntitySelectInteractionEvent -> "EntitySelect id=${event.componentId} userId=${event.user.id} channelId=${event.channel.id} guildId=${event.guild?.id}"
            is ButtonInteractionEvent -> "Button id=${event.componentId} userId=${event.user.id} channelId=${event.channel.id} guildId=${event.guild?.id}"
            is MessageReceivedEvent -> "Message id=${event.messageId} userId=${event.author.id} channelId=${event.channel.id} guildId=${event.guild.id}"
            is SlashCommandInteractionEvent -> "Slash ${event.fullCommandName} userId=${event.user.id} channelId=${event.channel.id} guildId=${event.guild?.id}"
            is UserContextInteractionEvent -> "UserCmd ${event.fullCommandName} userId=${event.user.id} channelId=${event.channel?.id} guildId=${event.guild?.id}"
            is MessageContextInteractionEvent -> "MsgCmd ${event.fullCommandName} userId=${event.user.id} channelId=${event.channel?.id} guildId=${event.guild?.id}"
            is CommandAutoCompleteInteractionEvent -> "Autocomplete ${event.fullCommandName} userId=${event.user.id} channelId=${event.channel.id} guildId=${event.guild?.id}"
            else -> throw IllegalArgumentException("You can't dispatch a $event in a launchMessageJob!")
        }

        val start = System.currentTimeMillis()
        val job = messageScope.launch(CoroutineName(coroutineName), block = block)

        job.invokeOnCompletion {
            val diff = System.currentTimeMillis() - start
            if (diff >= 60_000) {
                logger.warn { "Message Coroutine $job took too long to process! ${diff}ms" }
            }
        }
    }
}