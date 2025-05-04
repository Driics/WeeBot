package ru.driics.sablebot.common.service

import net.dv8tion.jda.api.entities.Guild
import ru.driics.sablebot.common.persistence.entity.base.GuildEntity

interface DomainService<T : GuildEntity> {

    fun get(guild: Guild): T?

    fun get(id: Long): T?

    fun getByGuildId(guildId: Long): T?

    fun getOrCreate(guildId: Long): T

    fun save(entity: T): T

    fun exists(guildId: Long): Boolean

    fun evict(guildId: Long)

    var cacheable: Boolean
}
