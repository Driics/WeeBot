package ru.sablebot.common.persistence.repository.base

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.NoRepositoryBean
import org.springframework.data.repository.query.Param
import ru.sablebot.common.persistence.entity.base.FeaturedUserEntity

@NoRepositoryBean
interface FeaturedUserRepository<T : FeaturedUserEntity> : UserRepository<T> {
    @Query("SELECT u.features FROM #{#entityName} u WHERE u.userId = :userId")
    fun findFeaturesByUserId(@Param("userId") userId: String): String
}