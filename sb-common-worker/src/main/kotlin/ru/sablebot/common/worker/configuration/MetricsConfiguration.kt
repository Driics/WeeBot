package ru.sablebot.common.worker.configuration

import org.springframework.context.annotation.Configuration

/**
 * Metrics configuration.
 *
 * JVM metrics (memory, GC, threads, CPU) are auto-configured by Spring Boot Actuator.
 * Common tags are configured in application.yml (management.metrics.tags).
 */
@Configuration(proxyBeanMethods = false)
open class MetricsConfiguration
