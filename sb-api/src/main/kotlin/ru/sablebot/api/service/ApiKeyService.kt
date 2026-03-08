package ru.sablebot.api.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.sablebot.api.entity.ApiKey
import ru.sablebot.api.repository.ApiKeyRepository
import java.security.SecureRandom
import java.time.Instant
import java.util.*

@Service
class ApiKeyService(private val apiKeyRepository: ApiKeyRepository) {

    private val logger = KotlinLogging.logger {}
    private val passwordEncoder = BCryptPasswordEncoder()
    private val secureRandom = SecureRandom()

    data class GeneratedKey(val rawKey: String, val apiKey: ApiKey)

    @Transactional
    fun generateKey(guildId: Long, name: String, scopes: List<String>, createdBy: String): GeneratedKey {
        val rawKey = generateRawKey()
        val prefix = rawKey.take(8)
        val hashedKey = passwordEncoder.encode(rawKey)

        val apiKey = ApiKey().apply {
            this.guildId = guildId
            this.name = name
            this.hashedKey = hashedKey
            this.keyPrefix = prefix
            this.scopes = scopes
            this.createdBy = createdBy
            this.createdAt = Instant.now()
        }

        val saved = apiKeyRepository.save(apiKey)
        logger.info { "API key generated for guild $guildId by $createdBy (prefix: $prefix)" }
        return GeneratedKey(rawKey, saved)
    }

    @Transactional
    fun revokeKey(keyId: Long, guildId: Long): Boolean {
        val key = apiKeyRepository.findById(keyId).orElse(null) ?: return false
        if (key.guildId != guildId) return false
        key.revoked = true
        apiKeyRepository.save(key)
        logger.info { "API key ${key.keyPrefix}... revoked for guild $guildId" }
        return true
    }

    @Transactional(readOnly = true)
    fun listKeys(guildId: Long): List<ApiKey> {
        return apiKeyRepository.findByGuildId(guildId)
    }

    @Transactional
    fun validateKey(rawKey: String): ApiKey? {
        val prefix = extractPrefix(rawKey) ?: return null
        val candidate = apiKeyRepository.findByKeyPrefixAndRevokedFalse(prefix) ?: return null

        if (passwordEncoder.matches(rawKey, candidate.hashedKey)) {
            candidate.lastUsedAt = Instant.now()
            apiKeyRepository.save(candidate)
            return candidate
        }
        return null
    }

    private fun extractPrefix(rawKey: String): String? {
        if (rawKey.length < 8) return null
        return rawKey.take(8)
    }

    private fun generateRawKey(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return "sb_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
