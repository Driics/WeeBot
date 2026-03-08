package ru.sablebot.common.service.impl

import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate
import org.springframework.kafka.requestreply.RequestReplyFuture
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.stereotype.Service
import ru.sablebot.common.configuration.KafkaTopics
import ru.sablebot.common.model.request.CacheEvictRequest
import ru.sablebot.common.model.request.CheckOwnerRequest
import ru.sablebot.common.model.status.StatusDto
import ru.sablebot.common.service.GatewayService
import java.util.*
import java.util.concurrent.TimeUnit

@Service
class GatewayServiceImpl(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val replyKafkaTemplate: ReplyingKafkaTemplate<String, Any, Any>,
) : GatewayService {
    override fun isChannelOwner(req: CheckOwnerRequest): Boolean {
        val record = ProducerRecord<String, Any>(
            KafkaTopics.CHECK_OWNER_REQUEST,
            req
        )
        record.headers().add(
            KafkaHeaders.REPLY_TOPIC,
            KafkaTopics.CHECK_OWNER_REPLY.toByteArray()
        )
        record.headers().add(
            KafkaHeaders.CORRELATION_ID,
            UUID.randomUUID().toString().toByteArray(Charsets.UTF_8)
        )

        val replyFuture: RequestReplyFuture<String, Any, Any> =
            replyKafkaTemplate.sendAndReceive(record)

        val consumerRecord = replyFuture.get(10, TimeUnit.SECONDS)

        val v = consumerRecord.value()
        return (v as? Boolean) ?: throw IllegalStateException(
            "Unexpected reply for ${KafkaTopics.CHECK_OWNER_REQUEST}: ${v?.javaClass?.name}"
        )
    }

    override fun getWorkerStatus(): StatusDto {
        val record = ProducerRecord<String, Any>(
            KafkaTopics.STATUS_REQUEST,
            "1"
        )
        record.headers().add(
            KafkaHeaders.REPLY_TOPIC,
            KafkaTopics.STATUS_REPLY.toByteArray(Charsets.UTF_8)
        )
        record.headers().add(
            KafkaHeaders.CORRELATION_ID,
            UUID.randomUUID().toString().toByteArray(Charsets.UTF_8)
        )

        val replyFuture: RequestReplyFuture<String, Any, Any> =
            replyKafkaTemplate.sendAndReceive(record)

        val consumerRecord = replyFuture.get(10, TimeUnit.SECONDS)

        val v = consumerRecord.value()
        return (v as? StatusDto) ?: throw IllegalStateException(
            "Unexpected reply for ${KafkaTopics.STATUS_REQUEST}: ${v?.javaClass?.name}"
        )
    }

    override fun evictCache(cacheName: String, guildId: Long) {
        kafkaTemplate.send(
            KafkaTopics.CACHE_EVICT_REQUEST,
            guildId.toString(),
            CacheEvictRequest(cacheName, guildId)
        )
    }
}