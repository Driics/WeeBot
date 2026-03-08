package ru.sablebot.common.worker.modules.audit.provider

import dev.minn.jda.ktx.messages.InlineEmbed
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import ru.sablebot.common.model.AuditActionType
import ru.sablebot.common.persistence.entity.AuditAction

@ForwardProvider(AuditActionType.MEMBER_LEAVE)
open class MemberLeaveAuditForwardProvider : LoggingAuditForwardProvider() {
    override fun build(
        action: AuditAction,
        messageBuilder: MessageCreateBuilder,
        embedBuilder: EmbedBuilder
    ) {
        InlineEmbed(embedBuilder).apply {
            val message = messageService.getMessage(
                "audit.member.leave.message",
                getReferenceContent(action.user, false)
            )

            description = message
            footer(messageService.getMessage("audit.member.id", action.user.id) ?: "")
        }
    }
}