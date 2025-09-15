package ru.sablebot.common.configuration

import org.springframework.amqp.core.AmqpAdmin
import org.springframework.amqp.core.Queue
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

@Configuration
open class RabbitConfiguration(
    private val commonProperties: CommonProperties
) {

    companion object {
        const val QUEUE_GUILD_INFO_REQUEST = "juniperbot.guild.info.request"
        const val QUEUE_RANKING_UPDATE_REQUEST = "juniperbot.ranking.update.request"
        const val QUEUE_COMMAND_LIST_REQUEST = "juniperbot.command.list.request"
        const val QUEUE_STATUS_REQUEST = "juniperbot.status.request"
        const val QUEUE_WEBHOOK_GET_REQUEST = "juniperbot.webhook.get.request"
        const val QUEUE_WEBHOOK_UPDATE_REQUEST = "juniperbot.webhook.update.request"
        const val QUEUE_WEBHOOK_DELETE_REQUEST = "juniperbot.webhook.delete.request"
        const val QUEUE_PATREON_WEBHOOK_REQUEST = "juniperbot.patreon.webhook.request"
        const val QUEUE_CHECK_OWNER_REQUEST = "juniperbot.check.owner.request"
        const val QUEUE_CACHE_EVICT_REQUEST = "juniperbot.cache.evict.request"
    }

    @Bean
    open fun connectionFactory(): ConnectionFactory {
        val rabbitMQ = commonProperties.rabbitMQ
        return CachingConnectionFactory(rabbitMQ.hostname, rabbitMQ.port).apply {
            if (rabbitMQ.username.isNotBlank() && rabbitMQ.password.isNotBlank()) {
                username = rabbitMQ.username
                setPassword(rabbitMQ.password)
            }
        }
    }

    @Bean
    open fun rabbitListenerContainerFactory(): RabbitListenerContainerFactory<SimpleMessageListenerContainer> {
        return SimpleRabbitListenerContainerFactory().apply {
            setConnectionFactory(connectionFactory())
            setDefaultRequeueRejected(false)
            setMessageConverter(messageConverter())
        }
    }

    @Bean
    open fun amqpAdmin(): AmqpAdmin = RabbitAdmin(connectionFactory())

    @Bean
    open fun rabbitTemplate(): RabbitTemplate = RabbitTemplate(connectionFactory()).apply {
        messageConverter = messageConverter()
    }

    @Bean
    open fun messageConverter(): MessageConverter = Jackson2JsonMessageConverter()

    @Bean open fun guildInfoRequest(): Queue = Queue(QUEUE_GUILD_INFO_REQUEST)
    @Bean open fun rankingUpdateRewardsQueue(): Queue = Queue(QUEUE_RANKING_UPDATE_REQUEST)
    @Bean open fun commandListRequest(): Queue = Queue(QUEUE_COMMAND_LIST_REQUEST)
    @Bean open fun statusRequest(): Queue = Queue(QUEUE_STATUS_REQUEST)
    @Bean open fun webhookGetRequest(): Queue = Queue(QUEUE_WEBHOOK_GET_REQUEST)
    @Bean open fun webhookUpdateRequest(): Queue = Queue(QUEUE_WEBHOOK_UPDATE_REQUEST)
    @Bean open fun webhookDeleteRequest(): Queue = Queue(QUEUE_WEBHOOK_DELETE_REQUEST)
    @Bean open fun patreonWebhookRequest(): Queue = Queue(QUEUE_PATREON_WEBHOOK_REQUEST)
    @Bean open fun checkOwnerRequest(): Queue = Queue(QUEUE_CHECK_OWNER_REQUEST)
    @Bean open fun cacheEvictRequest(): Queue = Queue(QUEUE_CACHE_EVICT_REQUEST)
}
