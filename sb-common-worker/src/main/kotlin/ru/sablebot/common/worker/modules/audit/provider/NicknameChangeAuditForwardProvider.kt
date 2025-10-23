package ru.sablebot.common.worker.modules.audit.provider

import dev.minn.jda.ktx.messages.InlineEmbed
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import ru.sablebot.common.model.AuditActionType
import ru.sablebot.common.persistence.entity.AuditAction

@ForwardProvider(AuditActionType.MEMBER_NAME_CHANGE)
class NicknameChangeAuditForwardProvider : LoggingAuditForwardProvider() {
    companion object {
        const val OLD_NAME = "old"
        const val NEW_NAME = "new"
    }

    override fun build(
        action: AuditAction,
        messageBuilder: MessageCreateBuilder,
        embedBuilder: EmbedBuilder
    ) {
        InlineEmbed(embedBuilder).apply {
            field(
                "Old Nickname",
                action.getAttribute(OLD_NAME, String::class.java) ?: "-",
                true
            )

            field(
                "New Nickname",
                action.getAttribute(NEW_NAME, String::class.java) ?: "-",
                true
            )

            description = "Member ID: ${action.user.id}"
        }
    }
}