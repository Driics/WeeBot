package ru.sablebot.common.worker.event.intercept

import net.dv8tion.jda.api.events.Event
import org.springframework.beans.factory.annotation.Autowired

abstract class BaseEventFilterFactory<T: Event>: EventFilterFactory<T> {
    @Autowired
    private lateinit var filterList: List<Filter<T>>

    override fun createChain(event: T): FilterChain<T>? {
        if (filterList.isEmpty()) {
            return null
        }
        return FilterChainImpl(filterList)
    }
}
