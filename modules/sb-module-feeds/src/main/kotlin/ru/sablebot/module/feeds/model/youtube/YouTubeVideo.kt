package ru.sablebot.module.feeds.model.youtube

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents a YouTube video from the Data API v3 response.
 * Maps to the "snippet" object within each item in the YouTube API response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class YouTubeVideo(
    @JsonProperty("id")
    val id: String,

    @JsonProperty("title")
    val title: String,

    @JsonProperty("description")
    val description: String? = null,

    @JsonProperty("channelId")
    val channelId: String,

    @JsonProperty("channelTitle")
    val channelTitle: String,

    @JsonProperty("publishedAt")
    val publishedAt: String,

    @JsonProperty("thumbnailUrl")
    val thumbnailUrl: String? = null,

    @JsonProperty("liveBroadcastContent")
    val liveBroadcastContent: String = "none", // "live", "upcoming", "none"

    @JsonProperty("viewCount")
    val viewCount: Long? = null,

    @JsonProperty("likeCount")
    val likeCount: Long? = null,

    @JsonProperty("commentCount")
    val commentCount: Long? = null,

    @JsonProperty("duration")
    val duration: String? = null // ISO 8601 format (e.g., "PT15M33S")
) {
    /**
     * Check if the video is a live stream
     */
    val isLive: Boolean
        get() = liveBroadcastContent == "live"

    /**
     * Check if the video is an upcoming premiere/stream
     */
    val isUpcoming: Boolean
        get() = liveBroadcastContent == "upcoming"

    /**
     * Full YouTube video URL
     */
    val videoUrl: String
        get() = "https://www.youtube.com/watch?v=$id"

    /**
     * Full YouTube channel URL
     */
    val channelUrl: String
        get() = "https://www.youtube.com/channel/$channelId"
}

/**
 * Response wrapper for YouTube Data API v3 playlistItems endpoint
 */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class YouTubePlaylistItemsResponse(
    @JsonProperty("items")
    val items: List<YouTubePlaylistItem>,

    @JsonProperty("nextPageToken")
    val nextPageToken: String? = null,

    @JsonProperty("pageInfo")
    val pageInfo: YouTubePageInfo? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class YouTubePlaylistItem(
    @JsonProperty("snippet")
    val snippet: YouTubeSnippet,

    @JsonProperty("contentDetails")
    val contentDetails: YouTubeContentDetails? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class YouTubeSnippet(
    @JsonProperty("publishedAt")
    val publishedAt: String,

    @JsonProperty("channelId")
    val channelId: String,

    @JsonProperty("title")
    val title: String,

    @JsonProperty("description")
    val description: String? = null,

    @JsonProperty("channelTitle")
    val channelTitle: String,

    @JsonProperty("thumbnails")
    val thumbnails: YouTubeThumbnails? = null,

    @JsonProperty("resourceId")
    val resourceId: YouTubeResourceId? = null,

    @JsonProperty("liveBroadcastContent")
    val liveBroadcastContent: String? = "none"
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class YouTubeContentDetails(
    @JsonProperty("videoId")
    val videoId: String,

    @JsonProperty("videoPublishedAt")
    val videoPublishedAt: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class YouTubeResourceId(
    @JsonProperty("kind")
    val kind: String,

    @JsonProperty("videoId")
    val videoId: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class YouTubeThumbnails(
    @JsonProperty("default")
    val default: YouTubeThumbnail? = null,

    @JsonProperty("medium")
    val medium: YouTubeThumbnail? = null,

    @JsonProperty("high")
    val high: YouTubeThumbnail? = null,

    @JsonProperty("standard")
    val standard: YouTubeThumbnail? = null,

    @JsonProperty("maxres")
    val maxres: YouTubeThumbnail? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class YouTubeThumbnail(
    @JsonProperty("url")
    val url: String,

    @JsonProperty("width")
    val width: Int? = null,

    @JsonProperty("height")
    val height: Int? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class YouTubePageInfo(
    @JsonProperty("totalResults")
    val totalResults: Int,

    @JsonProperty("resultsPerPage")
    val resultsPerPage: Int
)

/**
 * Response wrapper for YouTube Data API v3 channels endpoint
 */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class YouTubeChannelsResponse(
    @JsonProperty("items")
    val items: List<YouTubeChannel>
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class YouTubeChannel(
    @JsonProperty("id")
    val id: String,

    @JsonProperty("contentDetails")
    val contentDetails: YouTubeChannelContentDetails
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class YouTubeChannelContentDetails(
    @JsonProperty("relatedPlaylists")
    val relatedPlaylists: YouTubeRelatedPlaylists
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class YouTubeRelatedPlaylists(
    @JsonProperty("uploads")
    val uploads: String
)

/**
 * Response wrapper for YouTube Data API v3 videos endpoint
 */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class YouTubeVideosResponse(
    @JsonProperty("items")
    val items: List<YouTubeVideoDetails>
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class YouTubeVideoDetails(
    @JsonProperty("id")
    val id: String,

    @JsonProperty("snippet")
    val snippet: YouTubeSnippet,

    @JsonProperty("statistics")
    val statistics: YouTubeStatistics? = null,

    @JsonProperty("contentDetails")
    val contentDetails: YouTubeVideoContentDetails? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class YouTubeStatistics(
    @JsonProperty("viewCount")
    val viewCount: String? = null,

    @JsonProperty("likeCount")
    val likeCount: String? = null,

    @JsonProperty("commentCount")
    val commentCount: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class YouTubeVideoContentDetails(
    @JsonProperty("duration")
    val duration: String
)
