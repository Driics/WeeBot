package ru.sablebot.common.worker.modules.audit.provider

import dev.minn.jda.ktx.messages.InlineEmbed
import net.dv8tion.jda.api.EmbedBuilder
import ru.sablebot.common.persistence.entity.AuditAction

abstract class MessageAuditForwardProvider : LoggingAuditForwardProvider() {
    companion object {
        const val OLD_MESSAGE_CONTENT = "oldMessageContent"
        const val MESSAGE_ID_CONTENT = "message_id"
    }

    protected fun addAuthorField(
        action: AuditAction,
        embedBuilder: EmbedBuilder,
    ) {
        InlineEmbed(embedBuilder).apply {
            field(
                "Author:",
                getReferenceContent(action.user, false) ?: "err",
                true
            )
        }
    }

    protected fun addOldContentField(
        action: AuditAction,
        embedBuilder: EmbedBuilder,
    ) {
        InlineEmbed(embedBuilder).apply {
            field(
                "OldContent:",
                getMessageValue(action, OLD_MESSAGE_CONTENT),
                false
            )
        }
    }

    protected fun getMessageValue(
        action: AuditAction,
        key: String
    ): String {
        val oldContent = action.getAttribute(key, String::class.java) ?: return "-"
        return if (oldContent.isBlank()) "empty" else "```${oldContent}```"
    }
}