package ru.driics.sablebot.common.worker.command.model

import dev.minn.jda.ktx.messages.InlineMessage
import net.dv8tion.jda.api.entities.Message.MentionType
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import ru.driics.sablebot.common.worker.message.model.InteractionMessage
import java.util.*

class BotContext(
    private val replyCallback: IReplyCallback
) {
    fun reply(
        ephemeral: Boolean,
        builder: InlineMessage<MessageCreateData>.() -> Unit
    ): InteractionMessage {
        val createdMessage = InlineMessage(MessageCreateBuilder()).apply {
            allowedMentionTypes = EnumSet.of(
                MentionType.CHANNEL,
                MentionType.EMOJI,
                MentionType.SLASH_COMMAND
            )

            builder()
        }.build()

        return if (replyCallback.isAcknowledged) {
            val message = replyCallback.hook.sendMessage(createdMessage).setEphemeral(ephemeral).complete()
            InteractionMessage.FollowUpInteractionMessage(message)
        } else {
            val hook = replyCallback.reply(createdMessage).setEphemeral(ephemeral).complete()
            InteractionMessage.InitialInteractionMessage(hook)
        }
    }
}