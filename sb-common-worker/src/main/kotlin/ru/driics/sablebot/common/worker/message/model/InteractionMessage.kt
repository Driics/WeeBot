package ru.driics.sablebot.common.worker.message.model

import dev.minn.jda.ktx.messages.InlineMessage
import dev.minn.jda.ktx.messages.MessageEdit
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.utils.messages.MessageEditData

/**
 * An interaction message that supports initial interaction message (which does NOT have the message data) and follow-up messages (which DO have the message data)
 */
interface InteractionMessage {
    /**
     * Retrieves the original interaction message.
     *
     * **Does not work with ephemeral messages!**
     */
    fun retrieveOriginal(): Message

    fun editMessage(builder: InlineMessage<MessageEditData>.() -> (Unit)): Message

    class InitialInteractionMessage(val hook: InteractionHook) : InteractionMessage {
        override fun retrieveOriginal(): Message = hook.retrieveOriginal().complete()

        override fun editMessage(builder: InlineMessage<MessageEditData>.() -> Unit): Message = hook.editOriginal(
            MessageEdit {
                builder()
            }
        ).complete()
    }

    class FollowUpInteractionMessage(val message: Message) : InteractionMessage {
        override fun retrieveOriginal() = message

        override fun editMessage(builder: InlineMessage<MessageEditData>.() -> Unit): Message = message.editMessage(
            MessageEdit {
                builder()
            }
        ).complete()
    }
}