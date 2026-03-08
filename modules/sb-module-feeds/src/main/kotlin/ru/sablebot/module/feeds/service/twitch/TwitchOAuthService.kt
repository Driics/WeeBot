package ru.sablebot.module.feeds.service.twitch

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate
import ru.sablebot.module.feeds.config.FeedProperties
import ru.sablebot.module.feeds.model.twitch.TwitchOAuthTokenResponse
import java.time.Instant

/**
 * Service for managing Twitch OAuth 2.0 Client Credentials flow.
 * Handles access token acquisition and automatic refresh.
 */
@Service
class TwitchOAuthService(
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper,
    private val feedProperties: FeedProperties,
    private val meterRegistry: MeterRegistry
) {
    private val log = KotlinLogging.logger {}

    companion object {
        private const val TWITCH_OAUTH_URL = "https://id.twitch.tv/oauth2/token"
        private const val TOKEN_REFRESH_BUFFER_SECONDS = 300L // Refresh 5 minutes before expiry
    }

    // Thread-safe token storage
    private var currentToken: String? = null
    private var tokenExpiresAt: Instant? = null
    private val tokenMutex = Mutex()

    /**
     * Get a valid access token, refreshing if necessary.
     *
     * @return Valid access token
     * @throws IllegalStateException if OAuth is not configured or token acquisition fails
     */
    suspend fun getAccessToken(): String {
        if (!feedProperties.twitch.enabled) {
            throw IllegalStateException("Twitch integration is not enabled")
        }

        if (feedProperties.twitch.clientId.isBlank() || feedProperties.twitch.clientSecret.isBlank()) {
            throw IllegalStateException("Twitch client ID and secret must be configured")
        }

        return tokenMutex.withLock {
            if (isTokenValid()) {
                log.debug { "Using cached Twitch access token" }
                currentToken!!
            } else {
                log.info { "Acquiring new Twitch access token" }
                refreshToken()
            }
        }
    }

    /**
     * Check if the current token is valid and not expired.
     *
     * @return true if token is valid, false otherwise
     */
    private fun isTokenValid(): Boolean {
        if (currentToken == null || tokenExpiresAt == null) {
            return false
        }

        val now = Instant.now()
        val expiresWithBuffer = tokenExpiresAt!!.minusSeconds(TOKEN_REFRESH_BUFFER_SECONDS)

        return now.isBefore(expiresWithBuffer)
    }

    /**
     * Refresh the OAuth access token using Client Credentials flow.
     *
     * @return New access token
     * @throws IllegalStateException if token acquisition fails
     */
    private fun refreshToken(): String {
        try {
            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_FORM_URLENCODED
            }

            val body = LinkedMultiValueMap<String, String>().apply {
                add("client_id", feedProperties.twitch.clientId)
                add("client_secret", feedProperties.twitch.clientSecret)
                add("grant_type", "client_credentials")
            }

            val request = HttpEntity(body, headers)

            log.debug { "Requesting Twitch OAuth token" }

            val response = restTemplate.postForEntity(
                TWITCH_OAUTH_URL,
                request,
                String::class.java
            )

            if (!response.statusCode.is2xxSuccessful || response.body == null) {
                log.error { "Twitch OAuth returned non-successful status: ${response.statusCode}" }
                meterRegistry.counter("sablebot.feeds.twitch.oauth.errors", "status", response.statusCode.toString()).increment()
                throw IllegalStateException("Failed to acquire Twitch OAuth token: ${response.statusCode}")
            }

            val tokenResponse = objectMapper.readValue(response.body, TwitchOAuthTokenResponse::class.java)

            currentToken = tokenResponse.accessToken
            tokenExpiresAt = Instant.now().plusSeconds(tokenResponse.expiresIn.toLong())

            log.info { "Successfully acquired Twitch access token (expires in ${tokenResponse.expiresIn}s)" }
            meterRegistry.counter("sablebot.feeds.twitch.oauth.success").increment()

            return tokenResponse.accessToken
        } catch (e: Exception) {
            log.error(e) { "Error acquiring Twitch OAuth token" }
            meterRegistry.counter("sablebot.feeds.twitch.oauth.errors", "type", "exception").increment()
            throw IllegalStateException("Failed to acquire Twitch OAuth token", e)
        }
    }

    /**
     * Invalidate the current token, forcing a refresh on next request.
     * Useful for testing or error recovery.
     */
    fun invalidateToken() {
        currentToken = null
        tokenExpiresAt = null
        log.debug { "Twitch OAuth token invalidated" }
    }

    /**
     * Check if OAuth is properly configured.
     *
     * @return true if client ID and secret are configured
     */
    fun isConfigured(): Boolean {
        return feedProperties.twitch.enabled &&
                feedProperties.twitch.clientId.isNotBlank() &&
                feedProperties.twitch.clientSecret.isNotBlank()
    }

    /**
     * Get the current token expiration time.
     *
     * @return Token expiration instant, or null if no token
     */
    fun getTokenExpiresAt(): Instant? = tokenExpiresAt
}
