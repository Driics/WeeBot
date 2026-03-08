package ru.sablebot.api.dto.feed

data class FeedListResponse(
    val feeds: List<FeedResponse>,
    val total: Long,
    val page: Int,
    val size: Int
)
