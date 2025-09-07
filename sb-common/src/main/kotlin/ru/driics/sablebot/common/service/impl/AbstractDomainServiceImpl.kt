package ru.driics.sablebot.common.service.impl

import net.dv8tion.jda.api.entities.Guild
import okhttp3.internal.toImmutableMap
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.transaction.annotation.Transactional
import ru.driics.sablebot.common.persistence.entity.base.GuildEntity
import ru.driics.sablebot.common.persistence.repository.base.GuildRepository
import ru.driics.sablebot.common.service.DomainService
import ru.driics.sablebot.common.service.GatewayService
import ru.driics.sablebot.common.support.SbCacheManager

abstract class AbstractDomainServiceImpl<T : GuildEntity, R : GuildRepository<T>>(
    protected val repository: R,
    override var cacheable: Boolean = false
) : DomainService<T> {
    private val lock = Any()
    private val log = LoggerFactory.getLogger(this::class.java)

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
            synchronized(lock) {
                result = repository.findByGuildId(guildId)
                if (result == null) {
                    result = createNew(guildId)
                    repository.saveAndFlush(result)
                }
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