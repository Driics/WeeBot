package ru.sablebot.common.configuration

import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.*
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer

@EnableKafka
@Configuration
class KafkaConfiguration(
    private val commonProperties: CommonProperties
) {
    companion object {
        // Topics (previously queues)
        const val TOPIC_GUILD_INFO_REQUEST = "sablebot.guild.info.request"
        const val TOPIC_RANKING_UPDATE_REQUEST = "sablebot.ranking.update.request"
        const val TOPIC_COMMAND_LIST_REQUEST = "sablebot.command.list.request"
        const val TOPIC_STATUS_REQUEST = "sablebot.status.request"
        const val TOPIC_WEBHOOK_GET_REQUEST = "sablebot.webhook.get.request"
        const val TOPIC_WEBHOOK_UPDATE_REQUEST = "sablebot.webhook.update.request"
        const val TOPIC_WEBHOOK_DELETE_REQUEST = "sablebot.webhook.delete.request"
        const val TOPIC_PATREON_WEBHOOK_REQUEST = "sablebot.patreon.webhook.request"
        const val TOPIC_CHECK_OWNER_REQUEST = "sablebot.check.owner.request"
        const val TOPIC_CACHE_EVICT_REQUEST = "sablebot.cache.evict.request"

        // Reply topics for request-reply pattern
        const val TOPIC_STATUS_REPLY = "sablebot.status.reply"
        const val TOPIC_CHECK_OWNER_REPLY = "sablebot.check.owner.reply"
    }

    @Bean
    fun producerFactory(): ProducerFactory<String, Any> {
        val kafka = commonProperties.kafka

        val configProps = mutableMapOf<String, Any>(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.RETRIES_CONFIG to 3,
            JsonSerializer.ADD_TYPE_INFO_HEADERS to true
        )

        return DefaultKafkaProducerFactory(configProps)
    }

    @Bean
    fun kafkaAdmin(): KafkaAdmin = KafkaAdmin(
        mapOf(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to commonProperties.kafka.bootstrapServers,
        )
    )

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, Any> = KafkaTemplate(producerFactory())

    @Bean
    fun consumerFactory(): ConsumerFactory<String, Any> {
        val kafka = commonProperties.kafka

        val props = mutableMapOf<String, Any>(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "sablebot-worker-group",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to JsonDeserializer::class.java,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "latest",
            JsonDeserializer.TRUSTED_PACKAGES to "ru.sablebot.common.model.*,java.lang",  // ← Add java.lang for String
            JsonDeserializer.USE_TYPE_INFO_HEADERS to true
        )

        return DefaultKafkaConsumerFactory(props)
    }

    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, Any> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, Any>()
        factory.consumerFactory = consumerFactory()
        factory.setReplyTemplate(kafkaTemplate())
        factory.setCommonErrorHandler(
            DefaultErrorHandler(ExponentialBackOffWithMaxRetries(10).apply {
                initialInterval = 200L; multiplier = 2.0; maxInterval = 5000L
            })
        )
        return factory
    }

    @Bean
    fun replyingKafkaTemplate(): ReplyingKafkaTemplate<String, Any, Any> =
        ReplyingKafkaTemplate(producerFactory(), replyContainer())

    @Bean
    fun replyConsumerFactory(): ConsumerFactory<String, Any> {
        val kafka = commonProperties.kafka

        val props = mutableMapOf<String, Any>(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "sablebot-reply-group",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to JsonDeserializer::class.java,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "latest",
            JsonDeserializer.TRUSTED_PACKAGES to "ru.sablebot.common.model.*,java.lang",
            JsonDeserializer.USE_TYPE_INFO_HEADERS to true
        )

        return DefaultKafkaConsumerFactory(props)
    }

    @Bean
    fun replyContainer(): ConcurrentMessageListenerContainer<String, Any> {
        val containerProps = ContainerProperties(TOPIC_STATUS_REPLY, TOPIC_CHECK_OWNER_REPLY)
        containerProps.groupId = "sablebot-reply-group"

        return ConcurrentMessageListenerContainer(replyConsumerFactory(), containerProps)
    }

    @Bean
    fun guildInfoRequestTopic(): NewTopic = TopicBuilder.name(TOPIC_GUILD_INFO_REQUEST)
        .partitions(3).replicas(1).build()

    @Bean
    fun rankingUpdateRequestTopic(): NewTopic = TopicBuilder.name(TOPIC_RANKING_UPDATE_REQUEST)
        .partitions(3).replicas(1).build()

    @Bean
    fun commandListRequestTopic(): NewTopic = TopicBuilder.name(TOPIC_COMMAND_LIST_REQUEST)
        .partitions(3).replicas(1).build()

    @Bean
    fun statusRequestTopic(): NewTopic = TopicBuilder.name(TOPIC_STATUS_REQUEST)
        .partitions(3).replicas(1).build()

    @Bean
    fun statusReplyTopic(): NewTopic = TopicBuilder.name(TOPIC_STATUS_REPLY)
        .partitions(3).replicas(1).build()

    @Bean
    fun webhookGetRequestTopic(): NewTopic = TopicBuilder.name(TOPIC_WEBHOOK_GET_REQUEST)
        .partitions(3).replicas(1).build()

    @Bean
    fun webhookUpdateRequestTopic(): NewTopic = TopicBuilder.name(TOPIC_WEBHOOK_UPDATE_REQUEST)
        .partitions(3).replicas(1).build()

    @Bean
    fun webhookDeleteRequestTopic(): NewTopic = TopicBuilder.name(TOPIC_WEBHOOK_DELETE_REQUEST)
        .partitions(3).replicas(1).build()

    @Bean
    fun patreonWebhookRequestTopic(): NewTopic = TopicBuilder.name(TOPIC_PATREON_WEBHOOK_REQUEST)
        .partitions(3).replicas(1).build()

    @Bean
    fun checkOwnerRequestTopic(): NewTopic = TopicBuilder.name(TOPIC_CHECK_OWNER_REQUEST)
        .partitions(3).replicas(1).build()

    @Bean
    fun checkOwnerReplyTopic(): NewTopic = TopicBuilder.name(TOPIC_CHECK_OWNER_REPLY)
        .partitions(3).replicas(1).build()

    @Bean
    fun cacheEvictRequestTopic(): NewTopic = TopicBuilder.name(TOPIC_CACHE_EVICT_REQUEST)
        .partitions(3).replicas(1).build()
}