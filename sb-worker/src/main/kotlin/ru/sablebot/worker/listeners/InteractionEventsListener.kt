package ru.sablebot.worker.listeners

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.DelicateCoroutinesApi
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import ru.sablebot.common.support.CoroutineLauncher
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
) : DiscordEventListener() {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        coroutineLauncher.launchMessageJob(event) {
            val componentId = try {
                UnleashedComponentId(event.componentId)
            } catch (e: IllegalArgumentException) {
                logger.debug(e) { "Invalid componentId: ${event.componentId}" }
                return@launchMessageJob
            }

            val callbackData = interactivityManager.buttonInteractionCallbacks[componentId.uniqueId]
            val context = ComponentContext(event, interactivityManager)

            if (callbackData == null) {
                context.reply(true) {
                    styled("I don't know what to handle interaction event: $event", ":face_with_monocle:")
                }
                return@launchMessageJob
            }

            context.alwaysEphemeral = callbackData.alwaysEphemeral

            try {
                callbackData.callback.invoke(context)
            } catch (e: Exception) {
                logger.warn(e) { "Button callback failed for ${componentId.uniqueId}" }
            }
        }
    }

    override fun onEntitySelectInteraction(event: EntitySelectInteractionEvent) {
        coroutineLauncher.launchMessageJob(event) {
            val componentId = try {
                UnleashedComponentId(event.componentId)
            } catch (e: IllegalArgumentException) {
                logger.debug(e) { "Invalid componentId: ${event.componentId}" }
                return@launchMessageJob
            }


            val callbackData = interactivityManager.selectMenuEntityInteractionCallbacks[componentId.uniqueId]
            val context = ComponentContext(event, interactivityManager)

            if (callbackData == null) {
                context.reply(true) {
                    styled("I don't know what to handle interaction event: $event", ":face_with_monocle:")
                }
                return@launchMessageJob
            }

            context.alwaysEphemeral = callbackData.alwaysEphemeral

            try {
                callbackData.callback.invoke(context, event.interaction.values)
            } catch (e: Exception) {
                logger.warn(e) { "Entity select callback failed for ${componentId.uniqueId}" }
            }
        }
    }

    override fun onStringSelectInteraction(event: StringSelectInteractionEvent) {
        coroutineLauncher.launchMessageJob(event) {
            val componentId = try {
                UnleashedComponentId(event.componentId)
            } catch (e: IllegalArgumentException) {
                logger.debug(e) { "Invalid componentId: ${event.componentId}" }
                return@launchMessageJob
            }

            val callbackData = interactivityManager.selectMenuInteractionCallbacks[componentId.uniqueId]
            val context = ComponentContext(event, interactivityManager)

            if (callbackData == null) {
                context.reply(true) {
                    styled("I don't know what to handle interaction event: $event", ":face_with_monocle:")
                }
                return@launchMessageJob
            }

            context.alwaysEphemeral = callbackData.alwaysEphemeral

            try {
                callbackData.callback.invoke(context, event.interaction.values)
            } catch (e: Exception) {
                logger.warn(e) { "String select callback failed for ${componentId.uniqueId}" }
            }
        }
    }
}