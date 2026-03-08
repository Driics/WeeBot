package ru.sablebot.common.worker.metrics

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaConsumerFactoryCustomizer
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.MicrometerConsumerListener
import org.springframework.kafka.core.MicrometerProducerListener

@Configuration
open class KafkaMetricsConfiguration {

    @Bean
    open fun kafkaConsumerFactoryCustomizer(meterRegistry: MeterRegistry): DefaultKafkaConsumerFactoryCustomizer {
        return DefaultKafkaConsumerFactoryCustomizer { factory ->
            factory.addListener(MicrometerConsumerListener(meterRegistry))
        }
    }

    @Bean
    open fun kafkaProducerFactoryCustomizer(meterRegistry: MeterRegistry): DefaultKafkaProducerFactoryCustomizer {
        return DefaultKafkaProducerFactoryCustomizer { factory ->
            factory.addListener(MicrometerProducerListener(meterRegistry))
        }
    }
}
