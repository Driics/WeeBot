package ru.sablebot.common.worker.message.model

import dev.minn.jda.ktx.messages.InlineMessage
import dev.minn.jda.ktx.messages.MessageEdit
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.utils.messages.MessageEditData
import ru.sablebot.common.utils.await

class InteractionHook(val jdaHook: net.dv8tion.jda.api.interactions.InteractionHook) {
    suspend fun editOriginal(message: MessageEditData): Message {
        return jdaHook.editOriginal(message).await()
    }

    suspend fun editOriginal(message: InlineMessage<MessageEditData>.() -> (Unit)): Message = editOriginal(
        MessageEdit {
            message()
        }
    )
}