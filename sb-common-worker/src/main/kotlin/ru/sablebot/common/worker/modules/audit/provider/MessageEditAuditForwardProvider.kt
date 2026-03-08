package ru.sablebot.common.worker.modules.audit.provider

import dev.minn.jda.ktx.messages.InlineEmbed
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import ru.sablebot.common.model.AuditActionType
import ru.sablebot.common.persistence.entity.AuditAction

@ForwardProvider(AuditActionType.MESSAGE_EDIT)
open class MessageEditAuditForwardProvider : MessageAuditForwardProvider() {
    companion object {
        const val NEW_MESSAGE_CONTENT = "newMessageContent"
    }

    override fun build(
        action: AuditAction,
        messageBuilder: MessageCreateBuilder,
        embedBuilder: EmbedBuilder
    ) {
        val messageId = action.getAttribute(MESSAGE_ID_CONTENT, String::class.java)
            ?: return

        InlineEmbed(embedBuilder).apply {
            description = action.channel.id

            addOldContentField(action, embedBuilder)
            field(
                "NewContent",
                getMessageValue(action, NEW_MESSAGE_CONTENT),
                false
            )

            addAuthorField(action, embedBuilder)
            addChannelField(action, embedBuilder)

            footer("ID: $messageId")
        }
    }
}