package ru.sablebot.common.configuration

import org.springframework.amqp.core.AmqpAdmin
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.annotation.EnableRabbit
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@EnableRabbit
@Configuration
class RabbitConfiguration(
    private val commonProperties: CommonProperties
) {

    companion object {
        const val QUEUE_GUILD_INFO_REQUEST = "sablebot.guild.info.request"
        const val QUEUE_RANKING_UPDATE_REQUEST = "sablebot.ranking.update.request"
        const val QUEUE_COMMAND_LIST_REQUEST = "sablebot.command.list.request"
        const val QUEUE_STATUS_REQUEST = "sablebot.status.request"
        const val QUEUE_WEBHOOK_GET_REQUEST = "sablebot.webhook.get.request"
        const val QUEUE_WEBHOOK_UPDATE_REQUEST = "sablebot.webhook.update.request"
        const val QUEUE_WEBHOOK_DELETE_REQUEST = "sablebot.webhook.delete.request"
        const val QUEUE_PATREON_WEBHOOK_REQUEST = "sablebot.patreon.webhook.request"
        const val QUEUE_CHECK_OWNER_REQUEST = "sablebot.check.owner.request"
        const val QUEUE_CACHE_EVICT_REQUEST = "sablebot.cache.evict.request"
    }

    @Bean
    fun connectionFactory(): ConnectionFactory {
        val rabbitMQ = commonProperties.rabbitMQ
        return CachingConnectionFactory(rabbitMQ.hostname, rabbitMQ.port).apply {
            if (rabbitMQ.username.isNotBlank() && rabbitMQ.password.isNotBlank()) {
                username = rabbitMQ.username
                setPassword(rabbitMQ.password)
            }
        }
    }

    @Bean
    fun rabbitListenerContainerFactory(): RabbitListenerContainerFactory<SimpleMessageListenerContainer> {
        return SimpleRabbitListenerContainerFactory().apply {
            setConnectionFactory(connectionFactory())
            setDefaultRequeueRejected(false)
            setMessageConverter(messageConverter())
        }
    }

    @Bean
    fun amqpAdmin(): AmqpAdmin = RabbitAdmin(connectionFactory())

    @Bean
    fun rabbitTemplate(): RabbitTemplate = RabbitTemplate(connectionFactory()).apply {
        messageConverter = messageConverter()
    }

    @Bean
    fun messageConverter(): MessageConverter = Jackson2JsonMessageConverter()

    @Bean
    fun guildInfoRequest(): Queue = Queue(QUEUE_GUILD_INFO_REQUEST)

    @Bean
    fun rankingUpdateRewardsQueue(): Queue = Queue(QUEUE_RANKING_UPDATE_REQUEST)
    @Bean
    fun commandListRequest(): Queue = Queue(QUEUE_COMMAND_LIST_REQUEST)
    @Bean
    fun statusRequest(): Queue = Queue(QUEUE_STATUS_REQUEST)
    @Bean
    fun webhookGetRequest(): Queue = Queue(QUEUE_WEBHOOK_GET_REQUEST)
    @Bean
    fun webhookUpdateRequest(): Queue = Queue(QUEUE_WEBHOOK_UPDATE_REQUEST)
    @Bean
    fun webhookDeleteRequest(): Queue = Queue(QUEUE_WEBHOOK_DELETE_REQUEST)
    @Bean
    fun patreonWebhookRequest(): Queue = Queue(QUEUE_PATREON_WEBHOOK_REQUEST)
    @Bean
    fun checkOwnerRequest(): Queue = Queue(QUEUE_CHECK_OWNER_REQUEST)
    @Bean
    fun cacheEvictRequest(): Queue = Queue(QUEUE_CACHE_EVICT_REQUEST)
}
