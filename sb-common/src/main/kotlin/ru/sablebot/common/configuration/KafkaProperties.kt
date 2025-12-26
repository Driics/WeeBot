package ru.sablebot.common.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sablebot.kafka")
data class KafkaProperties(
    val bootstrapServers: String = "localhost:9092",
    val consumer: ConsumerProperties = ConsumerProperties(),
    val producer: ProducerProperties = ProducerProperties(),
    val topic: TopicProperties = TopicProperties(),
    val retry: RetryProperties = RetryProperties()
) {
    data class ConsumerProperties(
        val workerGroupId: String = "sablebot-worker-group",
        val replyGroupId: String = "sablebot-reply-group",
        val autoOffsetReset: String = "latest",
        val trustedPackages: List<String> = listOf("ru.sablebot.common.model", "java.lang")
    )

    data class ProducerProperties(
        val acks: String = "all",
        val retries: Int = 3
    )

    data class TopicProperties(
        val partitions: Int = 3,
        val replicas: Int = 1
    )

    data class RetryProperties(
        val maxRetries: Int = 10,
        val initialInterval: Long = 200L,
        val multiplier: Double = 2.0,
        val maxInterval: Long = 5000L
    )
}