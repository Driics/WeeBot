package ru.driics.sablebot.common.worker.feature.provider

import org.springframework.stereotype.Component
import java.lang.annotation.Inherited

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Component
@MustBeDocumented
@Inherited
annotation class FeatureProvider(
    val priority: Int = 1
)