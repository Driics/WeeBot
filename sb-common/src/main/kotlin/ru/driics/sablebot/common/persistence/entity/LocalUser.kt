package ru.driics.sablebot.common.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.Transient
import ru.driics.sablebot.common.persistence.entity.base.FeaturedUserEntity

@Entity
@Table(name = "user", schema = "public")
class LocalUser: FeaturedUserEntity() {
    @Column
    private var name: String? = null

    @Column
    private var discriminator: String? = null

    @get:Transient
    val asMention: String
        get() = "<@$userId>"
}