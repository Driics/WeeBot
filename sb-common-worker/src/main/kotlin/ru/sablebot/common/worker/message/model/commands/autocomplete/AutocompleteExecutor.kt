package ru.sablebot.common.worker.message.model.commands.autocomplete

fun interface AutocompleteExecutor<T> {
    suspend fun execute(
        context: AutocompleteContext
    ): Map<String, T>
}