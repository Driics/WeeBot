package ru.sablebot.api.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.sablebot.api.entity.ApiKey

@Repository
interface ApiKeyRepository : JpaRepository<ApiKey, Long> {

    fun findByGuildIdAndRevokedFalse(guildId: Long): List<ApiKey>

    fun findByHashedKeyAndRevokedFalse(hashedKey: String): ApiKey?

    fun findByGuildId(guildId: Long): List<ApiKey>

    fun findByKeyPrefixAndRevokedFalse(prefix: String): ApiKey?
}
