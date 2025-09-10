package ru.sablebot.common.worker.event.intercept

import net.dv8tion.jda.api.events.Event

interface EventFilterFactory<T: Event> {
    fun createChain(event: T): FilterChain<T>?

    fun getType(): Class<T>
}