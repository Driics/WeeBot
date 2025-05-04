package ru.driics.sablebot.common.worker.modules.audit.provider

import org.springframework.stereotype.Component
import ru.driics.sablebot.common.model.AuditActionType
import java.lang.annotation.Inherited

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Inherited
@Component
annotation class ForwardProvider(
    val value: AuditActionType
)