package ru.driics.sablebot.common.worker.event.intercept

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.springframework.stereotype.Component

@Component
class SlashCommandFilterFactory : BaseEventFilterFactory<SlashCommandInteractionEvent>() {
    override fun getType(): Class<SlashCommandInteractionEvent> = SlashCommandInteractionEvent::class.java
}