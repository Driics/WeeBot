package ru.sablebot.module.feeds.service.reddit

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
import ru.sablebot.module.feeds.model.reddit.RedditListingResponse
import ru.sablebot.module.feeds.model.reddit.RedditPost
import java.net.URI
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Client for interacting with the Reddit API.
 * Fetches new posts from subreddits with rate limiting.
 */
@Service
class RedditApiClient(
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper,
    private val feedProperties: FeedProperties,
    private val meterRegistry: MeterRegistry
) {
    private val log = KotlinLogging.logger {}

    companion object {
        private const val REDDIT_BASE_URL = "https://www.reddit.com"
        private const val RATE_LIMIT_WINDOW_MS = 60_000L // 1 minute
    }

    // Rate limiting: track request timestamps per subreddit
    private val requestTimestamps = ConcurrentHashMap<String, MutableList<Long>>()

    /**
     * Fetches new posts from the specified subreddit.
     *
     * @param subreddit The subreddit name (without /r/ prefix)
     * @param limit Maximum number of posts to fetch (default 25, max 100)
     * @param after Reddit's pagination token for fetching posts after this ID
     * @return List of RedditPost objects, or empty list on error
     */
    suspend fun getNewPosts(
        subreddit: String,
        limit: Int = 25,
        after: String? = null
    ): List<RedditPost> = withContext(Dispatchers.IO) {
        try {
            // Rate limiting check
            if (!checkRateLimit(subreddit)) {
                log.warn { "Rate limit exceeded for subreddit: $subreddit" }
                meterRegistry.counter("sablebot.feeds.reddit.ratelimit.exceeded").increment()
                return@withContext emptyList()
            }

            val actualLimit = limit.coerceIn(1, 100)

            val url = UriComponentsBuilder.fromUri(URI.create(REDDIT_BASE_URL))
                .path("/r/$subreddit/new.json")
                .queryParam("limit", actualLimit)
                .apply {
                    if (after != null) {
                        queryParam("after", after)
                    }
                }
                .build(true)
                .toUriString()

            val headers = HttpHeaders().apply {
                set("User-Agent", feedProperties.reddit.userAgent)
            }
            val requestEntity = HttpEntity<String>(headers)

            log.debug { "Fetching Reddit posts from $subreddit (limit=$actualLimit, after=$after)" }

            val response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                requestEntity,
                String::class.java
            )

            if (!response.statusCode.is2xxSuccessful || response.body == null) {
                log.warn { "Reddit API returned non-successful status: ${response.statusCode}" }
                meterRegistry.counter("sablebot.feeds.reddit.api.errors", "status", response.statusCode.toString()).increment()
                return@withContext emptyList()
            }

            val listing = objectMapper.readValue(response.body, RedditListingResponse::class.java)
            val posts = listing.data.children.map { it.data }

            log.debug { "Fetched ${posts.size} posts from r/$subreddit" }
            meterRegistry.counter("sablebot.feeds.reddit.api.success").increment()
            meterRegistry.counter("sablebot.feeds.reddit.posts.fetched").increment(posts.size.toDouble())

            // Record request timestamp for rate limiting
            recordRequest(subreddit)

            posts
        } catch (e: Exception) {
            log.error(e) { "Error fetching Reddit posts from r/$subreddit" }
            meterRegistry.counter("sablebot.feeds.reddit.api.errors", "type", "exception").increment()
            emptyList()
        }
    }

    /**
     * Fetches a single post by ID from a subreddit.
     *
     * @param subreddit The subreddit name
     * @param postId The Reddit post ID
     * @return RedditPost or null if not found
     */
    suspend fun getPost(subreddit: String, postId: String): RedditPost? = withContext(Dispatchers.IO) {
        try {
            if (!checkRateLimit(subreddit)) {
                log.warn { "Rate limit exceeded for subreddit: $subreddit" }
                meterRegistry.counter("sablebot.feeds.reddit.ratelimit.exceeded").increment()
                return@withContext null
            }

            val url = UriComponentsBuilder.fromUri(URI.create(REDDIT_BASE_URL))
                .path("/r/$subreddit/comments/$postId.json")
                .build(true)
                .toUriString()

            val headers = HttpHeaders().apply {
                set("User-Agent", feedProperties.reddit.userAgent)
            }
            val requestEntity = HttpEntity<String>(headers)

            val response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                requestEntity,
                String::class.java
            )

            if (!response.statusCode.is2xxSuccessful || response.body == null) {
                log.warn { "Reddit API returned non-successful status: ${response.statusCode}" }
                return@withContext null
            }

            // Reddit returns an array with [post_listing, comments_listing]
            val jsonArray = objectMapper.readTree(response.body)
            if (jsonArray.isArray && jsonArray.size() > 0) {
                val postListing = objectMapper.treeToValue(jsonArray[0], RedditListingResponse::class.java)
                val post = postListing.data.children.firstOrNull()?.data

                recordRequest(subreddit)
                meterRegistry.counter("sablebot.feeds.reddit.api.success").increment()

                post
            } else {
                null
            }
        } catch (e: Exception) {
            log.error(e) { "Error fetching Reddit post $postId from r/$subreddit" }
            meterRegistry.counter("sablebot.feeds.reddit.api.errors", "type", "exception").increment()
            null
        }
    }

    /**
     * Check if we can make a request to this subreddit without exceeding rate limits.
     *
     * @param subreddit The subreddit name
     * @return true if request is allowed, false if rate limit would be exceeded
     */
    private fun checkRateLimit(subreddit: String): Boolean {
        if (!feedProperties.reddit.enabled) {
            return false
        }

        val now = Instant.now().toEpochMilli()
        val timestamps = requestTimestamps.computeIfAbsent(subreddit) { mutableListOf() }

        synchronized(timestamps) {
            // Remove timestamps older than the rate limit window
            timestamps.removeIf { it < now - RATE_LIMIT_WINDOW_MS }

            // Check if we've exceeded the limit
            val maxRequests = feedProperties.reddit.rateLimitRequestsPerMinute
            if (timestamps.size >= maxRequests) {
                return false
            }
        }

        return true
    }

    /**
     * Record a request timestamp for rate limiting.
     *
     * @param subreddit The subreddit name
     */
    private fun recordRequest(subreddit: String) {
        val now = Instant.now().toEpochMilli()
        val timestamps = requestTimestamps.computeIfAbsent(subreddit) { mutableListOf() }

        synchronized(timestamps) {
            timestamps.add(now)
        }
    }

    /**
     * Clear rate limit tracking for a specific subreddit.
     * Useful for testing or manual reset.
     *
     * @param subreddit The subreddit name
     */
    fun clearRateLimit(subreddit: String) {
        requestTimestamps.remove(subreddit)
    }

    /**
     * Get current request count within the rate limit window for a subreddit.
     *
     * @param subreddit The subreddit name
     * @return Number of requests in the current window
     */
    fun getRateLimitStatus(subreddit: String): Int {
        val now = Instant.now().toEpochMilli()
        val timestamps = requestTimestamps[subreddit] ?: return 0

        synchronized(timestamps) {
            timestamps.removeIf { it < now - RATE_LIMIT_WINDOW_MS }
            return timestamps.size
        }
    }
}
