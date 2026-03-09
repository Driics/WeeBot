package ru.sablebot.module.feeds.service.reddit

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
import kotlin.test.assertTrue

class RedditApiClientTest {

    private lateinit var restTemplate: RestTemplate
    private lateinit var objectMapper: ObjectMapper
    private lateinit var feedProperties: FeedProperties
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var redditApiClient: RedditApiClient

    @BeforeEach
    fun setup() {
        restTemplate = mockk()
        objectMapper = ObjectMapper()
        meterRegistry = SimpleMeterRegistry()
        feedProperties = FeedProperties().apply {
            reddit = FeedProperties.Reddit(
                enabled = true,
                userAgent = "SableBot Test/1.0",
                rateLimitRequestsPerMinute = 60
            )
        }
        redditApiClient = RedditApiClient(restTemplate, objectMapper, feedProperties, meterRegistry)
    }

    @Test
    fun `getNewPosts returns posts on successful API call`() = runBlocking {
        // Given
        val subreddit = "kotlin"
        val mockResponse = """
            {
              "kind": "Listing",
              "data": {
                "children": [
                  {
                    "kind": "t3",
                    "data": {
                      "id": "abc123",
                      "title": "Test Post",
                      "author": "testuser",
                      "subreddit": "kotlin",
                      "permalink": "/r/kotlin/comments/abc123/test_post/",
                      "url": "https://www.reddit.com/r/kotlin/comments/abc123/test_post/",
                      "created_utc": 1234567890,
                      "score": 42,
                      "num_comments": 5,
                      "is_self": true
                    }
                  }
                ]
              }
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
        val posts = redditApiClient.getNewPosts(subreddit, limit = 25)

        // Then
        assertEquals(1, posts.size)
        assertEquals("abc123", posts[0].id)
        assertEquals("Test Post", posts[0].title)
        assertEquals("testuser", posts[0].author)
        assertEquals("kotlin", posts[0].subreddit)
        assertEquals(42, posts[0].score)
        assertEquals(5, posts[0].numComments)
        assertTrue(posts[0].isSelf)

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
    fun `getNewPosts returns empty list on API error`() = runBlocking {
        // Given
        val subreddit = "kotlin"

        every {
            restTemplate.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        } throws RuntimeException("Network error")

        // When
        val posts = redditApiClient.getNewPosts(subreddit)

        // Then
        assertEquals(0, posts.size)
    }

    @Test
    fun `getNewPosts respects rate limiting`() = runBlocking {
        // Given
        val subreddit = "kotlin"
        feedProperties.reddit.rateLimitRequestsPerMinute = 2

        val mockResponse = """
            {
              "kind": "Listing",
              "data": {
                "children": []
              }
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
        val result1 = redditApiClient.getNewPosts(subreddit)
        val result2 = redditApiClient.getNewPosts(subreddit)
        val result3 = redditApiClient.getNewPosts(subreddit) // Should be rate limited

        // Then
        assertNotNull(result1)
        assertNotNull(result2)
        assertEquals(0, result3.size) // Rate limited, returns empty list

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
    fun `getRateLimitStatus returns correct count`() = runBlocking {
        // Given
        val subreddit = "kotlin"
        val mockResponse = """
            {
              "kind": "Listing",
              "data": {
                "children": []
              }
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
        assertEquals(0, redditApiClient.getRateLimitStatus(subreddit))
        redditApiClient.getNewPosts(subreddit)
        assertEquals(1, redditApiClient.getRateLimitStatus(subreddit))
        redditApiClient.getNewPosts(subreddit)
        assertEquals(2, redditApiClient.getRateLimitStatus(subreddit))
    }

    @Test
    fun `clearRateLimit resets rate limit counter`() = runBlocking {
        // Given
        val subreddit = "kotlin"
        val mockResponse = """
            {
              "kind": "Listing",
              "data": {
                "children": []
              }
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
        redditApiClient.getNewPosts(subreddit)
        assertEquals(1, redditApiClient.getRateLimitStatus(subreddit))

        redditApiClient.clearRateLimit(subreddit)

        // Then
        assertEquals(0, redditApiClient.getRateLimitStatus(subreddit))
    }

    @Test
    fun `getNewPosts with pagination parameter`() = runBlocking {
        // Given
        val subreddit = "kotlin"
        val after = "t3_abc123"
        val mockResponse = """
            {
              "kind": "Listing",
              "data": {
                "children": [],
                "after": "t3_def456"
              }
            }
        """.trimIndent()

        every {
            restTemplate.exchange(
                match<String> { it.contains("after=$after") },
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        } returns ResponseEntity(mockResponse, HttpStatus.OK)

        // When
        val posts = redditApiClient.getNewPosts(subreddit, limit = 25, after = after)

        // Then
        assertNotNull(posts)
        verify {
            restTemplate.exchange(
                match<String> { it.contains("after=$after") },
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        }
    }
}
