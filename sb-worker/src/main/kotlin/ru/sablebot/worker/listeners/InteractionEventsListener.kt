package ru.sablebot.worker.listeners

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import ru.sablebot.common.support.CoroutineLauncher
import ru.sablebot.common.worker.command.service.CommandsHolderService
import ru.sablebot.common.worker.event.DiscordEvent
import ru.sablebot.common.worker.event.listeners.DiscordEventListener
import ru.sablebot.common.worker.message.model.ComponentContext
import ru.sablebot.common.worker.message.model.InteractivityManager
import ru.sablebot.common.worker.message.model.UnleashedComponentId
import ru.sablebot.common.worker.message.model.styled

@DiscordEvent
@OptIn(DelicateCoroutinesApi::class)
class InteractionEventsListener(
    val interactivityManager: InteractivityManager,
    val coroutineLauncher: CoroutineLauncher,
    val commandsHolderService: CommandsHolderService
) : DiscordEventListener() {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        coroutineLauncher.messageScope.launch {
            val componentId = try {
                UnleashedComponentId(event.componentId)
            } catch (e: IllegalArgumentException) {
                logger.debug(e) { "Invalid componentId: ${event.componentId}" }
                return@launch
            }

            val callbackData = interactivityManager.buttonInteractionCallbacks[componentId.uniqueId]
            val context = ComponentContext(event, interactivityManager)

            if (callbackData == null) {
                context.reply(true) {
                    styled("I don't know what to handle interaction event: $event", ":face_with_monocle:")
                }
                return@launch
            }

            try {
                callbackData.callback.invoke(context)
            } catch (e: Exception) {
                logger.warn(e) { "Button callback failed for ${componentId.uniqueId}" }
            }
        }
    }

    override fun onEntitySelectInteraction(event: EntitySelectInteractionEvent) {
        coroutineLauncher.messageScope.launch {
            val componentId = try {
                UnleashedComponentId(event.componentId)
            } catch (e: IllegalArgumentException) {
                logger.debug(e) { "Invalid componentId: ${event.componentId}" }
                return@launch
            }

            var context: ComponentContext?

            try {
                val guild = event.guild
                val member = event.member

                val callbackData = interactivityManager.selectMenuEntityInteractionCallbacks[componentId.uniqueId]
                context = ComponentContext(event, interactivityManager)

                if (callbackData == null) {
                    context.reply(true) {
                        styled("I don't know what to handle interaction event: $event", ":face_with_monocle:")
                    }
                    return@launch
                }

                context.alwaysEphemeral = callbackData.alwaysEphemeral

                try {
                    callbackData.callback.invoke(context, event.interaction.values)
                } catch (e: Exception) {
                    logger.warn(e) { "Button callback failed for ${componentId.uniqueId}" }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Something went wrong while executing select menu interaction!" }
            }
        }
    }
}