package ru.driics.sablebot.common.worker.event.intercept

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.slf4j.LoggerFactory

abstract class MemberMessageFilter: Filter<SlashCommandInteractionEvent> {
    private val log = LoggerFactory.getLogger(MemberMessageFilter::class.java)

    override fun doFilter(event: SlashCommandInteractionEvent, chain: FilterChain<SlashCommandInteractionEvent>) {
        try {
            if (!event.user.isBot && event.isFromGuild) {
                doInternal(event, chain)
                return
            }
        } catch (throwable: Throwable) {
            log.warn("Unexpected filter exception", throwable)
        }
        chain.doFilter(event)
    }

    protected abstract fun doInternal(
        event: SlashCommandInteractionEvent,
        chain: FilterChain<SlashCommandInteractionEvent>
    )
}