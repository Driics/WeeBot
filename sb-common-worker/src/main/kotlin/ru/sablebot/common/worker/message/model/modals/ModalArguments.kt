package ru.sablebot.common.worker.message.model.modals

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import ru.sablebot.common.worker.message.model.modals.options.DiscordModalOptionReference
import ru.sablebot.common.worker.message.model.modals.options.ModalOptionReference

class ModalArguments(private val event: ModalInteractionEvent) {
    operator fun <T> get(argument: ModalOptionReference<T>): T {
        return when (argument) {
            is DiscordModalOptionReference -> {
                val option = event.getValue(argument.name)

                if (option == null) {
                    if (argument.required)
                        throw RuntimeException("Missing argument ${argument.name}!")

                    return null as T
                }

                return argument.get(option)
            }
        }
    }
}

