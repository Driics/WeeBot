package ru.sablebot.common.support

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.cache.Cache
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import ru.sablebot.common.persistence.entity.base.BaseEntity

class SbCacheManagerImpl(
    private val meterRegistry: MeterRegistry? = null
) : ConcurrentMapCacheManager(), SbCacheManager {

    override fun <T : BaseEntity> get(clazz: Class<T>, id: Long, supplier: (Long) -> T): T {
        return get(getCacheName(clazz), id, supplier)
    }

    override fun <T : BaseEntity> evict(clazz: Class<T>, id: Long) {
        evict(getCacheName(clazz), id)
    }

    override fun <T, K> get(cacheName: String, key: K, supplier: (K) -> T): T {
        meterRegistry?.counter("sablebot.cache.gets", "cache", cacheName)?.increment()
        val cache: Cache = getCache(cacheName) ?: return supplier(key)
        requireNotNull(key) { "Cache key must not be null" }
        @Suppress("UNCHECKED_CAST")
        return cache.get(key) { supplier(key) } as T
    }

    override fun <K> evict(cacheName: String, key: K) {
        meterRegistry?.counter("sablebot.cache.evictions", "cache", cacheName)?.increment()
        requireNotNull(key) { "Cache key must not be null" }
        getCache(cacheName)?.evict(key as Any)
    }

    private fun getCacheName(clazz: Class<*>): String = clazz.name
}
