package ru.sablebot.common.worker.modules.audit.provider

import ru.sablebot.common.persistence.entity.AuditAction

abstract class VoiceAuditForwardProvider : LoggingAuditForwardProvider() {
    protected fun getUserMessage(
        action: AuditAction,
        key: String
    ): String? = messageService.getMessage(
        key,
        getReferenceContent(action.user, false),
        action.channel.asChannelMention
    )
}