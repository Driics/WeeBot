package ru.driics.sablebot.common.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import ru.driics.sablebot.common.persistence.entity.base.FeaturedUserEntity

@Entity
@Table(name = "patreon_user", schema = "public")
class PatreonUser(
    @Column
    val patreonId: String = "",
    @Column
    val active: Boolean = false,
    @Column
    val boostedGuildId: Long = 0,
): FeaturedUserEntity()