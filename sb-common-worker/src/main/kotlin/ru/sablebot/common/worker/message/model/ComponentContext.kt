package ru.sablebot.common.worker.message.model

import dev.minn.jda.ktx.interactions.components.replyModal
import dev.minn.jda.ktx.messages.InlineMessage
import dev.minn.jda.ktx.messages.MessageEditBuilder
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.ComponentInteraction
import net.dv8tion.jda.api.utils.messages.MessageEditData
import ru.sablebot.common.utils.await
import ru.sablebot.common.worker.message.model.modals.ModalArguments
import ru.sablebot.common.worker.message.model.modals.ModalContext
import java.util.*
import java.util.concurrent.CompletableFuture

class ComponentContext(
    val event: ComponentInteraction,
    val interactivityManager: InteractivityManager
) : InteractionContext(event) {

    suspend fun deferEdit(): InteractionHook = event.deferEdit().await()

    fun deferEditAsync(): CompletableFuture<InteractionHook> = event.deferEdit().submit()

    /**
     * Edits the message that invoked the action
     */
    suspend inline fun editMessage(isReplace: Boolean = false, action: InlineMessage<*>.() -> (Unit)) =
        editMessage(isReplace, MessageEditBuilder { apply(action) }.build())


    /**
     * Edits the message that invoked the action
     */
    suspend fun editMessage(
        isReplace: Boolean = false,
        messageEditData: MessageEditData
    ): ru.sablebot.common.worker.message.model.InteractionHook {
        return InteractionHook(event.editMessage(messageEditData).apply { this.isReplace = isReplace }
            .await())
    }

    suspend fun sendModal(
        title: String,
        components: List<ActionRow>,
        callback: suspend (ModalContext, ModalArguments) -> (Unit)
    ) {
        val unleashedComponentId = UnleashedComponentId(UUID.randomUUID())
        interactivityManager.modalCallbacks[unleashedComponentId.uniqueId] =
            InteractivityManager.ModalInteractionCallback(true, callback)
        event.replyModal(
            unleashedComponentId.toString(),
            title,
            components
        ).await()
    }
}