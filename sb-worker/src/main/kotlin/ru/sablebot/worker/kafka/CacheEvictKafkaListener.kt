package ru.sablebot.worker.kafka

import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import ru.sablebot.common.configuration.KafkaConfiguration
import ru.sablebot.common.model.request.CacheEvictRequest
import ru.sablebot.common.support.SbCacheManager

@Component
class CacheEvictKafkaListener(
    private val cacheManager: SbCacheManager,
) : BaseKafkaListener() {

    @KafkaListener(
        topics = [KafkaConfiguration.TOPIC_CACHE_EVICT_REQUEST],
        groupId = "sablebot-cache-group"
    )
    fun evictCache(req: CacheEvictRequest) = cacheManager.evict(req.cacheName, req.guildId)
}