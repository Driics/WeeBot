package ru.sablebot.common.service.impl

import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate
import org.springframework.kafka.requestreply.RequestReplyFuture
import org.springframework.stereotype.Service
import ru.sablebot.common.configuration.KafkaConfiguration
import ru.sablebot.common.model.request.CacheEvictRequest
import ru.sablebot.common.model.request.CheckOwnerRequest
import ru.sablebot.common.model.status.StatusDto
import ru.sablebot.common.service.GatewayService
import java.util.concurrent.TimeUnit

@Service
class GatewayServiceImpl(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val replyKafkaTemplate: ReplyingKafkaTemplate<String, Any, Any>,
) : GatewayService {
    override fun isChannelOwner(req: CheckOwnerRequest): Boolean {
        val record = ProducerRecord<String, Any>(
            KafkaConfiguration.TOPIC_CHECK_OWNER_REQUEST,
            req
        )
        record.headers().add(
            "kafka_replyTopic",
            KafkaConfiguration.TOPIC_CHECK_OWNER_REPLY.toByteArray()
        )

        val replyFuture: RequestReplyFuture<String, Any, Any> =
            replyKafkaTemplate.sendAndReceive(record)

        val consumerRecord = replyFuture.get(10, TimeUnit.SECONDS)
        return consumerRecord.value() as? Boolean
            ?: throw IllegalStateException("No reply from ${KafkaConfiguration.TOPIC_CHECK_OWNER_REQUEST}")
    }

    override fun getWorkerStatus(): StatusDto {
        val record = ProducerRecord<String, Any>(
            KafkaConfiguration.TOPIC_STATUS_REQUEST,
            "1"
        )
        record.headers().add("kafka_replyTopic", KafkaConfiguration.TOPIC_STATUS_REPLY.toByteArray())

        val replyFuture: RequestReplyFuture<String, Any, Any> =
            replyKafkaTemplate.sendAndReceive(record)

        val consumerRecord = replyFuture.get(10, TimeUnit.SECONDS)
        return consumerRecord.value() as? StatusDto
            ?: throw IllegalStateException("No reply from ${KafkaConfiguration.TOPIC_STATUS_REQUEST}")
    }

    override fun evictCache(cacheName: String, guildId: Long) {
        kafkaTemplate.send(
            KafkaConfiguration.TOPIC_CACHE_EVICT_REQUEST,
            CacheEvictRequest(cacheName, guildId)
        )
    }
}