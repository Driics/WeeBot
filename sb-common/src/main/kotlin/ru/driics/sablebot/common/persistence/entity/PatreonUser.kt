package ru.driics.sablebot.common.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import ru.driics.sablebot.common.persistence.entity.base.FeaturedUserEntity

@Entity
@Table(name = "patreon_user", schema = "public")
class PatreonUser(
    @Column(nullable = false, unique = true, length = 32)
    var patreonId: String = "",
    @Column(nullable = false)
    var active: Boolean = false,
    @Column(nullable = false)
    var boostedGuildId: Long = 0,
): FeaturedUserEntity()