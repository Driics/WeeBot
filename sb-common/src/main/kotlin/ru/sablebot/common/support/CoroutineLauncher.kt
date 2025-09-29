package ru.sablebot.common.support

import com.google.common.util.concurrent.ThreadFactoryBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import net.dv8tion.jda.api.events.Event
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent
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
            Executors.newCachedThreadPool(ThreadFactoryBuilder().setNameFormat(name).build())
    }


    @OptIn(DelicateCoroutinesApi::class)
    fun launchMessageJob(event: Event, block: suspend CoroutineScope.() -> Unit) {
        val coroutineName = when (event) {
            is MessageReceivedEvent -> "Message ${event.message} by user ${event.author} in ${event.channel} on ${if (event.isFromGuild) event.guild else null}"
            is SlashCommandInteractionEvent -> "Slash Command ${event.fullCommandName} by user ${event.user} in ${event.channel} on ${if (event.isFromGuild) event.guild else null}"
            is UserContextInteractionEvent -> "User Command ${event.fullCommandName} by user ${event.user} in ${event.channel} on ${if (event.isFromGuild) event.guild else null}"
            is MessageContextInteractionEvent -> "User Command ${event.fullCommandName} by user ${event.user} in ${event.channel} on ${if (event.isFromGuild) event.guild else null}"
            is CommandAutoCompleteInteractionEvent -> "Autocomplete for Command ${event.fullCommandName} by user ${event.user} in ${event.channel} on ${if (event.isFromGuild) event.guild else null}"
            else -> throw IllegalArgumentException("You can't dispatch a $event in a launchMessageJob!")
        }

        val start = System.currentTimeMillis()
        val job = GlobalScope.launch(
            coroutineMessageDispatcher + CoroutineName(coroutineName),
            block = block
        )

        job.invokeOnCompletion {

            val diff = System.currentTimeMillis() - start
            if (diff >= 60_000) {
                logger.warn { "Message Coroutine $job took too long to process! ${diff}ms" }
            }
        }
    }
}