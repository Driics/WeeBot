package ru.driics.sablebot.common.service.impl

import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.entities.Guild
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import ru.driics.sablebot.common.persistence.entity.base.GuildEntity
import ru.driics.sablebot.common.persistence.repository.base.GuildRepository
import ru.driics.sablebot.common.service.DomainService
import ru.driics.sablebot.common.service.GatewayService
import ru.driics.sablebot.common.support.SbCacheManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

abstract class AbstractDomainServiceImpl<T : GuildEntity, R : GuildRepository<T>>(
    protected val repository: R,
    override var cacheable: Boolean = false
) : DomainService<T> {
    private val locks = ConcurrentHashMap<Long, ReentrantLock>()

    companion object {
        val logger = KotlinLogging.logger {}
    }

    @Autowired
    protected lateinit var cacheManager: SbCacheManager

    @Autowired
    protected lateinit var applicationContext: ApplicationContext

    @Autowired
    protected lateinit var gatewayService: GatewayService

    @Transactional(readOnly = true)
    override fun get(id: Long): T? = repository.findById(id).orElse(null)

    @Transactional(readOnly = true)
    override fun get(guild: Guild): T? =
        getByGuildId(guild.idLong)

    @Transactional(readOnly = true)
    override fun getByGuildId(guildId: Long): T? =
        if (cacheable)
            cacheManager.get(getDomainClass(), guildId) { repository.findByGuildId(it) as (T) }
        else repository.findByGuildId(guildId)

    @Transactional
    override fun save(entity: T): T {
        val saved = repository.save(entity)
        evict(entity.guildId)
        return saved
    }

    @Transactional(readOnly = true)
    override fun exists(guildId: Long): Boolean = repository.existsByGuildId(guildId)

    @Transactional
    override fun getOrCreate(guildId: Long): T {
        var result = repository.findByGuildId(guildId)
        if (result == null) {
            val l = locks.computeIfAbsent(guildId) { ReentrantLock() }
            l.lock()
            try {
                result = repository.findByGuildId(guildId)
                if (result == null) {
                    result = try {
                        repository.saveAndFlush(createNew(guildId))
                    } catch (_: DataIntegrityViolationException) {
                        repository.findByGuildId(guildId)
                    }
                }
            } finally {
                l.unlock()
                locks.remove(guildId, l)
            }
        }
        return result!!
    }

    override fun evict(guildId: Long) {
        gatewayService.evictCache(getDomainClass().name, guildId)

        if (cacheable) {
            cacheManager.evict(getDomainClass(), guildId)
        }
    }

    protected abstract fun createNew(guildId: Long): T

    protected abstract fun getDomainClass(): Class<T>
}