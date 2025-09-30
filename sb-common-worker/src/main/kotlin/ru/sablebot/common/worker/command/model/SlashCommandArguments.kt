package ru.sablebot.common.worker.command.model

import ru.sablebot.common.worker.message.model.commands.options.OptionReference

class SlashCommandArguments(
    private val event: SlashCommandArgumentsSource
) {
    operator fun <T> get(argument: OptionReference<T>) = event[argument]
}