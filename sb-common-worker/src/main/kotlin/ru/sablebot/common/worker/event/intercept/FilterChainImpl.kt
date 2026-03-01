package ru.sablebot.common.worker.event.intercept

import net.dv8tion.jda.api.events.Event

class FilterChainImpl<T : Event>(
    private val filters: List<Filter<T>>,
    private val pos: Int = 0
) : FilterChain<T> {
    override fun doFilter(event: T) {
        if (pos < filters.size) {
            filters[pos].doFilter(event, FilterChainImpl(filters, pos + 1))
        }
    }
}
