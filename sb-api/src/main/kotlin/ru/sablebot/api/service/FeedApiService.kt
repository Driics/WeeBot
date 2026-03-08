package ru.sablebot.api.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.sablebot.api.dto.feed.*
import ru.sablebot.common.persistence.entity.SocialFeed
import ru.sablebot.common.persistence.repository.SocialFeedRepository

@Service
class FeedApiService(
    private val socialFeedRepository: SocialFeedRepository,
    @Value("\${sablebot.feeds.max-feeds-per-guild:25}")
    private val maxFeedsPerGuild: Int
) {
    private val logger = KotlinLogging.logger {}

    @Transactional(readOnly = true)
    fun getFeeds(guildId: Long): FeedListResponse {
        val feeds = socialFeedRepository.findAllByGuildId(guildId)
        val total = socialFeedRepository.countByGuildId(guildId)

        return FeedListResponse(
            feeds = feeds.map { it.toResponse() },
            total = total,
            page = 0,
            size = feeds.size
        )
    }

    @Transactional(readOnly = true)
    fun getFeed(feedId: Long): FeedResponse? {
        return socialFeedRepository.findByIdOrNull(feedId)?.toResponse()
    }

    @Transactional
    fun createFeed(guildId: Long, request: CreateFeedRequest): FeedResponse {
        val currentCount = socialFeedRepository.countByGuildId(guildId)
        if (currentCount >= maxFeedsPerGuild) {
            throw IllegalStateException("Maximum number of feeds ($maxFeedsPerGuild) reached for guild $guildId")
        }

        val feed = SocialFeed(guildId).apply {
            feedType = request.feedType
            targetIdentifier = request.targetIdentifier
            targetChannelId = request.targetChannelId.toLong()
            checkIntervalMinutes = request.checkIntervalMinutes
            embedConfig = request.embedConfig
            enabled = request.enabled
        }

        val saved = socialFeedRepository.save(feed)
        logger.info { "Created feed ${saved.id} for guild $guildId: ${saved.feedType} -> ${saved.targetIdentifier}" }

        return saved.toResponse()
    }

    @Transactional
    fun updateFeed(feedId: Long, request: UpdateFeedRequest): FeedResponse {
        val feed = socialFeedRepository.findByIdOrNull(feedId)
            ?: throw IllegalArgumentException("Feed not found: $feedId")

        request.targetIdentifier?.let { feed.targetIdentifier = it }
        request.targetChannelId?.let { feed.targetChannelId = it.toLong() }
        request.checkIntervalMinutes?.let { feed.checkIntervalMinutes = it }
        request.embedConfig?.let { feed.embedConfig = it }
        request.enabled?.let { feed.enabled = it }

        val saved = socialFeedRepository.save(feed)
        logger.info { "Updated feed $feedId for guild ${feed.guildId}" }

        return saved.toResponse()
    }

    @Transactional
    fun deleteFeed(feedId: Long) {
        val feed = socialFeedRepository.findByIdOrNull(feedId)
            ?: throw IllegalArgumentException("Feed not found: $feedId")

        socialFeedRepository.delete(feed)
        logger.info { "Deleted feed $feedId from guild ${feed.guildId}" }
    }

    @Transactional
    fun testFeed(feedId: Long): Map<String, Any> {
        val feed = socialFeedRepository.findByIdOrNull(feedId)
            ?: throw IllegalArgumentException("Feed not found: $feedId")

        logger.info { "Test feed request for feed $feedId (${feed.feedType})" }

        // Return feed configuration for validation
        // The actual test notification would be triggered by the polling service
        return mapOf(
            "feedId" to feedId,
            "feedType" to feed.feedType.name,
            "targetIdentifier" to feed.targetIdentifier,
            "targetChannelId" to feed.targetChannelId.toString(),
            "enabled" to feed.enabled,
            "status" to "Feed configuration is valid. Polling service will check on next cycle."
        )
    }

    private fun SocialFeed.toResponse() = FeedResponse(
        id = id ?: 0L,
        guildId = guildId.toString(),
        feedType = feedType,
        targetIdentifier = targetIdentifier,
        targetChannelId = targetChannelId.toString(),
        checkIntervalMinutes = checkIntervalMinutes,
        embedConfig = embedConfig,
        enabled = enabled,
        lastCheckTime = lastCheckTime,
        lastItemId = lastItemId,
        createdAt = lastCheckTime ?: java.time.Instant.EPOCH
    )
}
