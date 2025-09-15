package ru.sablebot.common.persistence.entity.base

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass

@MappedSuperclass
abstract class UserEntity(
    @Column(name = "user_id") var userId: String = ""
): BaseEntity()