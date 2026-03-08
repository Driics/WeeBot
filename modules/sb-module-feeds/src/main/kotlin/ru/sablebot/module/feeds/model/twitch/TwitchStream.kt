package ru.sablebot.module.feeds.model.twitch

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents a Twitch stream from the Helix API response.
 * Maps to the "data" array elements in the Twitch streams endpoint response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TwitchStream(
    @JsonProperty("id")
    val id: String,

    @JsonProperty("user_id")
    val userId: String,

    @JsonProperty("user_login")
    val userLogin: String,

    @JsonProperty("user_name")
    val userName: String,

    @JsonProperty("game_id")
    val gameId: String,

    @JsonProperty("game_name")
    val gameName: String,

    @JsonProperty("type")
    val type: String, // "live" or "" for offline

    @JsonProperty("title")
    val title: String,

    @JsonProperty("viewer_count")
    val viewerCount: Int = 0,

    @JsonProperty("started_at")
    val startedAt: String,

    @JsonProperty("language")
    val language: String,

    @JsonProperty("thumbnail_url")
    val thumbnailUrl: String,

    @JsonProperty("tag_ids")
    val tagIds: List<String>? = null,

    @JsonProperty("is_mature")
    val isMature: Boolean = false
) {
    /**
     * Check if the stream is currently live
     */
    val isLive: Boolean
        get() = type == "live"

    /**
     * Get thumbnail URL with specific dimensions
     *
     * @param width Desired width in pixels
     * @param height Desired height in pixels
     * @return Formatted thumbnail URL
     */
    fun getThumbnailUrl(width: Int = 320, height: Int = 180): String {
        return thumbnailUrl
            .replace("{width}", width.toString())
            .replace("{height}", height.toString())
    }

    /**
     * Full Twitch channel URL
     */
    val channelUrl: String
        get() = "https://twitch.tv/$userLogin"
}

/**
 * Response wrapper for Twitch API streams endpoint
 */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class TwitchStreamsResponse(
    @JsonProperty("data")
    val data: List<TwitchStream>,

    @JsonProperty("pagination")
    val pagination: TwitchPagination? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class TwitchPagination(
    @JsonProperty("cursor")
    val cursor: String?
)

/**
 * OAuth token response from Twitch
 */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class TwitchOAuthTokenResponse(
    @JsonProperty("access_token")
    val accessToken: String,

    @JsonProperty("expires_in")
    val expiresIn: Int,

    @JsonProperty("token_type")
    val tokenType: String
)
