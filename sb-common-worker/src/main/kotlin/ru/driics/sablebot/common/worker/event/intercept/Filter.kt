package ru.driics.sablebot.common.worker.event.intercept

import net.dv8tion.jda.api.events.Event

/**
 * A filter is an object that performs filtering tasks Discord events.
 * <br/>
 * All implementation must have [Order][org.springframework.core.annotation.Order] annotation to handle chain order
 *
 * @see Filter.PRE_FILTER
 * @see Filter.HANDLE_FILTER
 * @see Filter.POST_FILTER
 */
interface Filter<T: Event> {
    companion object {
        /**
         * Pre-stage for various permission checks, common filters, etc
         */
        const val PRE_FILTER = 0

        /**
         * Common handling stage
         */
        const val HANDLE_FILTER = 1000

        /**
         * Post-stage
         */
        const val POST_FILTER = 2000
    }

    fun doFilter(event: T, chain: FilterChain<T>)
}