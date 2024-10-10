package ru.driics.sablebot.common.worker.command.service

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

/**
 * Common commands interface
 *
 * @see InternalCommandsService
 */
interface CommandsService {
    /**
     * Checks if specified command valid
     *
     * @param event Message event
     * @param key   Command key
     * @return Is command key valid
     */
    fun isValidKey(event: SlashCommandInteractionEvent, key: String): Boolean

    /**
     * Sends command
     *
     * @param event       Message event
     * @return Is command was sent
     */
    fun sendCommand(
        event: SlashCommandInteractionEvent
    ): Boolean
}