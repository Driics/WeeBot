package ru.sablebot.common.worker.configuration

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class MetricsConfiguration {
    /**
     * Customizes the global MeterRegistry with application-specific tags
     */
    @Bean
    open fun metricsCommonTags(): MeterRegistryCustomizer<MeterRegistry> {
        return MeterRegistryCustomizer { registry ->
            registry.config().commonTags("application", "sablebot")
        }
    }

    @Bean
    open fun jvmMemoryMetrics(): JvmMemoryMetrics = JvmMemoryMetrics()

    @Bean
    open fun jvmGcMetrics(): JvmGcMetrics = JvmGcMetrics()

    @Bean
    open fun jvmThreadMetrics(): JvmThreadMetrics = JvmThreadMetrics()

    @Bean
    open fun processorMetrics(): ProcessorMetrics = ProcessorMetrics()
}