package ru.sablebot.common.worker.message.model.commands.autocomplete

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import java.util.*

class AutocompleteContext(
    val locale: Locale,
    val event: CommandAutoCompleteInteractionEvent
)