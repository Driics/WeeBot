package ru.sablebot.api.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import ru.sablebot.api.security.annotation.RequireGuildPermission
import ru.sablebot.api.security.utils.SecurityUtils
import ru.sablebot.api.service.ApiKeyService
import java.time.Instant

@RestController
@RequestMapping("/api/guilds/{guildId}/api-keys")
class ApiKeyController(
    private val apiKeyService: ApiKeyService
) {

    data class ApiKeyResponse(
        val id: Long,
        val name: String,
        val keyPrefix: String,
        val scopes: List<String>,
        val createdBy: String,
        val createdAt: Instant,
        val lastUsedAt: Instant?,
        val revoked: Boolean
    )

    data class CreateApiKeyRequest(
        val name: String,
        val scopes: List<String>
    )

    data class CreateApiKeyResponse(
        val id: Long,
        val name: String,
        val rawKey: String,
        val scopes: List<String>
    )

    @GetMapping
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun listKeys(@PathVariable guildId: String): ResponseEntity<List<ApiKeyResponse>> {
        val keys = apiKeyService.listKeys(guildId.toLong()).map {
            ApiKeyResponse(
                id = it.id!!,
                name = it.name,
                keyPrefix = it.keyPrefix,
                scopes = it.scopes,
                createdBy = it.createdBy,
                createdAt = it.createdAt,
                lastUsedAt = it.lastUsedAt,
                revoked = it.revoked
            )
        }
        return ResponseEntity.ok(keys)
    }

    @PostMapping
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun createKey(
        @PathVariable guildId: String,
        @RequestBody request: CreateApiKeyRequest
    ): ResponseEntity<CreateApiKeyResponse> {
        val userId = SecurityUtils.currentUser?.id
            ?: return ResponseEntity.status(401).build()

        val result = apiKeyService.generateKey(guildId.toLong(), request.name, request.scopes, userId)

        return ResponseEntity.ok(
            CreateApiKeyResponse(
                id = result.apiKey.id!!,
                name = result.apiKey.name,
                rawKey = result.rawKey,
                scopes = result.apiKey.scopes
            )
        )
    }

    @DeleteMapping("/{keyId}")
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun revokeKey(
        @PathVariable guildId: String,
        @PathVariable keyId: Long
    ): ResponseEntity<Void> {
        val revoked = apiKeyService.revokeKey(keyId, guildId.toLong())
        return if (revoked) ResponseEntity.ok().build()
        else ResponseEntity.notFound().build()
    }
}
