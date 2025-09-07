package ru.driics.sablebot.common.service.impl

import org.springframework.amqp.core.AmqpTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import ru.driics.sablebot.common.configuration.RabbitConfiguration.Companion.QUEUE_CACHE_EVICT_REQUEST
import ru.driics.sablebot.common.configuration.RabbitConfiguration.Companion.QUEUE_CHECK_OWNER_REQUEST
import ru.driics.sablebot.common.configuration.RabbitConfiguration.Companion.QUEUE_STATUS_REQUEST
import ru.driics.sablebot.common.model.request.CacheEvictRequest
import ru.driics.sablebot.common.model.request.CheckOwnerRequest
import ru.driics.sablebot.common.model.status.StatusDto
import ru.driics.sablebot.common.service.GatewayService

@Service
class GatewayServiceImpl(
    private val template: AmqpTemplate
) : GatewayService {
    override fun isChannelOwner(req: CheckOwnerRequest): Boolean =
        template.convertSendAndReceiveAsType(
            QUEUE_CHECK_OWNER_REQUEST, req,
            object : ParameterizedTypeReference<Boolean>() {}
        ) == true

    override fun getWorkerStatus(): StatusDto =
        template.convertSendAndReceiveAsType(
            QUEUE_STATUS_REQUEST,
            "1",
            object : ParameterizedTypeReference<StatusDto>() {})!!

    override fun evictCache(cacheName: String, guildId: Long) =
        template.convertAndSend(QUEUE_CACHE_EVICT_REQUEST, CacheEvictRequest(cacheName, guildId))
}