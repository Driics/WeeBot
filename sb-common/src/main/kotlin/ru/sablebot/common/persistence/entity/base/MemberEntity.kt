package ru.sablebot.common.persistence.entity.base

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass

@MappedSuperclass
abstract class MemberEntity(
    @Column(name = "user_id", nullable = false)
    protected var userId: String = "",
): GuildEntity()