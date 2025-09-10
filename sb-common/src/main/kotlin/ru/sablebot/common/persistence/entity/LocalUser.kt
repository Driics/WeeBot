package ru.sablebot.common.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.Transient
import ru.sablebot.common.persistence.entity.base.FeaturedUserEntity

@Entity
@Table(name = "user", schema = "public")
class LocalUser: FeaturedUserEntity() {
    @Column
    var name: String? = null

    @Column
    var discriminator: String? = null

    @get:Transient
    val asMention: String
        get() = "<@$userId>"
}