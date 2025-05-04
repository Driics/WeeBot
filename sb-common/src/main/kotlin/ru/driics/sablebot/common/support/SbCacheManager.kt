package ru.driics.sablebot.common.support

import org.springframework.cache.CacheManager
import ru.driics.sablebot.common.persistence.entity.base.BaseEntity

interface SbCacheManager : CacheManager {

    fun <T : BaseEntity> get(clazz: Class<T>, id: Long, supplier: (Long) -> T): T

    fun <T : BaseEntity> evict(clazz: Class<T>, id: Long)

    fun <T, K> get(cacheName: String, key: K, supplier: (K) -> T): T

    fun <K> evict(cacheName: String, key: K)
}