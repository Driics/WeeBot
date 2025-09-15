package ru.sablebot.common.worker.command.service

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent


/**
 * A command holder interface. All implementation must have [Order][org.springframework.core.annotation.Order] annotation to handle orders
 */
interface CommandHandler {
    /**
     * Handle slash command
     *
     * @param[event] Slash command event
     * @return True is this event was handled
     */
    fun handleSlashCommand(event: SlashCommandInteractionEvent): Boolean
}