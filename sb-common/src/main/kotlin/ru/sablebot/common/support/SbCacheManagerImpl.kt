package ru.sablebot.common.support

import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.cache.Cache
import org.springframework.cache.caffeine.CaffeineCache
import ru.sablebot.common.persistence.entity.base.BaseEntity
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class SbCacheManagerImpl(
    private val meterRegistry: MeterRegistry? = null
) : SbCacheManager {

    private val caches = ConcurrentHashMap<String, CaffeineCache>()

    override fun getCache(name: String): Cache {
        return caches.computeIfAbsent(name) { createCache(it) }
    }

    override fun getCacheNames(): Collection<String> = caches.keys

    override fun <T : BaseEntity> get(clazz: Class<T>, id: Long, supplier: (Long) -> T): T {
        return get(getCacheName(clazz), id, supplier)
    }

    override fun <T : BaseEntity> getOrNull(clazz: Class<T>, id: Long, supplier: (Long) -> T?): T? {
        val cacheName = getCacheName(clazz)
        meterRegistry?.counter("sablebot.cache.gets", "cache", cacheName)?.increment()
        val cache = getCache(cacheName) as CaffeineCache
        val nativeCache = cache.nativeCache
        val existing = nativeCache.getIfPresent(id)
        if (existing != null) {
            meterRegistry?.counter("sablebot.cache.hits", "cache", cacheName)?.increment()
            @Suppress("UNCHECKED_CAST")
            return existing as T
        }
        meterRegistry?.counter("sablebot.cache.misses", "cache", cacheName)?.increment()
        val value = supplier(id)
        if (value != null) {
            nativeCache.put(id, value)
        }
        return value
    }

    override fun <T : BaseEntity> evict(clazz: Class<T>, id: Long) {
        evict(getCacheName(clazz), id)
    }

    override fun <T, K> get(cacheName: String, key: K, supplier: (K) -> T): T {
        meterRegistry?.counter("sablebot.cache.gets", "cache", cacheName)?.increment()
        requireNotNull(key) { "Cache key must not be null" }
        val cache = getCache(cacheName) as CaffeineCache
        val nativeCache = cache.nativeCache
        val existing = nativeCache.getIfPresent(key)
        if (existing != null) {
            meterRegistry?.counter("sablebot.cache.hits", "cache", cacheName)?.increment()
            @Suppress("UNCHECKED_CAST")
            return existing as T
        }
        meterRegistry?.counter("sablebot.cache.misses", "cache", cacheName)?.increment()
        val value = supplier(key)
        nativeCache.put(key as Any, value as Any)
        @Suppress("UNCHECKED_CAST")
        return value
    }

    override fun <K> evict(cacheName: String, key: K) {
        meterRegistry?.counter("sablebot.cache.evictions", "cache", cacheName)?.increment()
        requireNotNull(key) { "Cache key must not be null" }
        getCache(cacheName).evict(key as Any)
    }

    private fun createCache(name: String): CaffeineCache {
        val caffeine = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build<Any, Any>()

        return CaffeineCache(name, caffeine)
    }

    private fun getCacheName(clazz: Class<*>): String = clazz.name
}
