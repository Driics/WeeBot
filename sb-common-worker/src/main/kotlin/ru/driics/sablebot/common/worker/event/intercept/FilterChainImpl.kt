package ru.driics.sablebot.common.worker.event.intercept

import net.dv8tion.jda.api.events.Event

class FilterChainImpl<T : Event>(filters: Collection<Filter<T>>) : FilterChain<T> {
    companion object {
        private const val INCREMENT = 10
    }

    private val filters: MutableList<Filter<T>> = mutableListOf()

    /**
     * The int which is used to maintain the current position
     * in the filter chain.
     */
    private var pos: Int = 0

    init {
        filters.forEach(::addFilter)
    }

    override fun doFilter(event: T) {
        if (pos < filters.size) {
            filters[pos++].doFilter(event, this)
        }
    }

    override fun reset() {
        pos = 0
    }

    /**
     * Add a filter to the set of filters that will be executed in this chain.
     *
     * @param newFilter The Filter for the event to be executed
     */
    private fun addFilter(newFilter: Filter<T>) {
        if (!filters.contains(newFilter)) {
            filters.add(newFilter)
        }
    }
}
