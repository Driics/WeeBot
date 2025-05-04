package ru.driics.sablebot.common.persistence.repository.base

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.NoRepositoryBean
import ru.driics.sablebot.common.persistence.entity.base.UserEntity

@NoRepositoryBean
interface UserRepository<T : UserEntity> : JpaRepository<T, Long> {
    fun findByUserId(userId: String): T
}