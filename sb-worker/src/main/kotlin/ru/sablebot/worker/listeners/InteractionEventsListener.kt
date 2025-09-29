package ru.sablebot.worker.listeners

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import ru.sablebot.common.support.CoroutineLauncher
import ru.sablebot.common.worker.event.DiscordEvent
import ru.sablebot.common.worker.event.listeners.DiscordEventListener
import ru.sablebot.common.worker.message.model.ComponentContext
import ru.sablebot.common.worker.message.model.InteractivityManager
import ru.sablebot.common.worker.message.model.UnleashedComponentId
import ru.sablebot.common.worker.message.model.modals.ModalArguments
import ru.sablebot.common.worker.message.model.modals.ModalContext
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
                logger.error(e) { "Button callback failed for ${componentId.uniqueId}" }
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
                logger.error(e) { "Entity select callback failed for ${componentId.uniqueId}" }
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
                logger.error(e) { "String select callback failed for ${componentId.uniqueId}" }
            }
        }
    }

    override fun onModalInteraction(event: ModalInteractionEvent) {
        GlobalScope.launch {
            val modalId = try {
                UnleashedComponentId(event.modalId)
            } catch (e: IllegalArgumentException) {
                logger.debug(e) { "Invalid modalId: ${event.modalId}" }
                return@launch
            }

            val callbackData = interactivityManager.modalCallbacks[modalId.uniqueId]
            val context = ModalContext(event)

            if (callbackData == null) {
                context.reply(true) {
                    styled("I don't know what to handle interaction event: $event", ":face_with_monocle:")
                }
                return@launch
            }

            context.alwaysEphemeral = callbackData.alwaysEphemeral // Inherit alwaysEphemeral from the callback data

            try {
                callbackData.callback.invoke(context, ModalArguments(event))
            } catch (e: Exception) {
                logger.error(e) { "Modal callback error for ${modalId.uniqueId}" }
            }
        }
    }
}