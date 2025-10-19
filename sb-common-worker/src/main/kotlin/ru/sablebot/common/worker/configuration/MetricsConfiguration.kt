package ru.sablebot.common.worker.configuration

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
open class MetricsConfiguration {
    /**
     * Customizes the global MeterRegistry with application-specific tags
     */
    @Bean
    fun metricsCommonTags(): MeterRegistryCustomizer<MeterRegistry> {
        return MeterRegistryCustomizer { registry ->
            registry.config().commonTags("application", "sablebot")
        }
    }

    @Bean
    fun jvmMemoryMetrics(): JvmMemoryMetrics = JvmMemoryMetrics()

    @Bean
    fun jvmGcMetrics(): JvmGcMetrics = JvmGcMetrics()

    @Bean
    fun jvmThreadMetrics(): JvmThreadMetrics = JvmThreadMetrics()

    @Bean
    fun processorMetrics(): ProcessorMetrics = ProcessorMetrics()
}