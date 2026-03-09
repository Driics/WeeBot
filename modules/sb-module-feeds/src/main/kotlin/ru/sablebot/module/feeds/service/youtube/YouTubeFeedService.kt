package ru.sablebot.module.feeds.service.youtube

import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.sablebot.common.persistence.entity.FeedNotification
import ru.sablebot.common.persistence.entity.SocialFeed
import ru.sablebot.common.persistence.repository.FeedNotificationRepository
import ru.sablebot.common.persistence.repository.SocialFeedRepository
import ru.sablebot.common.worker.message.service.MessageService
import ru.sablebot.common.worker.shared.service.DiscordService
import ru.sablebot.module.feeds.model.youtube.YouTubeVideo
import java.awt.Color
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Service for processing YouTube feed subscriptions.
 * Fetches new videos, checks for duplicates, and sends Discord notifications.
 */
@Service
open class YouTubeFeedService(
    private val youtubeApiClient: YouTubeApiClient,
    private val socialFeedRepository: SocialFeedRepository,
    private val feedNotificationRepository: FeedNotificationRepository,
    private val discordService: DiscordService,
    private val messageService: MessageService,
    private val meterRegistry: MeterRegistry
) {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val MAX_VIDEOS_PER_CHECK = 10
    }

    /**
     * Process a single YouTube feed subscription.
     * Fetches new videos, deduplicates, and sends notifications.
     *
     * @param feed The social feed configuration for YouTube
     */
    @Transactional
    open fun processFeed(feed: SocialFeed) {
        try {
            log.debug { "Processing YouTube feed ${feed.id} for channel: ${feed.targetIdentifier}" }

            if (!discordService.isConnected(feed.guildId)) {
                log.warn { "Discord not connected for guild ${feed.guildId}, skipping feed ${feed.id}" }
                return
            }

            val guild = discordService.getGuildById(feed.guildId)
            if (guild == null) {
                log.warn { "Guild ${feed.guildId} not found, skipping feed ${feed.id}" }
                return
            }

            val channel = guild.getTextChannelById(feed.targetChannelId)
            if (channel == null) {
                log.warn { "Channel ${feed.targetChannelId} not found in guild ${feed.guildId}, skipping feed ${feed.id}" }
                return
            }

            // Check bot permissions
            if (!hasRequiredPermissions(channel)) {
                log.warn { "Missing permissions in channel ${channel.id} for feed ${feed.id}" }
                meterRegistry.counter("sablebot.feeds.checks.failure", "reason", "permissions").increment()
                updateFeedCheckTime(feed)
                return
            }

            // Fetch new videos from YouTube
            val videos = runBlocking {
                youtubeApiClient.getRecentVideos(
                    channelId = feed.targetIdentifier,
                    maxResults = MAX_VIDEOS_PER_CHECK
                )
            }

            if (videos.isEmpty()) {
                log.debug { "No videos fetched for feed ${feed.id}" }
                updateFeedCheckTime(feed)
                return
            }

            // Ensure feed has an ID (should always be true for saved entities)
            val feedId = feed.id ?: run {
                log.error { "Feed ${feed.targetIdentifier} has no ID, cannot process" }
                return
            }

            // Filter out videos we've already sent notifications for
            val newVideos = videos.filterNot { video ->
                feedNotificationRepository.existsByFeedIdAndExternalItemId(
                    feedId = feedId,
                    externalItemId = video.id
                )
            }

            // Process each new video
            var sentCount = 0
            for (video in newVideos.reversed()) { // Process oldest first
                try {
                    if (sendNotification(feed, video, channel)) {
                        sentCount++
                        recordNotification(feed, video)
                    }
                } catch (e: Exception) {
                    log.error(e) { "Error sending notification for video ${video.id} in feed ${feed.id}" }
                    meterRegistry.counter("sablebot.feeds.notifications.failure", "platform", "youtube").increment()
                }
            }

            // Update feed metadata
            if (newVideos.isNotEmpty()) {
                feed.lastItemId = newVideos.first().id // Most recent video ID
            }
            updateFeedCheckTime(feed)

            log.info { "Processed YouTube feed ${feed.id}: sent $sentCount new notifications" }
            meterRegistry.counter("sablebot.feeds.checks.success", "platform", "youtube").increment()
            meterRegistry.counter("sablebot.feeds.notifications.sent", "platform", "youtube").increment(sentCount.toDouble())

        } catch (e: Exception) {
            log.error(e) { "Error processing YouTube feed ${feed.id}" }
            meterRegistry.counter("sablebot.feeds.checks.failure", "platform", "youtube", "reason", "exception").increment()
            updateFeedCheckTime(feed)
        }
    }

    /**
     * Check if the bot has required permissions in the target channel.
     */
    private fun hasRequiredPermissions(channel: TextChannel): Boolean {
        val self = channel.guild.selfMember
        return self.hasPermission(
            channel,
            Permission.VIEW_CHANNEL,
            Permission.MESSAGE_SEND,
            Permission.MESSAGE_EMBED_LINKS
        )
    }

    /**
     * Send a Discord notification for a YouTube video.
     *
     * @param feed The feed configuration
     * @param video The YouTube video
     * @param channel The Discord channel to send to
     * @return true if sent successfully
     */
    private fun sendNotification(feed: SocialFeed, video: YouTubeVideo, channel: TextChannel): Boolean {
        val embed = buildEmbed(feed, video)

        return try {
            channel.sendMessageEmbeds(embed.build()).queue(
                { message ->
                    log.debug { "Sent YouTube notification for video ${video.id} to channel ${channel.id}" }
                },
                { error ->
                    log.error(error) { "Failed to send YouTube notification for video ${video.id}" }
                }
            )
            true
        } catch (e: Exception) {
            log.error(e) { "Error queuing YouTube notification for video ${video.id}" }
            false
        }
    }

    /**
     * Build a Discord embed for a YouTube video.
     * Uses custom embed config from the feed if available.
     *
     * @param feed The feed configuration
     * @param video The YouTube video
     * @return EmbedBuilder configured for the video
     */
    private fun buildEmbed(feed: SocialFeed, video: YouTubeVideo): EmbedBuilder {
        val embedConfig = feed.embedConfig ?: emptyMap()

        val embedColor = embedConfig["color"]?.let { colorValue ->
            when (colorValue) {
                is String -> Color.decode(colorValue)
                is Int -> Color(colorValue)
                else -> Color.RED
            }
        } ?: Color.RED

        val embed = EmbedBuilder()
            .setColor(embedColor)
            .setTitle(video.title.take(256), video.videoUrl)
            .setAuthor(video.channelTitle, video.channelUrl)

        // Parse and set timestamp
        try {
            val publishedAt = Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(video.publishedAt))
            embed.setTimestamp(publishedAt)
        } catch (e: Exception) {
            log.warn(e) { "Failed to parse publishedAt timestamp: ${video.publishedAt}" }
        }

        // Add description if available
        if (!video.description.isNullOrBlank()) {
            val description = video.description.take(2048)
            embed.setDescription(description)
        }

        // Add statistics if available
        video.viewCount?.let {
            embed.addField("Views", formatNumber(it), true)
        }
        video.likeCount?.let {
            embed.addField("Likes", formatNumber(it), true)
        }
        video.commentCount?.let {
            embed.addField("Comments", formatNumber(it), true)
        }

        // Add duration if available
        video.duration?.let {
            embed.addField("Duration", formatDuration(it), true)
        }

        // Add live status indicators
        when {
            video.isLive -> {
                embed.addField("🔴 Status", "LIVE NOW", false)
            }
            video.isUpcoming -> {
                embed.addField("📅 Status", "Upcoming Premiere", false)
            }
        }

        // Add thumbnail if available
        if (!video.thumbnailUrl.isNullOrBlank()) {
            embed.setImage(video.thumbnailUrl)
        }

        // Custom footer from embed config or default
        val footer = embedConfig["footer"] as? String ?: "YouTube Feed"
        embed.setFooter(footer)

        return embed
    }

    /**
     * Format a number with thousand separators (e.g., 1,234,567)
     */
    private fun formatNumber(number: Long): String {
        return String.format("%,d", number)
    }

    /**
     * Format ISO 8601 duration to human-readable format.
     * Example: PT15M33S -> 15:33, PT1H2M10S -> 1:02:10
     *
     * @param duration ISO 8601 duration string (e.g., "PT15M33S")
     * @return Formatted duration string
     */
    private fun formatDuration(duration: String): String {
        return try {
            // Parse PT15M33S format
            val pattern = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?")
            val match = pattern.matchEntire(duration) ?: return duration

            val hours = match.groupValues[1].toIntOrNull() ?: 0
            val minutes = match.groupValues[2].toIntOrNull() ?: 0
            val seconds = match.groupValues[3].toIntOrNull() ?: 0

            when {
                hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
                minutes > 0 -> String.format("%d:%02d", minutes, seconds)
                else -> String.format("0:%02d", seconds)
            }
        } catch (e: Exception) {
            log.warn(e) { "Failed to parse duration: $duration" }
            duration
        }
    }

    /**
     * Record that a notification was sent for a specific video.
     */
    @Transactional
    protected open fun recordNotification(feed: SocialFeed, video: YouTubeVideo) {
        val feedId = feed.id ?: run {
            log.error { "Cannot record notification for feed without ID" }
            return
        }

        val notification = FeedNotification(
            feedId = feedId,
            externalItemId = video.id,
            sentAt = Instant.now(),
            guildId = feed.guildId
        )
        feedNotificationRepository.save(notification)
    }

    /**
     * Update the feed's last check time.
     */
    @Transactional
    protected open fun updateFeedCheckTime(feed: SocialFeed) {
        feed.lastCheckTime = Instant.now()
        socialFeedRepository.save(feed)
    }
}
