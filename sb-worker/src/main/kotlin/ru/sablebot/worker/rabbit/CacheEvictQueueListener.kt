package ru.sablebot.worker.rabbit

import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component
import ru.sablebot.common.configuration.RabbitConfiguration
import ru.sablebot.common.model.request.CacheEvictRequest
import ru.sablebot.common.support.SbCacheManager

@Component
class CacheEvictQueueListener(
    private val cacheManager: SbCacheManager,
) : BaseQueueListener() {

    @RabbitListener(queues = [RabbitConfiguration.QUEUE_CACHE_EVICT_REQUEST])
    fun evictCache(req: CacheEvictRequest) = cacheManager.evict(req.cacheName, req.guildId)
}