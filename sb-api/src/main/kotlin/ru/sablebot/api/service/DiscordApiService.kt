package ru.sablebot.api.service

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import ru.sablebot.api.dto.auth.DiscordGuildResponse
import ru.sablebot.api.dto.auth.DiscordTokenResponse
import ru.sablebot.api.dto.auth.DiscordUserResponse
import ru.sablebot.api.security.config.DiscordProperties
import java.util.concurrent.TimeUnit

@Service
class DiscordApiService(private val discordProperties: DiscordProperties) {

    private val logger = KotlinLogging.logger {}

    private val restClient = RestClient.builder()
        .baseUrl("https://discord.com/api/v10")
        .build()

    private val botGuildCache = Caffeine.newBuilder()
        .expireAfterWrite(2, TimeUnit.MINUTES)
        .maximumSize(1)
        .build<String, Set<String>>()

    fun exchangeCode(code: String): DiscordTokenResponse {
        val formData = LinkedMultiValueMap<String, String>().apply {
            add("client_id", discordProperties.clientId)
            add("client_secret", discordProperties.clientSecret)
            add("grant_type", "authorization_code")
            add("code", code)
            add("redirect_uri", discordProperties.redirectUri)
        }

        return restClient.post()
            .uri("/oauth2/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(formData)
            .retrieve()
            .body(DiscordTokenResponse::class.java)!!
    }

    fun getCurrentUser(accessToken: String): DiscordUserResponse {
        return restClient.get()
            .uri("/users/@me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .retrieve()
            .body(DiscordUserResponse::class.java)!!
    }

    fun getUserGuilds(accessToken: String): List<DiscordGuildResponse> {
        return restClient.get()
            .uri("/users/@me/guilds")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .retrieve()
            .body(Array<DiscordGuildResponse>::class.java)?.toList() ?: emptyList()
    }

    fun getBotGuildIds(): Set<String> {
        if (discordProperties.botToken.isBlank()) return emptySet()
        return botGuildCache.get("bot") {
            try {
                restClient.get()
                    .uri("/users/@me/guilds")
                    .header(HttpHeaders.AUTHORIZATION, "Bot ${discordProperties.botToken}")
                    .retrieve()
                    .body(Array<DiscordGuildResponse>::class.java)
                    ?.map { it.id }?.toSet() ?: emptySet()
            } catch (e: Exception) {
                logger.warn(e) { "Failed to fetch bot guilds" }
                emptySet()
            }
        }
    }

    fun getGuildRoles(botToken: String, guildId: String): List<Map<String, Any>> {
        return restClient.get()
            .uri("/guilds/$guildId/roles")
            .header(HttpHeaders.AUTHORIZATION, "Bot $botToken")
            .retrieve()
            .body(object : org.springframework.core.ParameterizedTypeReference<List<Map<String, Any>>>() {})
            ?: emptyList()
    }

    fun getGuildChannels(botToken: String, guildId: String): List<Map<String, Any>> {
        return restClient.get()
            .uri("/guilds/$guildId/channels")
            .header(HttpHeaders.AUTHORIZATION, "Bot $botToken")
            .retrieve()
            .body(object : org.springframework.core.ParameterizedTypeReference<List<Map<String, Any>>>() {})
            ?: emptyList()
    }
}
