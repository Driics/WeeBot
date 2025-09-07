package ru.driics.sablebot.common.worker.modules.audit.provider

import ru.driics.sablebot.common.persistence.entity.AuditAction
import ru.driics.sablebot.common.persistence.entity.AuditConfig

interface AuditForwardProvider {
    fun send(
        config: AuditConfig,
        action: AuditAction,
        attachments: Map<String, ByteArray>
    )
}