package ru.sablebot.module.feeds.model.reddit

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents a Reddit post from the API response.
 * Maps to the "data" object within each child in the Reddit JSON response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class RedditPost(
    @JsonProperty("id")
    val id: String,

    @JsonProperty("title")
    val title: String,

    @JsonProperty("author")
    val author: String,

    @JsonProperty("subreddit")
    val subreddit: String,

    @JsonProperty("permalink")
    val permalink: String,

    @JsonProperty("url")
    val url: String,

    @JsonProperty("selftext")
    val selftext: String? = null,

    @JsonProperty("created_utc")
    val createdUtc: Long,

    @JsonProperty("score")
    val score: Int = 0,

    @JsonProperty("num_comments")
    val numComments: Int = 0,

    @JsonProperty("thumbnail")
    val thumbnail: String? = null,

    @JsonProperty("is_self")
    val isSelf: Boolean = false,

    @JsonProperty("is_video")
    val isVideo: Boolean = false,

    @JsonProperty("over_18")
    val over18: Boolean = false,

    @JsonProperty("stickied")
    val stickied: Boolean = false
) {
    /**
     * Full Reddit URL to the post
     */
    val fullUrl: String
        get() = "https://www.reddit.com$permalink"

    /**
     * Human-readable timestamp
     */
    val createdAtSeconds: Long
        get() = createdUtc
}

/**
 * Internal wrapper for Reddit API response structure
 */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class RedditListingResponse(
    @JsonProperty("kind")
    val kind: String,

    @JsonProperty("data")
    val data: RedditListingData
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class RedditListingData(
    @JsonProperty("children")
    val children: List<RedditChild>,

    @JsonProperty("after")
    val after: String?,

    @JsonProperty("before")
    val before: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class RedditChild(
    @JsonProperty("kind")
    val kind: String,

    @JsonProperty("data")
    val data: RedditPost
)
