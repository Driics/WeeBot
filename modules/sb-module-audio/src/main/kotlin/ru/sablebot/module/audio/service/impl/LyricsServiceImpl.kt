package ru.sablebot.module.audio.service.impl

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForObject
import org.springframework.web.util.UriComponentsBuilder
import ru.sablebot.module.audio.model.LyricsResult
import ru.sablebot.module.audio.service.ILyricsService
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Service
class LyricsServiceImpl(
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper
) : ILyricsService {

    companion object {
        private val log = KotlinLogging.logger { }
        private const val LRCLIB_API = "https://lrclib.net/api"
        private const val RATE_LIMIT_WINDOW_MS = 1000L // 1 second between requests
    }

    private val lastRequestTime = AtomicLong(0)
    private val requestCount = ConcurrentHashMap<String, Long>()

    override suspend fun searchLyrics(query: String): LyricsResult? = withContext(Dispatchers.IO) {
        try {
            // Rate limiting
            enforceRateLimit()

            val url = UriComponentsBuilder.fromUri(URI.create(LRCLIB_API))
                .path("/search")
                .queryParam("q", query)
                .build(true)
                .toUriString()

            log.debug { "Searching lyrics with query: $query" }

            val response = restTemplate.getForObject<String>(url)
            val searchResults = objectMapper.readValue(response, Array<LrcLibLyricsResponse>::class.java)

            if (searchResults.isEmpty()) {
                log.debug { "No lyrics found for query: $query" }
                return@withContext null
            }

            // Return first result
            val result = searchResults.first()
            toLyricsResult(result)
        } catch (e: Exception) {
            log.error(e) { "Error searching lyrics for query: $query" }
            null
        }
    }

    override suspend fun getLyrics(trackName: String, artistName: String): LyricsResult? =
        withContext(Dispatchers.IO) {
            try {
                // Rate limiting
                enforceRateLimit()

                val url = UriComponentsBuilder.fromUri(URI.create(LRCLIB_API))
                    .path("/get")
                    .queryParam("track_name", trackName)
                    .queryParam("artist_name", artistName)
                    .build(true)
                    .toUriString()

                log.debug { "Fetching lyrics for track: $trackName by $artistName" }

                val response = restTemplate.getForObject<LrcLibLyricsResponse>(url)
                toLyricsResult(response)
            } catch (e: Exception) {
                log.error(e) { "Error fetching lyrics for track: $trackName by $artistName" }
                null
            }
        }

    private fun toLyricsResult(response: LrcLibLyricsResponse): LyricsResult {
        return LyricsResult(
            trackName = response.trackName ?: "Unknown",
            artistName = response.artistName ?: "Unknown",
            plainLyrics = response.plainLyrics,
            syncedLyrics = response.syncedLyrics
        )
    }

    private fun enforceRateLimit() {
        val now = System.currentTimeMillis()
        val last = lastRequestTime.get()
        val timeSinceLastRequest = now - last

        if (timeSinceLastRequest < RATE_LIMIT_WINDOW_MS) {
            val sleepTime = RATE_LIMIT_WINDOW_MS - timeSinceLastRequest
            log.debug { "Rate limiting: sleeping for ${sleepTime}ms" }
            Thread.sleep(sleepTime)
        }

        lastRequestTime.set(System.currentTimeMillis())
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class LrcLibLyricsResponse(
    val id: Int? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean? = null,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null
)
