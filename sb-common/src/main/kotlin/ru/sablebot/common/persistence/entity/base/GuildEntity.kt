package ru.sablebot.common.persistence.entity.base

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass

@MappedSuperclass
abstract class GuildEntity(
    @Column(name = "guild_id")
    open var guildId: Long = 0
) : BaseEntity()