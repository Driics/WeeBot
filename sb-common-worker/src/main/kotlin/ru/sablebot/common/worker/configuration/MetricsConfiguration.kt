package ru.sablebot.common.worker.configuration

import com.codahale.metrics.ConsoleReporter
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.jmx.JmxReporter
import com.codahale.metrics.jvm.JvmAttributeGaugeSet
import com.ryantenney.metrics.spring.config.annotation.EnableMetrics
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@EnableMetrics
@Configuration
open class MetricsConfiguration {
    @Autowired
    private lateinit var metricRegistry: MetricRegistry

    @Bean(destroyMethod = "stop")
    open fun consoleReporter(): ConsoleReporter =
        ConsoleReporter
            .forRegistry(metricRegistry)
            .build()
            .apply {
                start(1, TimeUnit.DAYS)
            }

    @Bean(destroyMethod = "stop")
    open fun jmxReporter(): JmxReporter =
        JmxReporter
            .forRegistry(metricRegistry)
            .build()
            .apply {
                start()
            }

    @Bean
    open fun jvmGauge(): JvmAttributeGaugeSet {
        val jvmMetrics = JvmAttributeGaugeSet()
        metricRegistry.register("jvm", jvmMetrics)
        return jvmMetrics
    }


}