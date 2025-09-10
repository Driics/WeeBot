package ru.sablebot.common.service.impl

import org.springframework.amqp.rabbit.core.RabbitOperations
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import ru.sablebot.common.configuration.RabbitConfiguration.Companion.QUEUE_CACHE_EVICT_REQUEST
import ru.sablebot.common.configuration.RabbitConfiguration.Companion.QUEUE_CHECK_OWNER_REQUEST
import ru.sablebot.common.configuration.RabbitConfiguration.Companion.QUEUE_STATUS_REQUEST
import ru.sablebot.common.model.request.CacheEvictRequest
import ru.sablebot.common.model.request.CheckOwnerRequest
import ru.sablebot.common.model.status.StatusDto
import ru.sablebot.common.service.GatewayService

@Service
class GatewayServiceImpl(
    private val template: RabbitOperations
) : GatewayService {
    override fun isChannelOwner(req: CheckOwnerRequest): Boolean =
        template.convertSendAndReceiveAsType(
            QUEUE_CHECK_OWNER_REQUEST, req,
            object : ParameterizedTypeReference<Boolean>() {}
        ) ?: throw IllegalStateException("No reply from $QUEUE_CHECK_OWNER_REQUEST")

    override fun getWorkerStatus(): StatusDto =
        template.convertSendAndReceiveAsType(
            QUEUE_STATUS_REQUEST,
            "1",
            object : ParameterizedTypeReference<StatusDto>() {}
        ) ?: throw IllegalStateException("No reply from $QUEUE_STATUS_REQUEST")

    override fun evictCache(cacheName: String, guildId: Long) =
        template.convertAndSend(QUEUE_CACHE_EVICT_REQUEST, CacheEvictRequest(cacheName, guildId))
}