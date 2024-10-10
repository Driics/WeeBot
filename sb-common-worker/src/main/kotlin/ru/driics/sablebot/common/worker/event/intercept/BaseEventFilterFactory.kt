package ru.driics.sablebot.common.worker.event.intercept

import net.dv8tion.jda.api.events.Event
import org.springframework.beans.factory.annotation.Autowired

abstract class BaseEventFilterFactory<T: Event>: EventFilterFactory<T> {
    private val chains: ThreadLocal<FilterChainImpl<T>?> = ThreadLocal()

    @Autowired
    private lateinit var filterList: List<Filter<T>>

    override fun createChain(event: T): FilterChain<T>? {
        if (filterList.isEmpty()) {
            return null // Return null if no filters available
        }

        var chain = chains.get()
        if (chain == null) {
            chain = FilterChainImpl(filterList)
            chains.set(chain)
        }
        chain.reset()
        return chain
    }
}