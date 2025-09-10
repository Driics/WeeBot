package ru.sablebot.common.worker.modules.audit.provider

import org.springframework.stereotype.Component

@Component
abstract class ModerationAuditForwardProvider : LoggingAuditForwardProvider() {
    companion object {
        const val REASON_ATTR = "reason"
        const val GLOBAL_ATTR = "global"
        const val DURATION_ATTR = "duration"
        const val DURATION_MS_ATTR = "duration_ms"
    }
}