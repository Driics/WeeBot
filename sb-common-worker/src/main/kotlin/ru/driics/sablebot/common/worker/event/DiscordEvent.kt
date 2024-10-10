package ru.driics.sablebot.common.worker.event

import jakarta.annotation.Priority
import org.springframework.stereotype.Component
import java.lang.annotation.Inherited

@Component
@Inherited
@Target(AnnotationTarget.TYPE, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class DiscordEvent(
    val priority: Int = Int.MAX_VALUE,
)