package ru.sablebot.common.worker.message.model.modals

import net.dv8tion.jda.api.interactions.modals.ModalInteraction
import ru.sablebot.common.utils.await
import ru.sablebot.common.worker.message.model.InteractionContext
import ru.sablebot.common.worker.message.model.InteractionHook

class ModalContext(
    val event: ModalInteraction
) : InteractionContext(event) {
    suspend fun deferEdit() = InteractionHook(event.deferEdit().await())
}