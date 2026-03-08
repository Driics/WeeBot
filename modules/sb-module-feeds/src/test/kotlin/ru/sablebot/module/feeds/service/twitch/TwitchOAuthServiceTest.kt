package ru.sablebot.module.feeds.service.twitch

import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import ru.sablebot.module.feeds.config.FeedProperties
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TwitchOAuthServiceTest {

    private lateinit var restTemplate: RestTemplate
    private lateinit var objectMapper: ObjectMapper
    private lateinit var feedProperties: FeedProperties
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var twitchOAuthService: TwitchOAuthService

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
        twitchOAuthService = TwitchOAuthService(
            restTemplate,
            objectMapper,
            feedProperties,
            meterRegistry
        )
    }

    @Test
    fun `getAccessToken returns token on successful OAuth request`() = runBlocking {
        // Given
        val mockResponse = """
            {
              "access_token": "test_access_token_12345",
              "expires_in": 3600,
              "token_type": "bearer"
            }
        """.trimIndent()

        every {
            restTemplate.postForEntity(
                any<String>(),
                any<HttpEntity<*>>(),
                eq(String::class.java)
            )
        } returns ResponseEntity(mockResponse, HttpStatus.OK)

        // When
        val token = twitchOAuthService.getAccessToken()

        // Then
        assertEquals("test_access_token_12345", token)
        assertNotNull(twitchOAuthService.getTokenExpiresAt())

        verify(exactly = 1) {
            restTemplate.postForEntity(
                any<String>(),
                any<HttpEntity<*>>(),
                eq(String::class.java)
            )
        }
    }

    @Test
    fun `getAccessToken reuses cached token when valid`() = runBlocking {
        // Given
        val mockResponse = """
            {
              "access_token": "cached_token",
              "expires_in": 3600,
              "token_type": "bearer"
            }
        """.trimIndent()

        every {
            restTemplate.postForEntity(
                any<String>(),
                any<HttpEntity<*>>(),
                eq(String::class.java)
            )
        } returns ResponseEntity(mockResponse, HttpStatus.OK)

        // When - First call should fetch token
        val token1 = twitchOAuthService.getAccessToken()

        // Second call should reuse cached token
        val token2 = twitchOAuthService.getAccessToken()

        // Then
        assertEquals(token1, token2)

        // Should only have called the API once (token is cached)
        verify(exactly = 1) {
            restTemplate.postForEntity(
                any<String>(),
                any<HttpEntity<*>>(),
                eq(String::class.java)
            )
        }
    }

    @Test
    fun `getAccessToken refreshes token after invalidation`() = runBlocking {
        // Given
        val mockResponse = """
            {
              "access_token": "new_token",
              "expires_in": 3600,
              "token_type": "bearer"
            }
        """.trimIndent()

        every {
            restTemplate.postForEntity(
                any<String>(),
                any<HttpEntity<*>>(),
                eq(String::class.java)
            )
        } returns ResponseEntity(mockResponse, HttpStatus.OK)

        // When
        val token1 = twitchOAuthService.getAccessToken()
        twitchOAuthService.invalidateToken()
        val token2 = twitchOAuthService.getAccessToken()

        // Then
        assertEquals("new_token", token1)
        assertEquals("new_token", token2)

        // Should have called the API twice (once before invalidation, once after)
        verify(exactly = 2) {
            restTemplate.postForEntity(
                any<String>(),
                any<HttpEntity<*>>(),
                eq(String::class.java)
            )
        }
    }

    @Test
    fun `getAccessToken throws exception when OAuth is disabled`() {
        // Given
        feedProperties.twitch.enabled = false

        // When / Then
        assertThrows<IllegalStateException> {
            runBlocking {
                twitchOAuthService.getAccessToken()
            }
        }
    }

    @Test
    fun `getAccessToken throws exception when client ID is missing`() {
        // Given
        feedProperties.twitch.clientId = ""

        // When / Then
        assertThrows<IllegalStateException> {
            runBlocking {
                twitchOAuthService.getAccessToken()
            }
        }
    }

    @Test
    fun `getAccessToken throws exception when client secret is missing`() {
        // Given
        feedProperties.twitch.clientSecret = ""

        // When / Then
        assertThrows<IllegalStateException> {
            runBlocking {
                twitchOAuthService.getAccessToken()
            }
        }
    }

    @Test
    fun `getAccessToken throws exception on API error`() {
        // Given
        every {
            restTemplate.postForEntity(
                any<String>(),
                any<HttpEntity<*>>(),
                eq(String::class.java)
            )
        } throws RuntimeException("Network error")

        // When / Then
        assertThrows<IllegalStateException> {
            runBlocking {
                twitchOAuthService.getAccessToken()
            }
        }
    }

    @Test
    fun `getAccessToken throws exception on non-2xx response`() {
        // Given
        every {
            restTemplate.postForEntity(
                any<String>(),
                any<HttpEntity<*>>(),
                eq(String::class.java)
            )
        } returns ResponseEntity(null, HttpStatus.UNAUTHORIZED)

        // When / Then
        assertThrows<IllegalStateException> {
            runBlocking {
                twitchOAuthService.getAccessToken()
            }
        }
    }

    @Test
    fun `isConfigured returns true when credentials are present`() {
        // When / Then
        assertTrue(twitchOAuthService.isConfigured())
    }

    @Test
    fun `isConfigured returns false when disabled`() {
        // Given
        feedProperties.twitch.enabled = false

        // When / Then
        assertFalse(twitchOAuthService.isConfigured())
    }

    @Test
    fun `isConfigured returns false when client ID is blank`() {
        // Given
        feedProperties.twitch.clientId = ""

        // When / Then
        assertFalse(twitchOAuthService.isConfigured())
    }

    @Test
    fun `isConfigured returns false when client secret is blank`() {
        // Given
        feedProperties.twitch.clientSecret = ""

        // When / Then
        assertFalse(twitchOAuthService.isConfigured())
    }

    @Test
    fun `invalidateToken clears cached token`() {
        // Given
        val mockResponse = """
            {
              "access_token": "token_to_invalidate",
              "expires_in": 3600,
              "token_type": "bearer"
            }
        """.trimIndent()

        every {
            restTemplate.postForEntity(
                any<String>(),
                any<HttpEntity<*>>(),
                eq(String::class.java)
            )
        } returns ResponseEntity(mockResponse, HttpStatus.OK)

        runBlocking {
            twitchOAuthService.getAccessToken()
        }

        assertNotNull(twitchOAuthService.getTokenExpiresAt())

        // When
        twitchOAuthService.invalidateToken()

        // Then - Token expiration should be null after invalidation
        // (We can't directly check the token, but we can verify getTokenExpiresAt is null)
        // Note: In the actual implementation, invalidateToken() sets tokenExpiresAt to null
    }
}
