package ru.sablebot.module.feeds.service.youtube

import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import ru.sablebot.module.feeds.config.FeedProperties
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YouTubeApiClientTest {

    private lateinit var restTemplate: RestTemplate
    private lateinit var objectMapper: ObjectMapper
    private lateinit var feedProperties: FeedProperties
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var youtubeApiClient: YouTubeApiClient

    @BeforeEach
    fun setup() {
        restTemplate = mockk()
        objectMapper = ObjectMapper()
        meterRegistry = SimpleMeterRegistry()
        feedProperties = FeedProperties().apply {
            youtube = FeedProperties.YouTube(
                enabled = true,
                apiKey = "test-api-key-12345",
                rateLimitQuotaPerDay = 10000
            )
        }
        youtubeApiClient = YouTubeApiClient(restTemplate, objectMapper, feedProperties, meterRegistry)
    }

    @Test
    fun `getRecentVideos returns videos on successful API calls`() = runBlocking {
        // Given
        val channelId = "UCtest123"
        val uploadsPlaylistId = "UUtest123"

        // Mock channels endpoint response
        val channelsResponse = """
            {
              "items": [
                {
                  "id": "$channelId",
                  "contentDetails": {
                    "relatedPlaylists": {
                      "uploads": "$uploadsPlaylistId"
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        // Mock playlistItems endpoint response
        val playlistItemsResponse = """
            {
              "items": [
                {
                  "snippet": {
                    "publishedAt": "2024-01-15T10:00:00Z",
                    "channelId": "$channelId",
                    "title": "Test Video",
                    "description": "Test description",
                    "channelTitle": "Test Channel",
                    "resourceId": {
                      "kind": "youtube#video",
                      "videoId": "video123"
                    }
                  },
                  "contentDetails": {
                    "videoId": "video123"
                  }
                }
              ]
            }
        """.trimIndent()

        // Mock videos endpoint response
        val videosResponse = """
            {
              "items": [
                {
                  "id": "video123",
                  "snippet": {
                    "publishedAt": "2024-01-15T10:00:00Z",
                    "channelId": "$channelId",
                    "title": "Test Video",
                    "description": "Test description",
                    "channelTitle": "Test Channel",
                    "thumbnails": {
                      "high": {
                        "url": "https://i.ytimg.com/vi/video123/hqdefault.jpg",
                        "width": 480,
                        "height": 360
                      }
                    },
                    "liveBroadcastContent": "none"
                  },
                  "statistics": {
                    "viewCount": "1000",
                    "likeCount": "50",
                    "commentCount": "10"
                  },
                  "contentDetails": {
                    "duration": "PT5M30S"
                  }
                }
              ]
            }
        """.trimIndent()

        every {
            restTemplate.exchange(
                match<String> { it.contains("/channels") },
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        } returns ResponseEntity(channelsResponse, HttpStatus.OK)

        every {
            restTemplate.exchange(
                match<String> { it.contains("/playlistItems") },
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        } returns ResponseEntity(playlistItemsResponse, HttpStatus.OK)

        every {
            restTemplate.exchange(
                match<String> { it.contains("/videos") },
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        } returns ResponseEntity(videosResponse, HttpStatus.OK)

        // When
        val videos = youtubeApiClient.getRecentVideos(channelId, maxResults = 10)

        // Then
        assertEquals(1, videos.size)
        assertEquals("video123", videos[0].id)
        assertEquals("Test Video", videos[0].title)
        assertEquals("Test description", videos[0].description)
        assertEquals(channelId, videos[0].channelId)
        assertEquals("Test Channel", videos[0].channelTitle)
        assertEquals(1000L, videos[0].viewCount)
        assertEquals(50L, videos[0].likeCount)
        assertEquals(10L, videos[0].commentCount)
        assertEquals("PT5M30S", videos[0].duration)
        assertEquals("https://www.youtube.com/watch?v=video123", videos[0].videoUrl)
        assertEquals("https://www.youtube.com/channel/$channelId", videos[0].channelUrl)

        verify(exactly = 1) {
            restTemplate.exchange(
                match<String> { it.contains("/channels") },
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        }

        verify(exactly = 1) {
            restTemplate.exchange(
                match<String> { it.contains("/playlistItems") },
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        }

        verify(exactly = 1) {
            restTemplate.exchange(
                match<String> { it.contains("/videos") },
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        }
    }

    @Test
    fun `getRecentVideos caches uploads playlist ID`() = runBlocking {
        // Given
        val channelId = "UCtest123"
        val uploadsPlaylistId = "UUtest123"

        val channelsResponse = """
            {
              "items": [
                {
                  "id": "$channelId",
                  "contentDetails": {
                    "relatedPlaylists": {
                      "uploads": "$uploadsPlaylistId"
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val playlistItemsResponse = """
            {
              "items": []
            }
        """.trimIndent()

        every {
            restTemplate.exchange(
                match<String> { it.contains("/channels") },
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        } returns ResponseEntity(channelsResponse, HttpStatus.OK)

        every {
            restTemplate.exchange(
                match<String> { it.contains("/playlistItems") },
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        } returns ResponseEntity(playlistItemsResponse, HttpStatus.OK)

        // When - Call twice
        youtubeApiClient.getRecentVideos(channelId)
        youtubeApiClient.getRecentVideos(channelId)

        // Then - Channels endpoint should only be called once (cached)
        verify(exactly = 1) {
            restTemplate.exchange(
                match<String> { it.contains("/channels") },
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        }

        // But playlistItems should be called twice
        verify(exactly = 2) {
            restTemplate.exchange(
                match<String> { it.contains("/playlistItems") },
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        }
    }

    @Test
    fun `getRecentVideos returns empty list when disabled`() = runBlocking {
        // Given
        feedProperties.youtube.enabled = false

        // When
        val videos = youtubeApiClient.getRecentVideos("UCtest123")

        // Then
        assertEquals(0, videos.size)

        verify(exactly = 0) {
            restTemplate.exchange(
                any<String>(),
                any<HttpMethod>(),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        }
    }

    @Test
    fun `getRecentVideos returns empty list when API key is blank`() = runBlocking {
        // Given
        feedProperties.youtube.apiKey = ""

        // When
        val videos = youtubeApiClient.getRecentVideos("UCtest123")

        // Then
        assertEquals(0, videos.size)

        verify(exactly = 0) {
            restTemplate.exchange(
                any<String>(),
                any<HttpMethod>(),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        }
    }

    @Test
    fun `getRecentVideos returns empty list on API error`() = runBlocking {
        // Given
        val channelId = "UCtest123"

        every {
            restTemplate.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        } throws RuntimeException("Network error")

        // When
        val videos = youtubeApiClient.getRecentVideos(channelId)

        // Then
        assertEquals(0, videos.size)
    }

    @Test
    fun `getVideo returns video on successful API call`() = runBlocking {
        // Given
        val videoId = "video123"

        val videosResponse = """
            {
              "items": [
                {
                  "id": "$videoId",
                  "snippet": {
                    "publishedAt": "2024-01-15T10:00:00Z",
                    "channelId": "UCtest123",
                    "title": "Single Video",
                    "description": "Video description",
                    "channelTitle": "Test Channel",
                    "liveBroadcastContent": "live"
                  },
                  "statistics": {
                    "viewCount": "5000"
                  },
                  "contentDetails": {
                    "duration": "PT1H30M"
                  }
                }
              ]
            }
        """.trimIndent()

        every {
            restTemplate.exchange(
                match<String> { it.contains("/videos") && it.contains(videoId) },
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        } returns ResponseEntity(videosResponse, HttpStatus.OK)

        // When
        val video = youtubeApiClient.getVideo(videoId)

        // Then
        assertNotNull(video)
        assertEquals(videoId, video.id)
        assertEquals("Single Video", video.title)
        assertEquals("Video description", video.description)
        assertEquals(5000L, video.viewCount)
        assertEquals("PT1H30M", video.duration)
        assertTrue(video.isLive)
    }

    @Test
    fun `getVideo returns null when not found`() = runBlocking {
        // Given
        val videoId = "nonexistent"

        val videosResponse = """
            {
              "items": []
            }
        """.trimIndent()

        every {
            restTemplate.exchange(
                match<String> { it.contains("/videos") },
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        } returns ResponseEntity(videosResponse, HttpStatus.OK)

        // When
        val video = youtubeApiClient.getVideo(videoId)

        // Then
        assertNull(video)
    }

    @Test
    fun `quota tracking prevents requests when limit reached`() = runBlocking {
        // Given
        feedProperties.youtube.rateLimitQuotaPerDay = 5 // Very low quota
        val channelId = "UCtest123"

        val channelsResponse = """
            {
              "items": [
                {
                  "id": "$channelId",
                  "contentDetails": {
                    "relatedPlaylists": {
                      "uploads": "UUtest123"
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val playlistItemsResponse = """{"items": []}""".trimIndent()
        val videosResponse = """{"items": []}""".trimIndent()

        every {
            restTemplate.exchange(
                match<String> { it.contains("/channels") },
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        } returns ResponseEntity(channelsResponse, HttpStatus.OK)

        every {
            restTemplate.exchange(
                match<String> { it.contains("/playlistItems") },
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        } returns ResponseEntity(playlistItemsResponse, HttpStatus.OK)

        every {
            restTemplate.exchange(
                match<String> { it.contains("/videos") },
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        } returns ResponseEntity(videosResponse, HttpStatus.OK)

        // When - First call should succeed (uses 3 quota: channels + playlistItems + videos)
        val result1 = youtubeApiClient.getRecentVideos(channelId)
        assertNotNull(result1)

        // Second call should fail due to quota limit (would need 2 more quota: playlistItems + videos, but only 2 left)
        // Actually it needs playlistItems (1) + videos (1) = 2, and we have 2 left, so it should work
        val result2 = youtubeApiClient.getRecentVideos(channelId)
        assertNotNull(result2)

        // Third call should fail (would need 2 quota but we have 0 left)
        val result3 = youtubeApiClient.getRecentVideos(channelId)
        assertEquals(0, result3.size) // Should be blocked by quota
    }

    @Test
    fun `getQuotaInfo returns correct quota information`() = runBlocking {
        // Given - Fresh client with no API calls

        // When
        val (used, max) = youtubeApiClient.getQuotaInfo()

        // Then
        assertEquals(0, used)
        assertEquals(10000, max)

        // Make an API call
        val videoId = "video123"
        val videosResponse = """{"items": []}""".trimIndent()

        every {
            restTemplate.exchange(
                match<String> { it.contains("/videos") },
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        } returns ResponseEntity(videosResponse, HttpStatus.OK)

        youtubeApiClient.getVideo(videoId)

        // Check quota again
        val (usedAfter, maxAfter) = youtubeApiClient.getQuotaInfo()
        assertEquals(1, usedAfter) // videos endpoint costs 1 quota
        assertEquals(10000, maxAfter)
    }

    @Test
    fun `video properties are correctly parsed`() = runBlocking {
        // Given
        val videoId = "video123"
        val videosResponse = """
            {
              "items": [
                {
                  "id": "$videoId",
                  "snippet": {
                    "publishedAt": "2024-01-15T10:00:00Z",
                    "channelId": "UCtest123",
                    "title": "Test Video",
                    "channelTitle": "Test Channel",
                    "liveBroadcastContent": "upcoming"
                  }
                }
              ]
            }
        """.trimIndent()

        every {
            restTemplate.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        } returns ResponseEntity(videosResponse, HttpStatus.OK)

        // When
        val video = youtubeApiClient.getVideo(videoId)

        // Then
        assertNotNull(video)
        assertTrue(video.isUpcoming)
        assertEquals("upcoming", video.liveBroadcastContent)
    }

    @Test
    fun `handles non-2xx HTTP response gracefully`() = runBlocking {
        // Given
        val channelId = "UCtest123"

        every {
            restTemplate.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        } returns ResponseEntity<String>(null, HttpStatus.FORBIDDEN)

        // When
        val videos = youtubeApiClient.getRecentVideos(channelId)

        // Then
        assertEquals(0, videos.size)
    }
}
