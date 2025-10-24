package ru.sablebot.common.worker.modules.audit.provider

import dev.minn.jda.ktx.messages.InlineEmbed
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import ru.sablebot.common.model.AuditActionType
import ru.sablebot.common.persistence.entity.AuditAction

@ForwardProvider(AuditActionType.VOICE_JOIN)
open class VoiceJoinAuditForwardProvider : VoiceAuditForwardProvider() {
    override fun build(action: AuditAction, messageBuilder: MessageCreateBuilder, embedBuilder: EmbedBuilder) {
        InlineEmbed(embedBuilder).apply {
            description = getUserMessage(action, "test")
            footer("Member ID: ${action.user.id}")
        }
    }
}