package ru.sablebot.common.worker.modules.game

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForObject
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

@Service
class SteamService(
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper
) : PriceProvider {
    companion object {
        private val log = KotlinLogging.logger { }
        private const val STEAM_STORE_API = "https://store.steampowered.com/api"
        private const val STEAM_SEARCH_API = "https://steamcommunity.com/actions/SearchApps"
    }

    override val providerName: String = "Steam"

    override suspend fun getPrice(gameId: String): PriceInfo? = withContext(Dispatchers.IO) {
        try {

            val url = UriComponentsBuilder.fromUri(URI.create(STEAM_STORE_API))
                .path("/appdetails")
                .queryParam("appids", gameId)
                .queryParam("cc", "us")
                .build(true).toUriString()

            val response = restTemplate.getForObject<Map<String, Any>>(url)

            @Suppress("UNCHECKED_CAST")
            val appData = response[gameId] as? Map<String, Any> ?: return@withContext null

            val success = appData["success"] as? Boolean ?: false
            if (!success) {
                log.warn { "Steam API returned success=false for appId=$gameId" }
                return@withContext null
            }

            @Suppress("UNCHECKED_CAST")
            val data = appData["data"] as? Map<String, Any> ?: return@withContext null

            val name = data["name"] as? String ?: "Unknown Game"
            val isFree = data["is_free"] as? Boolean ?: false

            @Suppress("UNCHECKED_CAST")
            val priceOverview = data["price_overview"] as? Map<String, Any>

            if (isFree) {
                return@withContext PriceInfo(
                    gameId = gameId,
                    gameName = name,
                    currency = "USD",
                    originalPrice = 0.0,
                    discountedPrice = null,
                    discountPercentage = null,
                    storeUrl = "https://store.steampowered.com/app/$gameId",
                    providerName = providerName
                )
            }

            if (priceOverview == null) {
                log.warn { "No price overview for appId=$gameId" }
                return@withContext null
            }

            val currency = priceOverview["currency"] as? String ?: "USD"
            val initial = (priceOverview["initial"] as? Number)?.toDouble()?.div(100)
            val final = (priceOverview["final"] as? Number)?.toDouble()?.div(100)
            val discountPercent = priceOverview["discount_percent"] as? Int

            PriceInfo(
                gameId = gameId,
                gameName = name,
                currency = currency,
                originalPrice = initial,
                discountedPrice = if (discountPercent != null && discountPercent > 0) final else null,
                discountPercentage = discountPercent?.takeIf { it > 0 },
                storeUrl = "https://store.steampowered.com/app/$gameId",
                providerName = providerName
            )
        } catch (e: Exception) {
            log.error(e) { "Error fetching Steam price for appId=$gameId" }
            null
        }
    }

    override suspend fun searchGame(gameName: String): List<GameSearchResult> = withContext(Dispatchers.IO) {
        try {
            val url = UriComponentsBuilder.fromUri(URI.create(STEAM_SEARCH_API))
                .path("/${gameName}")
                .build(true)
                .toUriString()
            val response = restTemplate.getForObject(url, String::class.java) ?: return@withContext emptyList()

            val searchResults = objectMapper.readValue(response, Array<SteamSearchItem>::class.java)

            searchResults.take(10).map { item ->
                GameSearchResult(
                    gameId = item.appid.toString(),
                    gameName = item.name,
                    providerName = providerName
                )
            }
        } catch (e: Exception) {
            log.error(e) { "Error searching Steam for gameName=$gameName" }
            emptyList()
        }
    }

    /**
     * Get detailed game information including description, genres, developers, etc.
     */
    suspend fun getGameDetails(appId: String): SteamGameDetails? = withContext(Dispatchers.IO) {
        try {
            val url = UriComponentsBuilder.fromUri(URI.create(STEAM_STORE_API))
                .path("/appdetails")
                .queryParam("appids", appId)
                .build(true).toUriString()

            val response = restTemplate.getForObject<Map<String, Any>>(url)

            @Suppress("UNCHECKED_CAST")
            val appData = response[appId] as? Map<String, Any> ?: return@withContext null

            val success = appData["success"] as? Boolean ?: false
            if (!success) return@withContext null

            @Suppress("UNCHECKED_CAST")
            val data = appData["data"] as? Map<String, Any> ?: return@withContext null

            val name = data["name"] as? String ?: "Unknown"
            val shortDescription = data["short_description"] as? String
            val headerImage = data["header_image"] as? String
            val developers = (data["developers"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val publishers = (data["publishers"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val releaseDate = (data["release_date"] as? Map<*, *>)?.get("date") as? String
            val isFree = data["is_free"] as? Boolean ?: false

            @Suppress("UNCHECKED_CAST")
            val genres =
                (data["genres"] as? List<Map<String, Any>>)?.mapNotNull { it["description"] as? String } ?: emptyList()

            SteamGameDetails(
                appId = appId,
                name = name,
                shortDescription = shortDescription,
                headerImage = headerImage,
                developers = developers,
                publishers = publishers,
                releaseDate = releaseDate,
                genres = genres,
                isFree = isFree,
                storeUrl = "https://store.steampowered.com/app/$appId"
            )
        } catch (e: Exception) {
            log.error(e) { "Error fetching Steam details for appId=$appId" }
            null
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class SteamSearchItem(
    val appid: Int,
    val name: String,
    val icon: String? = null,
    val logo: String? = null
)


data class SteamGameDetails(
    val appId: String,
    val name: String,
    val shortDescription: String?,
    val headerImage: String?,
    val developers: List<String>,
    val publishers: List<String>,
    val releaseDate: String?,
    val genres: List<String>,
    val isFree: Boolean,
    val storeUrl: String
)