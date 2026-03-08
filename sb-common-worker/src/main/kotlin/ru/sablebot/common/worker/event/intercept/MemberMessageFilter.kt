package ru.sablebot.common.worker.event.intercept

import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

abstract class MemberMessageFilter: Filter<SlashCommandInteractionEvent> {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun doFilter(event: SlashCommandInteractionEvent, chain: FilterChain<SlashCommandInteractionEvent>) {
        try {
            if (!event.user.isBot && event.isFromGuild) {
                doInternal(event, chain)
                return
            }
        } catch (e: Exception) {
            log.warn("Unexpected filter exception", e)
        }
        chain.doFilter(event)
    }

    protected abstract fun doInternal(
        event: SlashCommandInteractionEvent,
        chain: FilterChain<SlashCommandInteractionEvent>
    )
}