package ru.sablebot.module.feeds.service.twitch

import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.coEvery
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

class TwitchApiClientTest {

    private lateinit var restTemplate: RestTemplate
    private lateinit var objectMapper: ObjectMapper
    private lateinit var feedProperties: FeedProperties
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var twitchOAuthService: TwitchOAuthService
    private lateinit var twitchApiClient: TwitchApiClient

    @BeforeEach
    fun setup() {
        restTemplate = mockk()
        objectMapper = ObjectMapper()
        meterRegistry = SimpleMeterRegistry()
        feedProperties = FeedProperties().apply {
            twitch = FeedProperties.Twitch(
                enabled = true,
                clientId = "test_client_id",
                clientSecret = "test_client_secret",
                rateLimitRequestsPerMinute = 800
            )
        }
        twitchOAuthService = mockk()

        // Mock OAuth service to return a valid token
        coEvery { twitchOAuthService.getAccessToken() } returns "mock_access_token"
        every { twitchOAuthService.isConfigured() } returns true

        twitchApiClient = TwitchApiClient(
            restTemplate,
            objectMapper,
            feedProperties,
            meterRegistry,
            twitchOAuthService
        )
    }

    @Test
    fun `getStream returns stream when user is live`() = runBlocking {
        // Given
        val userLogin = "teststreamer"
        val mockResponse = """
            {
              "data": [
                {
                  "id": "123456789",
                  "user_id": "98765",
                  "user_login": "teststreamer",
                  "user_name": "TestStreamer",
                  "game_id": "12345",
                  "game_name": "Just Chatting",
                  "type": "live",
                  "title": "Test Stream Title",
                  "viewer_count": 42,
                  "started_at": "2024-01-01T12:00:00Z",
                  "language": "en",
                  "thumbnail_url": "https://static-cdn.jtvnw.net/previews-ttv/live_user_teststreamer-{width}x{height}.jpg",
                  "is_mature": false
                }
              ],
              "pagination": {}
            }
        """.trimIndent()

        every {
            restTemplate.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        } returns ResponseEntity(mockResponse, HttpStatus.OK)

        // When
        val stream = twitchApiClient.getStream(userLogin)

        // Then
        assertNotNull(stream)
        assertEquals("123456789", stream.id)
        assertEquals("teststreamer", stream.userLogin)
        assertEquals("TestStreamer", stream.userName)
        assertEquals("Just Chatting", stream.gameName)
        assertEquals("Test Stream Title", stream.title)
        assertEquals(42, stream.viewerCount)
        assertTrue(stream.isLive)
        assertEquals("https://twitch.tv/teststreamer", stream.channelUrl)

        verify(exactly = 1) {
            restTemplate.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        }
    }

    @Test
    fun `getStream returns null when user is offline`() = runBlocking {
        // Given
        val userLogin = "offlinestreamer"
        val mockResponse = """
            {
              "data": [],
              "pagination": {}
            }
        """.trimIndent()

        every {
            restTemplate.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        } returns ResponseEntity(mockResponse, HttpStatus.OK)

        // When
        val stream = twitchApiClient.getStream(userLogin)

        // Then
        assertNull(stream)
    }

    @Test
    fun `getStream returns null on API error`() = runBlocking {
        // Given
        val userLogin = "teststreamer"

        every {
            restTemplate.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        } throws RuntimeException("Network error")

        // When
        val stream = twitchApiClient.getStream(userLogin)

        // Then
        assertNull(stream)
    }

    @Test
    fun `getStream respects rate limiting`() = runBlocking {
        // Given
        val userLogin = "teststreamer"
        feedProperties.twitch.rateLimitRequestsPerMinute = 2

        val mockResponse = """
            {
              "data": [],
              "pagination": {}
            }
        """.trimIndent()

        every {
            restTemplate.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        } returns ResponseEntity(mockResponse, HttpStatus.OK)

        // When - Make 3 requests, the 3rd should be rate limited
        val result1 = twitchApiClient.getStream(userLogin)
        val result2 = twitchApiClient.getStream(userLogin)
        val result3 = twitchApiClient.getStream(userLogin) // Should be rate limited

        // Then
        assertNull(result1) // Offline
        assertNull(result2) // Offline
        assertNull(result3) // Rate limited

        // Should only have called the API twice
        verify(exactly = 2) {
            restTemplate.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        }
    }

    @Test
    fun `getStreams returns multiple live streams`() = runBlocking {
        // Given
        val userLogins = listOf("streamer1", "streamer2")
        val mockResponse = """
            {
              "data": [
                {
                  "id": "123",
                  "user_id": "98765",
                  "user_login": "streamer1",
                  "user_name": "Streamer1",
                  "game_id": "12345",
                  "game_name": "Game 1",
                  "type": "live",
                  "title": "Stream 1",
                  "viewer_count": 100,
                  "started_at": "2024-01-01T12:00:00Z",
                  "language": "en",
                  "thumbnail_url": "https://example.com/thumb1.jpg",
                  "is_mature": false
                },
                {
                  "id": "456",
                  "user_id": "98766",
                  "user_login": "streamer2",
                  "user_name": "Streamer2",
                  "game_id": "12346",
                  "game_name": "Game 2",
                  "type": "live",
                  "title": "Stream 2",
                  "viewer_count": 200,
                  "started_at": "2024-01-01T13:00:00Z",
                  "language": "en",
                  "thumbnail_url": "https://example.com/thumb2.jpg",
                  "is_mature": false
                }
              ],
              "pagination": {}
            }
        """.trimIndent()

        every {
            restTemplate.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        } returns ResponseEntity(mockResponse, HttpStatus.OK)

        // When
        val streams = twitchApiClient.getStreams(userLogins)

        // Then
        assertEquals(2, streams.size)
        assertEquals("streamer1", streams[0].userLogin)
        assertEquals("streamer2", streams[1].userLogin)
        assertTrue(streams.all { it.isLive })
    }

    @Test
    fun `getStreams returns empty list when no users are live`() = runBlocking {
        // Given
        val userLogins = listOf("streamer1", "streamer2")
        val mockResponse = """
            {
              "data": [],
              "pagination": {}
            }
        """.trimIndent()

        every {
            restTemplate.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        } returns ResponseEntity(mockResponse, HttpStatus.OK)

        // When
        val streams = twitchApiClient.getStreams(userLogins)

        // Then
        assertEquals(0, streams.size)
    }

    @Test
    fun `getStreams returns empty list when input is empty`() = runBlocking {
        // Given
        val userLogins = emptyList<String>()

        // When
        val streams = twitchApiClient.getStreams(userLogins)

        // Then
        assertEquals(0, streams.size)

        // Should not call the API at all
        verify(exactly = 0) {
            restTemplate.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        }
    }

    @Test
    fun `getRateLimitStatus returns correct count`() = runBlocking {
        // Given
        val userLogin = "teststreamer"
        val mockResponse = """
            {
              "data": [],
              "pagination": {}
            }
        """.trimIndent()

        every {
            restTemplate.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        } returns ResponseEntity(mockResponse, HttpStatus.OK)

        // When
        assertEquals(0, twitchApiClient.getRateLimitStatus())
        twitchApiClient.getStream(userLogin)
        assertEquals(1, twitchApiClient.getRateLimitStatus())
        twitchApiClient.getStream(userLogin)
        assertEquals(2, twitchApiClient.getRateLimitStatus())
    }

    @Test
    fun `clearRateLimit resets rate limit counter`() = runBlocking {
        // Given
        val userLogin = "teststreamer"
        val mockResponse = """
            {
              "data": [],
              "pagination": {}
            }
        """.trimIndent()

        every {
            restTemplate.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        } returns ResponseEntity(mockResponse, HttpStatus.OK)

        // When
        twitchApiClient.getStream(userLogin)
        assertEquals(1, twitchApiClient.getRateLimitStatus())

        twitchApiClient.clearRateLimit()

        // Then
        assertEquals(0, twitchApiClient.getRateLimitStatus())
    }

    @Test
    fun `getThumbnailUrl formats correctly`() {
        // Given
        val mockResponse = """
            {
              "data": [
                {
                  "id": "123",
                  "user_id": "98765",
                  "user_login": "teststreamer",
                  "user_name": "TestStreamer",
                  "game_id": "12345",
                  "game_name": "Just Chatting",
                  "type": "live",
                  "title": "Test Stream",
                  "viewer_count": 42,
                  "started_at": "2024-01-01T12:00:00Z",
                  "language": "en",
                  "thumbnail_url": "https://example.com/thumb-{width}x{height}.jpg",
                  "is_mature": false
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
        } returns ResponseEntity(mockResponse, HttpStatus.OK)

        // When
        runBlocking {
            val stream = twitchApiClient.getStream("teststreamer")

            // Then
            assertNotNull(stream)
            assertEquals("https://example.com/thumb-320x180.jpg", stream.getThumbnailUrl())
            assertEquals("https://example.com/thumb-640x360.jpg", stream.getThumbnailUrl(640, 360))
        }
    }
}
