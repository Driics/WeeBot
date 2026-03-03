package ru.sablebot.common.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.producer.ProducerInterceptor
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.*
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer

@EnableKafka
@Configuration
@EnableConfigurationProperties(KafkaProperties::class)
class KafkaConfiguration(
    private val kafkaProperties: KafkaProperties
) {
    private val logger = LoggerFactory.getLogger(KafkaConfiguration::class.java)

    @Bean
    fun objectMapper(): ObjectMapper = jacksonObjectMapper()

    @Bean
    fun kafkaAdmin(): KafkaAdmin = KafkaAdmin(
        mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaProperties.bootstrapServers)
    )

    @Bean
    fun producerFactory(): ProducerFactory<String, Any> =
        DefaultKafkaProducerFactory(buildProducerConfig())

    @Bean
    fun kafkaTemplate(producerFactory: ProducerFactory<String, Any>): KafkaTemplate<String, Any> =
        KafkaTemplate(producerFactory)

    @Bean
    fun consumerFactory(objectMapper: ObjectMapper): ConsumerFactory<String, Any> =
        createConsumerFactory(kafkaProperties.consumer.workerGroupId, objectMapper)

    @Bean
    fun replyConsumerFactory(objectMapper: ObjectMapper): ConsumerFactory<String, Any> =
        createConsumerFactory(kafkaProperties.consumer.replyGroupId, objectMapper)

    @Bean
    fun kafkaListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, Any>,
        kafkaTemplate: KafkaTemplate<String, Any>
    ): ConcurrentKafkaListenerContainerFactory<String, Any> =
        ConcurrentKafkaListenerContainerFactory<String, Any>().apply {
            this.consumerFactory = consumerFactory
            setReplyTemplate(kafkaTemplate)
            setCommonErrorHandler(buildErrorHandler(kafkaTemplate))
            setRecordInterceptor { record, _ ->
                record.headers().lastHeader("x-trace-id")?.let { header ->
                    MDC.put("traceId", String(header.value(), Charsets.UTF_8))
                }
                record
            }
        }

    @Bean
    fun replyContainer(
        replyConsumerFactory: ConsumerFactory<String, Any>
    ): ConcurrentMessageListenerContainer<String, Any> {
        val containerProps = ContainerProperties(*KafkaTopics.replies).apply {
            groupId = kafkaProperties.consumer.replyGroupId
        }
        return ConcurrentMessageListenerContainer(replyConsumerFactory, containerProps)
    }

    @Bean
    fun replyingKafkaTemplate(
        producerFactory: ProducerFactory<String, Any>,
        replyContainer: ConcurrentMessageListenerContainer<String, Any>
    ): ReplyingKafkaTemplate<String, Any, Any> =
        ReplyingKafkaTemplate(producerFactory, replyContainer)

    @Bean
    fun kafkaTopics(): List<NewTopic> = KafkaTopics.all.map { name ->
        TopicBuilder.name(name)
            .partitions(kafkaProperties.topic.partitions)
            .replicas(kafkaProperties.topic.replicas)
            .build()
    }

    private fun buildProducerConfig(): Map<String, Any> = mapOf(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaProperties.bootstrapServers,
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
        ProducerConfig.ACKS_CONFIG to kafkaProperties.producer.acks,
        ProducerConfig.RETRIES_CONFIG to kafkaProperties.producer.retries,
        ProducerConfig.INTERCEPTOR_CLASSES_CONFIG to listOf(TraceIdProducerInterceptor::class.java.name),
        JsonSerializer.ADD_TYPE_INFO_HEADERS to true
    )

    private fun createConsumerFactory(
        groupId: String,
        objectMapper: ObjectMapper
    ): ConsumerFactory<String, Any> {
        val config = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaProperties.bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to groupId,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to kafkaProperties.consumer.autoOffsetReset
        )

        val jsonDeserializer = JsonDeserializer<Any>(objectMapper).apply {
            addTrustedPackages(*kafkaProperties.consumer.trustedPackages.toTypedArray())
            setUseTypeHeaders(true)
        }

        return DefaultKafkaConsumerFactory(
            config,
            ErrorHandlingDeserializer(StringDeserializer()),
            ErrorHandlingDeserializer(jsonDeserializer)
        )
    }

    private fun buildErrorHandler(kafkaTemplate: KafkaTemplate<String, Any>): DefaultErrorHandler {
        val backOff = ExponentialBackOffWithMaxRetries(
            kafkaProperties.retry.maxRetries
        ).apply {
            initialInterval = kafkaProperties.retry.initialInterval
            multiplier = kafkaProperties.retry.multiplier
            maxInterval = kafkaProperties.retry.maxInterval
        }

        val recoverer = buildDeadLetterRecoverer(kafkaTemplate)

        return DefaultErrorHandler(recoverer, backOff).apply {
            setRetryListeners({ record, ex, deliveryAttempt ->
                logger.warn(
                    "Retry attempt {} for topic={}, partition={}, offset={}, exception={}",
                    deliveryAttempt,
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    ex.message
                )
            })
        }
    }

    private fun buildDeadLetterRecoverer(
        kafkaTemplate: KafkaTemplate<String, Any>
    ): DeadLetterPublishingRecoverer {
        val destinationResolver: (ConsumerRecord<*, *>, Exception) -> TopicPartition = { record, ex ->
            val deadLetterTopic = if (kafkaProperties.deadLetter.useSingleTopic) {
                KafkaTopics.DEAD_LETTER
            } else {
                KafkaTopics.deadLetterTopicFor(record.topic())
            }

            logger.error(
                "Sending record to DLT: topic={}, partition={}, offset={}, dlt={}, exception={}",
                record.topic(),
                record.partition(),
                record.offset(),
                deadLetterTopic,
                ex.message
            )

            TopicPartition(deadLetterTopic, -1) // -1 = use default partitioning
        }

        return DeadLetterPublishingRecoverer(kafkaTemplate, destinationResolver).apply {
            setHeadersFunction { _, exception ->
                // Add custom headers to DLT message for debugging
                org.springframework.kafka.support.KafkaHeaders.DLT_EXCEPTION_MESSAGE
                mapOf(
                    "x-original-exception" to (exception.message ?: "Unknown error").toByteArray(),
                    "x-failed-timestamp" to System.currentTimeMillis().toString().toByteArray()
                ).entries.fold(org.apache.kafka.common.header.internals.RecordHeaders()) { headers, entry ->
                    headers.add(entry.key, entry.value)
                    headers
                }
            }
        }
    }
}

class TraceIdProducerInterceptor : ProducerInterceptor<String, Any> {
    override fun onSend(record: ProducerRecord<String, Any>): ProducerRecord<String, Any> {
        MDC.get("traceId")?.let { traceId ->
            record.headers().add("x-trace-id", traceId.toByteArray(Charsets.UTF_8))
        }
        return record
    }

    override fun onAcknowledgement(metadata: RecordMetadata?, exception: Exception?) {}
    override fun close() {}
    override fun configure(configs: MutableMap<String, *>?) {}
}