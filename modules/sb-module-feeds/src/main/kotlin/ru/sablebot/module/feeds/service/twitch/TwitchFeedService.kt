package ru.sablebot.module.feeds.service.twitch

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
import ru.sablebot.module.feeds.model.twitch.TwitchStream
import java.awt.Color
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Service for processing Twitch feed subscriptions.
 * Fetches stream status, detects go-live/go-offline events, and sends Discord notifications.
 */
@Service
open class TwitchFeedService(
    private val twitchApiClient: TwitchApiClient,
    private val socialFeedRepository: SocialFeedRepository,
    private val feedNotificationRepository: FeedNotificationRepository,
    private val discordService: DiscordService,
    private val messageService: MessageService,
    private val meterRegistry: MeterRegistry
) {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val NOTIFICATION_TYPE_LIVE = "live"
        private const val NOTIFICATION_TYPE_OFFLINE = "offline"
    }

    /**
     * Process a single Twitch feed subscription.
     * Checks stream status and sends go-live or go-offline notifications.
     *
     * @param feed The social feed configuration for Twitch
     */
    @Transactional
    open fun processFeed(feed: SocialFeed) {
        try {
            log.debug { "Processing Twitch feed ${feed.id} for streamer: ${feed.targetIdentifier}" }

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

            // Fetch stream status from Twitch
            val stream = runBlocking {
                twitchApiClient.getStream(feed.targetIdentifier)
            }

            // Ensure feed has an ID (should always be true for saved entities)
            val feedId = feed.id ?: run {
                log.error { "Feed ${feed.targetIdentifier} has no ID, cannot process" }
                return
            }

            // Determine if streamer was previously live by checking for a recent "live" notification
            val wasLive = feed.lastItemId != null && feedNotificationRepository.existsByFeedIdAndExternalItemId(
                feedId = feedId,
                externalItemId = "${feed.lastItemId}_$NOTIFICATION_TYPE_LIVE"
            )

            val isNowLive = stream != null && stream.isLive

            when {
                // Stream went live (was offline, now live)
                !wasLive && isNowLive -> {
                    log.info { "Stream ${feed.targetIdentifier} went live: ${stream.title}" }
                    if (sendLiveNotification(feed, stream, channel)) {
                        recordNotification(feed, stream, NOTIFICATION_TYPE_LIVE)
                        feed.lastItemId = stream.id
                        meterRegistry.counter("sablebot.feeds.notifications.sent", "platform", "twitch", "type", "live").increment()
                    }
                }
                // Stream went offline (was live, now offline)
                wasLive && !isNowLive -> {
                    log.info { "Stream ${feed.targetIdentifier} went offline" }
                    // Only send offline notification if enabled in embed config
                    val sendOfflineNotification = (feed.embedConfig?.get("sendOfflineNotification") as? Boolean) ?: false
                    if (sendOfflineNotification) {
                        if (sendOfflineNotification(feed, channel)) {
                            recordNotification(feed, feed.lastItemId!!, NOTIFICATION_TYPE_OFFLINE)
                            meterRegistry.counter("sablebot.feeds.notifications.sent", "platform", "twitch", "type", "offline").increment()
                        }
                    }
                    feed.lastItemId = null // Clear last item ID when stream goes offline
                }
                // Stream is still live (was live, still live) - update metadata if stream ID changed
                wasLive && isNowLive && stream.id != feed.lastItemId -> {
                    log.debug { "Stream ${feed.targetIdentifier} is still live, updating stream ID" }
                    feed.lastItemId = stream.id
                }
                // No change (was offline, still offline)
                else -> {
                    log.debug { "No status change for stream ${feed.targetIdentifier}" }
                }
            }

            updateFeedCheckTime(feed)

            log.debug { "Processed Twitch feed ${feed.id}: wasLive=$wasLive, isNowLive=$isNowLive" }
            meterRegistry.counter("sablebot.feeds.checks.success", "platform", "twitch").increment()

        } catch (e: Exception) {
            log.error(e) { "Error processing Twitch feed ${feed.id}" }
            meterRegistry.counter("sablebot.feeds.checks.failure", "platform", "twitch", "reason", "exception").increment()
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
     * Send a Discord notification for a stream going live.
     *
     * @param feed The feed configuration
     * @param stream The Twitch stream
     * @param channel The Discord channel to send to
     * @return true if sent successfully
     */
    private fun sendLiveNotification(feed: SocialFeed, stream: TwitchStream, channel: TextChannel): Boolean {
        val embed = buildLiveEmbed(feed, stream)

        return try {
            channel.sendMessageEmbeds(embed.build()).queue(
                { message ->
                    log.debug { "Sent Twitch live notification for ${stream.userLogin} to channel ${channel.id}" }
                },
                { error ->
                    log.error(error) { "Failed to send Twitch live notification for ${stream.userLogin}" }
                }
            )
            true
        } catch (e: Exception) {
            log.error(e) { "Error queuing Twitch live notification for ${stream.userLogin}" }
            false
        }
    }

    /**
     * Send a Discord notification for a stream going offline.
     *
     * @param feed The feed configuration
     * @param channel The Discord channel to send to
     * @return true if sent successfully
     */
    private fun sendOfflineNotification(feed: SocialFeed, channel: TextChannel): Boolean {
        val embed = buildOfflineEmbed(feed)

        return try {
            channel.sendMessageEmbeds(embed.build()).queue(
                { message ->
                    log.debug { "Sent Twitch offline notification for ${feed.targetIdentifier} to channel ${channel.id}" }
                },
                { error ->
                    log.error(error) { "Failed to send Twitch offline notification for ${feed.targetIdentifier}" }
                }
            )
            true
        } catch (e: Exception) {
            log.error(e) { "Error queuing Twitch offline notification for ${feed.targetIdentifier}" }
            false
        }
    }

    /**
     * Build a Discord embed for a stream going live.
     * Uses custom embed config from the feed if available.
     *
     * @param feed The feed configuration
     * @param stream The Twitch stream
     * @return EmbedBuilder configured for the live notification
     */
    private fun buildLiveEmbed(feed: SocialFeed, stream: TwitchStream): EmbedBuilder {
        val embedConfig = feed.embedConfig ?: emptyMap()

        val embedColor = embedConfig["color"]?.let { colorValue ->
            when (colorValue) {
                is String -> Color.decode(colorValue)
                is Int -> Color(colorValue)
                else -> Color(decodeColor("#9146FF")) // Twitch purple
            }
        } ?: Color(decodeColor("#9146FF")) // Twitch purple

        val embed = EmbedBuilder()
            .setColor(embedColor)
            .setTitle("${stream.userName} is now live!", stream.channelUrl)
            .setDescription(stream.title.take(2048))
            .setAuthor(stream.userName, stream.channelUrl)
            .setTimestamp(Instant.now())

        // Add game/category field if available
        if (stream.gameName.isNotBlank()) {
            embed.addField("Game", stream.gameName, true)
        }

        // Add viewer count
        embed.addField("Viewers", "${stream.viewerCount}", true)

        // Add language if configured to show
        val showLanguage = (embedConfig["showLanguage"] as? Boolean) ?: false
        if (showLanguage && stream.language.isNotBlank()) {
            embed.addField("Language", stream.language, true)
        }

        // Add thumbnail if configured to show (default: true)
        val showThumbnail = (embedConfig["showThumbnail"] as? Boolean) ?: true
        if (showThumbnail && stream.thumbnailUrl.isNotBlank()) {
            // Use larger thumbnail for better quality
            embed.setImage(stream.getThumbnailUrl(width = 640, height = 360))
        }

        // Mark mature content
        if (stream.isMature) {
            embed.addField("Mature Content", "This stream is marked as mature", false)
        }

        // Custom footer from embed config or default
        val footer = embedConfig["footer"] as? String ?: "Twitch Feed"
        embed.setFooter(footer)

        return embed
    }

    /**
     * Build a Discord embed for a stream going offline.
     * Uses custom embed config from the feed if available.
     *
     * @param feed The feed configuration
     * @return EmbedBuilder configured for the offline notification
     */
    private fun buildOfflineEmbed(feed: SocialFeed): EmbedBuilder {
        val embedConfig = feed.embedConfig ?: emptyMap()

        val embedColor = embedConfig["offlineColor"]?.let { colorValue ->
            when (colorValue) {
                is String -> Color.decode(colorValue)
                is Int -> Color(colorValue)
                else -> Color.GRAY
            }
        } ?: Color.GRAY

        val embed = EmbedBuilder()
            .setColor(embedColor)
            .setTitle("${feed.targetIdentifier} is now offline", "https://twitch.tv/${feed.targetIdentifier}")
            .setAuthor(feed.targetIdentifier, "https://twitch.tv/${feed.targetIdentifier}")
            .setTimestamp(Instant.now())

        // Custom footer from embed config or default
        val footer = embedConfig["footer"] as? String ?: "Twitch Feed"
        embed.setFooter(footer)

        return embed
    }

    /**
     * Record that a notification was sent for a specific stream status change.
     */
    @Transactional
    protected open fun recordNotification(feed: SocialFeed, stream: TwitchStream, notificationType: String) {
        val feedId = feed.id ?: run {
            log.error { "Cannot record notification for feed without ID" }
            return
        }

        val notification = FeedNotification(
            feedId = feedId,
            externalItemId = "${stream.id}_$notificationType",
            sentAt = Instant.now(),
            guildId = feed.guildId
        )
        feedNotificationRepository.save(notification)
    }

    /**
     * Record that an offline notification was sent.
     */
    @Transactional
    protected open fun recordNotification(feed: SocialFeed, streamId: String, notificationType: String) {
        val feedId = feed.id ?: run {
            log.error { "Cannot record notification for feed without ID" }
            return
        }

        val notification = FeedNotification(
            feedId = feedId,
            externalItemId = "${streamId}_$notificationType",
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

    /**
     * Helper to decode hex color strings to Color int value.
     */
    private fun decodeColor(hexColor: String): Int {
        return Integer.decode(hexColor)
    }
}
