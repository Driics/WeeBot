package ru.sablebot.common.worker.modules.game

/**
 * Interface for price providers from different gaming platforms/stores
 */
interface PriceProvider {
    /**
     * Provider name (e.g., "Steam", "Epic Games", "GOG")
     */
    val providerName: String

    /**
     * Get price information for a game
     * @param gameId Game identifier (can be Steam App ID, GOG ID, etc.)
     * @return PriceInfo or null if not found
     */
    suspend fun getPrice(gameId: String): PriceInfo?

    /**
     * Search for a game by name
     * @param gameName Game name to search
     * @return List of matching games with their IDs
     */
    suspend fun searchGame(gameName: String): List<GameSearchResult>
}

data class PriceInfo(
    val gameId: String,
    val gameName: String,
    val currency: String,
    val originalPrice: Double?,
    val discountedPrice: Double?,
    val discountPercentage: Int?,
    val storeUrl: String,
    val providerName: String
) {
    val isFree: Boolean
        get() = (discountedPrice ?: originalPrice ?: 0.0) == 0.0

    val isOnSale: Boolean
        get() = (discountPercentage ?: 0) > 0 ||
                (discountedPrice != null && originalPrice != null && discountedPrice < originalPrice)

    val finalPrice: Double
        get() = discountedPrice ?: originalPrice ?: 0.0
}

data class GameSearchResult(
    val gameId: String,
    val gameName: String,
    val providerName: String
)