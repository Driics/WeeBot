package ru.sablebot.module.feeds.service

/**
 * Service for orchestrating feed polling across all active feed subscriptions.
 * Runs on a scheduled interval to check feeds that are due for updates.
 */
interface IFeedPollingService {

    /**
     * Poll all feeds that are due for a check based on their configured intervals.
     * This method is called on a scheduled basis and delegates to platform-specific
     * feed services (Reddit, Twitch, YouTube) based on feed type.
     */
    fun pollFeeds()
}
