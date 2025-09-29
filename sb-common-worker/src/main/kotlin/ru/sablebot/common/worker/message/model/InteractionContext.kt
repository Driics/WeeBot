package ru.sablebot.common.worker.message.model

import dev.minn.jda.ktx.messages.InlineMessage
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import ru.sablebot.common.worker.command.model.context.UnleashedContext
import java.util.*

abstract class InteractionContext(
    private val replyCallback: IReplyCallback
) : UnleashedContext(
    discordGuildLocale = if (replyCallback.isFromGuild) replyCallback.guildLocale else null,
    discordUserLocale = replyCallback.userLocale,
    jda = replyCallback.jda,
    user = replyCallback.user,
    memberOrNull = replyCallback.member,
    guildOrNull = replyCallback.guild,
    channelOrNull = replyCallback.messageChannel,
    discordInteractionOrNull = replyCallback.hook.interaction
) {
    override fun deferChannelMessage(ephemeral: Boolean): InteractionHook {
        val realEphemeralState = if (alwaysEphemeral) true else ephemeral

        // TODO: maybe use await instead complete?
        val hook = replyCallback.deferReply().setEphemeral(realEphemeralState).complete()

        return InteractionHook(hook)
    }

    override fun reply(
        ephemeral: Boolean,
        builder: InlineMessage<MessageCreateData>.() -> Unit
    ): InteractionMessage {
        val createdMessage = InlineMessage(MessageCreateBuilder()).apply {
            allowedMentionTypes = EnumSet.of(
                Message.MentionType.CHANNEL,
                Message.MentionType.EMOJI,
                Message.MentionType.SLASH_COMMAND
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

    override fun reply(ephemeral: Boolean, builder: MessageCreateData): InteractionMessage {
        val realEphemeralState = if (alwaysEphemeral) true else ephemeral

        // We could actually disable the components when their state expires, however this is hard to track due to "@original" or ephemeral messages not having an ID associated with it
        // So, if the message is edited, we don't know if we *can* disable the components when their state expires!
        return if (replyCallback.isAcknowledged) {
            val message = replyCallback.hook.sendMessage(builder).setEphemeral(realEphemeralState).complete()
            InteractionMessage.FollowUpInteractionMessage(message)
        } else {
            val hook = replyCallback.reply(builder).setEphemeral(realEphemeralState).complete()
            InteractionMessage.InitialInteractionMessage(hook)
        }
    }
}