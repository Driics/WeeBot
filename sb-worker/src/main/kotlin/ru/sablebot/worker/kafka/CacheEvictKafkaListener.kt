package ru.sablebot.worker.kafka

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import ru.sablebot.common.configuration.KafkaTopics
import ru.sablebot.common.model.request.CacheEvictRequest
import ru.sablebot.common.support.SbCacheManager

@Component
class CacheEvictKafkaListener(
    private val cacheManager: SbCacheManager,
    private val meterRegistry: MeterRegistry,
) : BaseKafkaListener() {

    @KafkaListener(
        id = "cache-evict-listener",
        topics = [KafkaTopics.CACHE_EVICT_REQUEST],
        groupId = "\${sablebot.common.kafka.group-id}",
        concurrency = "\${sablebot.common.kafka.cache-evict.concurrency:3}"
    )
    fun evictCache(req: CacheEvictRequest) {
        meterRegistry.counter("sablebot.kafka.cache.evict.total").increment()
        cacheManager.evict(req.cacheName, req.guildId)
    }
}