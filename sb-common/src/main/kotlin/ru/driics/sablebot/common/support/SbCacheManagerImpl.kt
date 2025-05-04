package ru.driics.sablebot.common.support

import org.springframework.cache.Cache
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import ru.driics.sablebot.common.persistence.entity.base.BaseEntity

class SbCacheManagerImpl : ConcurrentMapCacheManager(), SbCacheManager {

    override fun <T : BaseEntity> get(clazz: Class<T>, id: Long, supplier: (Long) -> T): T {
        return get(getCacheName(clazz), id, supplier)
    }

    override fun <T : BaseEntity> evict(clazz: Class<T>, id: Long) {
        evict(getCacheName(clazz), id)
    }

    override fun <T, K> get(cacheName: String, key: K, supplier: (K) -> T): T {
        val cache: Cache = getCache(cacheName) ?: return supplier(key)
        val value = cache.get(key as Any)?.get() as? T
        return value ?: supplier(key).also { cache.put(key, it) }
    }

    override fun <K> evict(cacheName: String, key: K) {
        getCache(cacheName)?.evict(key as Any)
    }

    private fun getCacheName(clazz: Class<*>): String = clazz.name
}