package ru.sablebot.module.feeds.service.impl

import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.sablebot.common.persistence.entity.FeedType
import ru.sablebot.common.persistence.entity.SocialFeed
import ru.sablebot.common.persistence.repository.SocialFeedRepository
import ru.sablebot.module.feeds.config.FeedProperties
import ru.sablebot.module.feeds.service.IFeedPollingService
import ru.sablebot.module.feeds.service.reddit.RedditFeedService
import ru.sablebot.module.feeds.service.twitch.TwitchFeedService
import ru.sablebot.module.feeds.service.youtube.YouTubeFeedService
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

@Service
open class FeedPollingServiceImpl(
    private val socialFeedRepository: SocialFeedRepository,
    private val redditFeedService: RedditFeedService,
    private val twitchFeedService: TwitchFeedService,
    private val youtubeFeedService: YouTubeFeedService,
    private val feedProperties: FeedProperties,
    private val meterRegistry: MeterRegistry
) : IFeedPollingService {
    companion object {
        private val log = KotlinLogging.logger { }
    }

    /**
     * Poll all feeds that are due for a check.
     * Runs every minute and checks feeds based on their configured intervals.
     */
    @Scheduled(fixedDelay = 60000)
    @Transactional
    override fun pollFeeds() {
        try {
            log.debug { "Starting feed polling cycle" }
            val startTime = System.currentTimeMillis()

            // Query for feeds that are due for checking
            // A feed is due if it's enabled and either:
            // 1. Has never been checked (lastCheckTime is null)
            // 2. lastCheckTime + checkIntervalMinutes < now
            val now = Instant.now()
            val feedsDueForCheck = socialFeedRepository.findFeedsDueForCheck(now)

            if (feedsDueForCheck.isEmpty()) {
                log.debug { "No feeds due for check" }
                return
            }

            log.info { "Found ${feedsDueForCheck.size} feeds due for check" }

            // Group feeds by type for better logging
            val feedsByType = feedsDueForCheck.groupBy { it.feedType }
            feedsByType.forEach { (type, feeds) ->
                log.debug { "  ${type.name}: ${feeds.size} feeds" }
            }

            // Process each feed
            val processedCount = AtomicInteger(0)
            val successCount = AtomicInteger(0)
            val failureCount = AtomicInteger(0)

            for (feed in feedsDueForCheck) {
                try {
                    processSingleFeed(feed)
                    processedCount.incrementAndGet()
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    log.error(e) { "Error processing feed ${feed.id} (${feed.feedType})" }
                    failureCount.incrementAndGet()
                    meterRegistry.counter(
                        "sablebot.feeds.checks.failure",
                        "platform", feed.feedType.name.lowercase(),
                        "reason", "exception"
                    ).increment()
                }
            }

            val duration = System.currentTimeMillis() - startTime
            log.info {
                "Feed polling cycle completed: processed=$processedCount, " +
                        "success=$successCount, failure=$failureCount, duration=${duration}ms"
            }

            // Update active feed count gauge
            updateActiveFeedGauges()

            // Record overall polling metrics
            meterRegistry.counter("sablebot.feeds.polling.cycles.total").increment()
            meterRegistry.timer("sablebot.feeds.polling.duration").record(duration, java.util.concurrent.TimeUnit.MILLISECONDS)

        } catch (e: Exception) {
            log.error(e) { "Error during feed polling cycle" }
            meterRegistry.counter("sablebot.feeds.polling.cycles.failure").increment()
        }
    }

    /**
     * Process a single feed by delegating to the appropriate platform-specific service.
     */
    private fun processSingleFeed(feed: SocialFeed) {
        log.debug { "Processing feed ${feed.id} (${feed.feedType}) for guild ${feed.guildId}" }

        // Check if feed should be skipped based on time since last check
        if (!isFeedDueForCheck(feed)) {
            log.debug { "Feed ${feed.id} not yet due for check, skipping" }
            return
        }

        // Check if platform is enabled
        val platformEnabled = when (feed.feedType) {
            FeedType.REDDIT -> feedProperties.reddit.enabled
            FeedType.TWITCH -> feedProperties.twitch.enabled
            FeedType.YOUTUBE -> feedProperties.youtube.enabled
        }

        if (!platformEnabled) {
            log.warn { "Platform ${feed.feedType} is disabled, skipping feed ${feed.id}" }
            return
        }

        // Delegate to platform-specific service
        when (feed.feedType) {
            FeedType.REDDIT -> redditFeedService.processFeed(feed)
            FeedType.TWITCH -> twitchFeedService.processFeed(feed)
            FeedType.YOUTUBE -> youtubeFeedService.processFeed(feed)
        }
    }

    /**
     * Check if a feed is due for checking based on its last check time and configured interval.
     */
    private fun isFeedDueForCheck(feed: SocialFeed): Boolean {
        val lastCheckTime = feed.lastCheckTime ?: return true // Never checked before
        val now = Instant.now()
        val intervalSeconds = feed.checkIntervalMinutes * 60L
        val timeSinceLastCheck = now.epochSecond - lastCheckTime.epochSecond
        return timeSinceLastCheck >= intervalSeconds
    }

    /**
     * Update Prometheus gauges for active feed counts by platform.
     */
    private fun updateActiveFeedGauges() {
        val activeFeedsByType = socialFeedRepository
            .findByFeedTypeAndEnabled(FeedType.REDDIT)
            .size
        meterRegistry.gauge("sablebot.feeds.active.count", listOf(
            io.micrometer.core.instrument.Tag.of("platform", "reddit")
        ), activeFeedsByType)

        val activeTwitchFeeds = socialFeedRepository
            .findByFeedTypeAndEnabled(FeedType.TWITCH)
            .size
        meterRegistry.gauge("sablebot.feeds.active.count", listOf(
            io.micrometer.core.instrument.Tag.of("platform", "twitch")
        ), activeTwitchFeeds)

        val activeYouTubeFeeds = socialFeedRepository
            .findByFeedTypeAndEnabled(FeedType.YOUTUBE)
            .size
        meterRegistry.gauge("sablebot.feeds.active.count", listOf(
            io.micrometer.core.instrument.Tag.of("platform", "youtube")
        ), activeYouTubeFeeds)

        val totalActiveFeeds = activeFeedsByType + activeTwitchFeeds + activeYouTubeFeeds
        meterRegistry.gauge("sablebot.feeds.active.count", listOf(
            io.micrometer.core.instrument.Tag.of("platform", "all")
        ), totalActiveFeeds)
    }
}
