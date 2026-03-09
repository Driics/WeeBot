package ru.sablebot.module.feeds.service.reddit

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
import ru.sablebot.module.feeds.model.reddit.RedditPost
import java.awt.Color
import java.time.Instant

/**
 * Service for processing Reddit feed subscriptions.
 * Fetches new posts, checks for duplicates, and sends Discord notifications.
 */
@Service
open class RedditFeedService(
    private val redditApiClient: RedditApiClient,
    private val socialFeedRepository: SocialFeedRepository,
    private val feedNotificationRepository: FeedNotificationRepository,
    private val discordService: DiscordService,
    private val messageService: MessageService,
    private val meterRegistry: MeterRegistry
) {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val MAX_POSTS_PER_CHECK = 10
    }

    /**
     * Process a single Reddit feed subscription.
     * Fetches new posts, deduplicates, and sends notifications.
     *
     * @param feed The social feed configuration for Reddit
     */
    @Transactional
    open fun processFeed(feed: SocialFeed) {
        try {
            log.debug { "Processing Reddit feed ${feed.id} for subreddit: ${feed.targetIdentifier}" }

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

            // Fetch new posts from Reddit
            val posts = runBlocking {
                redditApiClient.getNewPosts(
                    subreddit = feed.targetIdentifier,
                    limit = MAX_POSTS_PER_CHECK
                )
            }

            if (posts.isEmpty()) {
                log.debug { "No posts fetched for feed ${feed.id}" }
                updateFeedCheckTime(feed)
                return
            }

            // Ensure feed has an ID (should always be true for saved entities)
            val feedId = feed.id ?: run {
                log.error { "Feed ${feed.targetIdentifier} has no ID, cannot process" }
                return
            }

            // Filter out posts we've already sent notifications for
            val newPosts = posts.filterNot { post ->
                feedNotificationRepository.existsByFeedIdAndExternalItemId(
                    feedId = feedId,
                    externalItemId = post.id
                )
            }

            // Process each new post
            var sentCount = 0
            for (post in newPosts.reversed()) { // Process oldest first
                try {
                    if (sendNotification(feed, post, channel)) {
                        sentCount++
                        recordNotification(feed, post)
                    }
                } catch (e: Exception) {
                    log.error(e) { "Error sending notification for post ${post.id} in feed ${feed.id}" }
                    meterRegistry.counter("sablebot.feeds.notifications.failure", "platform", "reddit").increment()
                }
            }

            // Update feed metadata
            if (newPosts.isNotEmpty()) {
                feed.lastItemId = newPosts.first().id // Most recent post ID
            }
            updateFeedCheckTime(feed)

            log.info { "Processed Reddit feed ${feed.id}: sent $sentCount new notifications" }
            meterRegistry.counter("sablebot.feeds.checks.success", "platform", "reddit").increment()
            meterRegistry.counter("sablebot.feeds.notifications.sent", "platform", "reddit").increment(sentCount.toDouble())

        } catch (e: Exception) {
            log.error(e) { "Error processing Reddit feed ${feed.id}" }
            meterRegistry.counter("sablebot.feeds.checks.failure", "platform", "reddit", "reason", "exception").increment()
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
     * Send a Discord notification for a Reddit post.
     *
     * @param feed The feed configuration
     * @param post The Reddit post
     * @param channel The Discord channel to send to
     * @return true if sent successfully
     */
    private fun sendNotification(feed: SocialFeed, post: RedditPost, channel: TextChannel): Boolean {
        val embed = buildEmbed(feed, post)

        return try {
            channel.sendMessageEmbeds(embed.build()).queue(
                { message ->
                    log.debug { "Sent Reddit notification for post ${post.id} to channel ${channel.id}" }
                },
                { error ->
                    log.error(error) { "Failed to send Reddit notification for post ${post.id}" }
                }
            )
            true
        } catch (e: Exception) {
            log.error(e) { "Error queuing Reddit notification for post ${post.id}" }
            false
        }
    }

    /**
     * Build a Discord embed for a Reddit post.
     * Uses custom embed config from the feed if available.
     *
     * @param feed The feed configuration
     * @param post The Reddit post
     * @return EmbedBuilder configured for the post
     */
    private fun buildEmbed(feed: SocialFeed, post: RedditPost): EmbedBuilder {
        val embedConfig = feed.embedConfig ?: emptyMap()

        val embedColor = embedConfig["color"]?.let { colorValue ->
            when (colorValue) {
                is String -> Color.decode(colorValue)
                is Int -> Color(colorValue)
                else -> Color.ORANGE
            }
        } ?: Color.ORANGE

        val embed = EmbedBuilder()
            .setColor(embedColor)
            .setTitle(post.title.take(256), post.fullUrl)
            .setAuthor("r/${post.subreddit}", "https://reddit.com/r/${post.subreddit}")
            .setTimestamp(Instant.ofEpochSecond(post.createdAtSeconds))
            .addField("Author", "u/${post.author}", true)
            .addField("Score", "${post.score}", true)
            .addField("Comments", "${post.numComments}", true)

        // Add selftext if it's a text post and has content
        if (post.isSelf && !post.selftext.isNullOrBlank()) {
            val description = post.selftext.take(2048)
            embed.setDescription(description)
        } else if (!post.isSelf && post.url.isNotBlank()) {
            // For link posts, add the URL if it's different from the permalink
            if (post.url != post.fullUrl) {
                embed.addField("Link", post.url, false)
            }
        }

        // Add thumbnail if available and valid
        if (!post.thumbnail.isNullOrBlank() &&
            (post.thumbnail.startsWith("http://") || post.thumbnail.startsWith("https://"))) {
            embed.setThumbnail(post.thumbnail)
        }

        // Mark NSFW content
        if (post.over18) {
            embed.addField("NSFW", "This post is marked as NSFW", false)
        }

        // Custom footer from embed config or default
        val footer = embedConfig["footer"] as? String ?: "Reddit Feed"
        embed.setFooter(footer)

        return embed
    }

    /**
     * Record that a notification was sent for a specific post.
     */
    @Transactional
    protected open fun recordNotification(feed: SocialFeed, post: RedditPost) {
        val feedId = feed.id ?: run {
            log.error { "Cannot record notification for feed without ID" }
            return
        }

        val notification = FeedNotification(
            feedId = feedId,
            externalItemId = post.id,
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
