package ru.sablebot.module.feeds.service.youtube

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
import ru.sablebot.module.feeds.model.youtube.*
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Client for interacting with the YouTube Data API v3.
 * Fetches recent videos and livestream notifications with quota management.
 */
@Service
class YouTubeApiClient(
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper,
    private val feedProperties: FeedProperties,
    private val meterRegistry: MeterRegistry
) {
    private val log = KotlinLogging.logger {}

    companion object {
        private const val YOUTUBE_BASE_URL = "https://www.googleapis.com/youtube/v3"

        // API quota costs (units per call)
        private const val QUOTA_CHANNELS = 1
        private const val QUOTA_PLAYLIST_ITEMS = 1
        private const val QUOTA_VIDEOS = 1
        private const val QUOTA_SEARCH = 100
    }

    // Quota tracking: reset daily
    private val quotaUsedToday = AtomicInteger(0)
    private var quotaResetDate = LocalDate.now()

    // Cache for channel uploads playlist IDs to reduce API calls
    private val uploadsPlaylistCache = ConcurrentHashMap<String, String>()

    /**
     * Fetches recent videos from a YouTube channel.
     *
     * @param channelId The YouTube channel ID (starts with UC)
     * @param maxResults Maximum number of videos to fetch (default 10, max 50)
     * @return List of YouTubeVideo objects, or empty list on error
     */
    suspend fun getRecentVideos(
        channelId: String,
        maxResults: Int = 10
    ): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        try {
            if (!feedProperties.youtube.enabled) {
                log.debug { "YouTube integration is disabled" }
                return@withContext emptyList()
            }

            if (feedProperties.youtube.apiKey.isBlank()) {
                log.warn { "YouTube API key is not configured" }
                return@withContext emptyList()
            }

            // Check quota limit
            if (!checkQuota(QUOTA_CHANNELS + QUOTA_PLAYLIST_ITEMS + QUOTA_VIDEOS)) {
                log.warn { "YouTube API quota limit reached for today" }
                meterRegistry.counter("sablebot.feeds.youtube.quota.exceeded").increment()
                return@withContext emptyList()
            }

            val actualMaxResults = maxResults.coerceIn(1, 50)

            // Step 1: Get uploads playlist ID (cached)
            val uploadsPlaylistId = getUploadsPlaylistId(channelId) ?: run {
                log.warn { "Could not find uploads playlist for channel: $channelId" }
                return@withContext emptyList()
            }

            // Step 2: Get playlist items
            val playlistItems = getPlaylistItems(uploadsPlaylistId, actualMaxResults)
            if (playlistItems.isEmpty()) {
                log.debug { "No playlist items found for channel: $channelId" }
                return@withContext emptyList()
            }

            // Step 3: Get detailed video information
            val videoIds = playlistItems.mapNotNull { item ->
                item.contentDetails?.videoId ?: item.snippet.resourceId?.videoId
            }

            if (videoIds.isEmpty()) {
                log.debug { "No video IDs extracted from playlist items" }
                return@withContext emptyList()
            }

            val videos = getVideosDetails(videoIds)

            log.debug { "Fetched ${videos.size} videos from channel $channelId" }
            meterRegistry.counter("sablebot.feeds.youtube.api.success").increment()
            meterRegistry.counter("sablebot.feeds.youtube.videos.fetched").increment(videos.size.toDouble())

            videos
        } catch (e: Exception) {
            log.error(e) { "Error fetching YouTube videos from channel $channelId" }
            meterRegistry.counter("sablebot.feeds.youtube.api.errors", "type", "exception").increment()
            emptyList()
        }
    }

    /**
     * Fetches a single video by ID.
     *
     * @param videoId The YouTube video ID
     * @return YouTubeVideo or null if not found
     */
    suspend fun getVideo(videoId: String): YouTubeVideo? = withContext(Dispatchers.IO) {
        try {
            if (!feedProperties.youtube.enabled || feedProperties.youtube.apiKey.isBlank()) {
                return@withContext null
            }

            if (!checkQuota(QUOTA_VIDEOS)) {
                log.warn { "YouTube API quota limit reached for today" }
                meterRegistry.counter("sablebot.feeds.youtube.quota.exceeded").increment()
                return@withContext null
            }

            val videos = getVideosDetails(listOf(videoId))
            val video = videos.firstOrNull()

            if (video != null) {
                meterRegistry.counter("sablebot.feeds.youtube.api.success").increment()
            }

            video
        } catch (e: Exception) {
            log.error(e) { "Error fetching YouTube video $videoId" }
            meterRegistry.counter("sablebot.feeds.youtube.api.errors", "type", "exception").increment()
            null
        }
    }

    /**
     * Gets the uploads playlist ID for a channel.
     * Results are cached to reduce API calls.
     *
     * @param channelId The YouTube channel ID
     * @return Uploads playlist ID or null if not found
     */
    private suspend fun getUploadsPlaylistId(channelId: String): String? = withContext(Dispatchers.IO) {
        // Check cache first
        uploadsPlaylistCache[channelId]?.let {
            log.debug { "Using cached uploads playlist ID for channel $channelId" }
            return@withContext it
        }

        try {
            val url = UriComponentsBuilder.fromUri(URI.create(YOUTUBE_BASE_URL))
                .path("/channels")
                .queryParam("part", "contentDetails")
                .queryParam("id", channelId)
                .queryParam("key", feedProperties.youtube.apiKey)
                .build(true)
                .toUriString()

            log.debug { "Fetching uploads playlist ID for channel $channelId" }

            val response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                HttpEntity<String>(HttpHeaders()),
                String::class.java
            )

            if (!response.statusCode.is2xxSuccessful || response.body == null) {
                log.warn { "YouTube API returned non-successful status: ${response.statusCode}" }
                meterRegistry.counter("sablebot.feeds.youtube.api.errors", "status", response.statusCode.toString()).increment()
                return@withContext null
            }

            val channelsResponse = objectMapper.readValue(response.body, YouTubeChannelsResponse::class.java)
            val uploadsPlaylistId = channelsResponse.items.firstOrNull()?.contentDetails?.relatedPlaylists?.uploads

            if (uploadsPlaylistId != null) {
                uploadsPlaylistCache[channelId] = uploadsPlaylistId
                recordQuotaUsage(QUOTA_CHANNELS)
                log.debug { "Found uploads playlist ID: $uploadsPlaylistId for channel $channelId" }
            } else {
                log.warn { "No uploads playlist found for channel $channelId" }
            }

            uploadsPlaylistId
        } catch (e: Exception) {
            log.error(e) { "Error fetching uploads playlist ID for channel $channelId" }
            null
        }
    }

    /**
     * Gets playlist items from a playlist.
     *
     * @param playlistId The YouTube playlist ID
     * @param maxResults Maximum number of items to fetch
     * @return List of playlist items
     */
    private suspend fun getPlaylistItems(
        playlistId: String,
        maxResults: Int
    ): List<YouTubePlaylistItem> = withContext(Dispatchers.IO) {
        try {
            val url = UriComponentsBuilder.fromUri(URI.create(YOUTUBE_BASE_URL))
                .path("/playlistItems")
                .queryParam("part", "snippet,contentDetails")
                .queryParam("playlistId", playlistId)
                .queryParam("maxResults", maxResults)
                .queryParam("key", feedProperties.youtube.apiKey)
                .build(true)
                .toUriString()

            log.debug { "Fetching playlist items from playlist $playlistId (max=$maxResults)" }

            val response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                HttpEntity<String>(HttpHeaders()),
                String::class.java
            )

            if (!response.statusCode.is2xxSuccessful || response.body == null) {
                log.warn { "YouTube API returned non-successful status: ${response.statusCode}" }
                meterRegistry.counter("sablebot.feeds.youtube.api.errors", "status", response.statusCode.toString()).increment()
                return@withContext emptyList()
            }

            val playlistResponse = objectMapper.readValue(response.body, YouTubePlaylistItemsResponse::class.java)
            recordQuotaUsage(QUOTA_PLAYLIST_ITEMS)

            playlistResponse.items
        } catch (e: Exception) {
            log.error(e) { "Error fetching playlist items from playlist $playlistId" }
            emptyList()
        }
    }

    /**
     * Gets detailed video information for a list of video IDs.
     *
     * @param videoIds List of video IDs (max 50)
     * @return List of YouTubeVideo objects
     */
    private suspend fun getVideosDetails(videoIds: List<String>): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        if (videoIds.isEmpty()) {
            return@withContext emptyList()
        }

        try {
            // YouTube API supports up to 50 video IDs in a single request
            val limitedVideoIds = videoIds.take(50)
            val videoIdsParam = limitedVideoIds.joinToString(",")

            val url = UriComponentsBuilder.fromUri(URI.create(YOUTUBE_BASE_URL))
                .path("/videos")
                .queryParam("part", "snippet,statistics,contentDetails")
                .queryParam("id", videoIdsParam)
                .queryParam("key", feedProperties.youtube.apiKey)
                .build(true)
                .toUriString()

            log.debug { "Fetching details for ${limitedVideoIds.size} videos" }

            val response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                HttpEntity<String>(HttpHeaders()),
                String::class.java
            )

            if (!response.statusCode.is2xxSuccessful || response.body == null) {
                log.warn { "YouTube API returned non-successful status: ${response.statusCode}" }
                meterRegistry.counter("sablebot.feeds.youtube.api.errors", "status", response.statusCode.toString()).increment()
                return@withContext emptyList()
            }

            val videosResponse = objectMapper.readValue(response.body, YouTubeVideosResponse::class.java)
            recordQuotaUsage(QUOTA_VIDEOS)

            // Convert to simplified YouTubeVideo objects
            videosResponse.items.map { item ->
                YouTubeVideo(
                    id = item.id,
                    title = item.snippet.title,
                    description = item.snippet.description,
                    channelId = item.snippet.channelId,
                    channelTitle = item.snippet.channelTitle,
                    publishedAt = item.snippet.publishedAt,
                    thumbnailUrl = item.snippet.thumbnails?.high?.url
                        ?: item.snippet.thumbnails?.medium?.url
                        ?: item.snippet.thumbnails?.default?.url,
                    liveBroadcastContent = item.snippet.liveBroadcastContent ?: "none",
                    viewCount = item.statistics?.viewCount?.toLongOrNull(),
                    likeCount = item.statistics?.likeCount?.toLongOrNull(),
                    commentCount = item.statistics?.commentCount?.toLongOrNull(),
                    duration = item.contentDetails?.duration
                )
            }
        } catch (e: Exception) {
            log.error(e) { "Error fetching video details" }
            emptyList()
        }
    }

    /**
     * Check if we have enough quota remaining for an operation.
     *
     * @param quotaCost The quota cost of the operation
     * @return true if quota is available, false if limit would be exceeded
     */
    private fun checkQuota(quotaCost: Int): Boolean {
        resetQuotaIfNeeded()

        val currentQuota = quotaUsedToday.get()
        val maxQuota = feedProperties.youtube.rateLimitQuotaPerDay

        if (currentQuota + quotaCost > maxQuota) {
            log.warn { "YouTube quota check failed: current=$currentQuota, cost=$quotaCost, max=$maxQuota" }
            return false
        }

        return true
    }

    /**
     * Record quota usage for an API call.
     *
     * @param quotaCost The quota cost of the operation
     */
    private fun recordQuotaUsage(quotaCost: Int) {
        resetQuotaIfNeeded()

        val newQuota = quotaUsedToday.addAndGet(quotaCost)
        meterRegistry.gauge("sablebot.feeds.youtube.quota.used", quotaUsedToday)

        log.debug { "YouTube quota used: $newQuota / ${feedProperties.youtube.rateLimitQuotaPerDay}" }

        // Warn if approaching quota limit (90%)
        val maxQuota = feedProperties.youtube.rateLimitQuotaPerDay
        if (newQuota > maxQuota * 0.9) {
            log.warn { "YouTube quota usage is at ${(newQuota.toDouble() / maxQuota * 100).toInt()}%" }
            meterRegistry.counter("sablebot.feeds.youtube.quota.warning").increment()
        }
    }

    /**
     * Reset quota counter if a new day has started.
     */
    private fun resetQuotaIfNeeded() {
        val today = LocalDate.now()
        if (today != quotaResetDate) {
            log.info { "Resetting YouTube quota counter for new day: $today" }
            quotaUsedToday.set(0)
            quotaResetDate = today
            meterRegistry.counter("sablebot.feeds.youtube.quota.reset").increment()
        }
    }

    /**
     * Get current quota usage information.
     *
     * @return Pair of (used quota, max quota)
     */
    fun getQuotaInfo(): Pair<Int, Int> {
        resetQuotaIfNeeded()
        return Pair(quotaUsedToday.get(), feedProperties.youtube.rateLimitQuotaPerDay)
    }
}
