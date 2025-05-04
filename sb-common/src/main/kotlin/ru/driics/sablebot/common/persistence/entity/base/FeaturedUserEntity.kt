package ru.driics.sablebot.common.persistence.entity.base

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass

@MappedSuperclass
data class FeaturedUserEntity(
    @Column
    var features: String = ""
): UserEntity() {
    // TODO
}