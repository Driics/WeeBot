package ru.sablebot.module.feeds.service.twitch

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import ru.sablebot.module.feeds.config.FeedProperties
import ru.sablebot.module.feeds.model.twitch.TwitchStream
import ru.sablebot.module.feeds.model.twitch.TwitchStreamsResponse
import java.net.URI
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Client for interacting with the Twitch Helix API.
 * Fetches stream status for streamers with rate limiting and OAuth support.
 */
@Service
class TwitchApiClient(
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper,
    private val feedProperties: FeedProperties,
    private val meterRegistry: MeterRegistry,
    private val twitchOAuthService: TwitchOAuthService
) {
    private val log = KotlinLogging.logger {}

    companion object {
        private const val TWITCH_HELIX_BASE_URL = "https://api.twitch.tv/helix"
        private const val RATE_LIMIT_WINDOW_MS = 60_000L // 1 minute
    }

    // Rate limiting: track request timestamps
    private val requestTimestamps = mutableListOf<Long>()
    private val timestampsMutex = Any()

    /**
     * Get stream status for a specific Twitch user.
     *
     * @param userLogin The Twitch user login name (lowercase username)
     * @return TwitchStream if the user is live, null if offline or not found
     */
    suspend fun getStream(userLogin: String): TwitchStream? = withContext(Dispatchers.IO) {
        try {
            // Rate limiting check
            if (!checkRateLimit()) {
                log.warn { "Rate limit exceeded for Twitch API" }
                meterRegistry.counter("sablebot.feeds.twitch.ratelimit.exceeded").increment()
                return@withContext null
            }

            val accessToken = twitchOAuthService.getAccessToken()

            val url = UriComponentsBuilder.fromUri(URI.create(TWITCH_HELIX_BASE_URL))
                .path("/streams")
                .queryParam("user_login", userLogin)
                .build(true)
                .toUriString()

            val headers = HttpHeaders().apply {
                set("Authorization", "Bearer $accessToken")
                set("Client-Id", feedProperties.twitch.clientId)
            }
            val requestEntity = HttpEntity<String>(headers)

            log.debug { "Fetching Twitch stream status for user: $userLogin" }

            val response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                requestEntity,
                String::class.java
            )

            if (!response.statusCode.is2xxSuccessful || response.body == null) {
                log.warn { "Twitch API returned non-successful status: ${response.statusCode}" }
                meterRegistry.counter("sablebot.feeds.twitch.api.errors", "status", response.statusCode.toString()).increment()
                return@withContext null
            }

            val streamsResponse = objectMapper.readValue(response.body, TwitchStreamsResponse::class.java)
            val stream = streamsResponse.data.firstOrNull()

            if (stream != null && stream.isLive) {
                log.debug { "User $userLogin is live: ${stream.title}" }
                meterRegistry.counter("sablebot.feeds.twitch.streams.found").increment()
            } else {
                log.debug { "User $userLogin is offline" }
                meterRegistry.counter("sablebot.feeds.twitch.streams.offline").increment()
            }

            meterRegistry.counter("sablebot.feeds.twitch.api.success").increment()

            // Record request timestamp for rate limiting
            recordRequest()

            stream
        } catch (e: Exception) {
            log.error(e) { "Error fetching Twitch stream for user: $userLogin" }
            meterRegistry.counter("sablebot.feeds.twitch.api.errors", "type", "exception").increment()
            null
        }
    }

    /**
     * Get stream status for multiple Twitch users in a single request.
     *
     * @param userLogins List of Twitch user login names (max 100)
     * @return List of TwitchStream objects for users that are currently live
     */
    suspend fun getStreams(userLogins: List<String>): List<TwitchStream> = withContext(Dispatchers.IO) {
        if (userLogins.isEmpty()) {
            return@withContext emptyList()
        }

        try {
            // Rate limiting check
            if (!checkRateLimit()) {
                log.warn { "Rate limit exceeded for Twitch API" }
                meterRegistry.counter("sablebot.feeds.twitch.ratelimit.exceeded").increment()
                return@withContext emptyList()
            }

            val accessToken = twitchOAuthService.getAccessToken()

            // Twitch API allows up to 100 user_login parameters
            val limitedUsers = userLogins.take(100)

            val uriBuilder = UriComponentsBuilder.fromUri(URI.create(TWITCH_HELIX_BASE_URL))
                .path("/streams")

            limitedUsers.forEach { userLogin ->
                uriBuilder.queryParam("user_login", userLogin)
            }

            val url = uriBuilder.build(true).toUriString()

            val headers = HttpHeaders().apply {
                set("Authorization", "Bearer $accessToken")
                set("Client-Id", feedProperties.twitch.clientId)
            }
            val requestEntity = HttpEntity<String>(headers)

            log.debug { "Fetching Twitch streams for ${limitedUsers.size} users" }

            val response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                requestEntity,
                String::class.java
            )

            if (!response.statusCode.is2xxSuccessful || response.body == null) {
                log.warn { "Twitch API returned non-successful status: ${response.statusCode}" }
                meterRegistry.counter("sablebot.feeds.twitch.api.errors", "status", response.statusCode.toString()).increment()
                return@withContext emptyList()
            }

            val streamsResponse = objectMapper.readValue(response.body, TwitchStreamsResponse::class.java)
            val streams = streamsResponse.data.filter { it.isLive }

            log.debug { "Found ${streams.size} live streams out of ${limitedUsers.size} users" }
            meterRegistry.counter("sablebot.feeds.twitch.api.success").increment()
            meterRegistry.counter("sablebot.feeds.twitch.streams.found").increment(streams.size.toDouble())

            // Record request timestamp for rate limiting
            recordRequest()

            streams
        } catch (e: Exception) {
            log.error(e) { "Error fetching Twitch streams for multiple users" }
            meterRegistry.counter("sablebot.feeds.twitch.api.errors", "type", "exception").increment()
            emptyList()
        }
    }

    /**
     * Check if we can make a request without exceeding rate limits.
     * Twitch allows 800 requests per minute.
     *
     * @return true if request is allowed, false if rate limit would be exceeded
     */
    private fun checkRateLimit(): Boolean {
        if (!feedProperties.twitch.enabled) {
            return false
        }

        if (!twitchOAuthService.isConfigured()) {
            log.warn { "Twitch OAuth is not properly configured" }
            return false
        }

        val now = Instant.now().toEpochMilli()

        synchronized(timestampsMutex) {
            // Remove timestamps older than the rate limit window
            requestTimestamps.removeIf { it < now - RATE_LIMIT_WINDOW_MS }

            // Check if we've exceeded the limit
            val maxRequests = feedProperties.twitch.rateLimitRequestsPerMinute
            if (requestTimestamps.size >= maxRequests) {
                return false
            }
        }

        return true
    }

    /**
     * Record a request timestamp for rate limiting.
     */
    private fun recordRequest() {
        val now = Instant.now().toEpochMilli()

        synchronized(timestampsMutex) {
            requestTimestamps.add(now)
        }
    }

    /**
     * Clear rate limit tracking.
     * Useful for testing or manual reset.
     */
    fun clearRateLimit() {
        synchronized(timestampsMutex) {
            requestTimestamps.clear()
        }
    }

    /**
     * Get current request count within the rate limit window.
     *
     * @return Number of requests in the current window
     */
    fun getRateLimitStatus(): Int {
        val now = Instant.now().toEpochMilli()

        synchronized(timestampsMutex) {
            requestTimestamps.removeIf { it < now - RATE_LIMIT_WINDOW_MS }
            return requestTimestamps.size
        }
    }
}
