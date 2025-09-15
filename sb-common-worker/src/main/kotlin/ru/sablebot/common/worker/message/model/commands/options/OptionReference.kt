package ru.sablebot.common.worker.message.model.commands.options

import net.dv8tion.jda.api.interactions.commands.OptionMapping
import ru.sablebot.common.worker.message.model.commands.autocomplete.AutocompleteExecutor

sealed class OptionReference<T>(val name: String)

sealed class DiscordOptionReference<T>(
    name: String,
    val description: String,
    val required: Boolean,
) : OptionReference<T>(name) {
    abstract fun get(option: OptionMapping): T
}

class StringDiscordOptionReference<T>(
    name: String,
    description: String,
    required: Boolean,
    val range: IntRange?,
) : DiscordOptionReference<T>(name, description, required) {
    val choices = mutableListOf<Choice>()
    var autocompleteExecutor: AutocompleteExecutor<T>? = null

    fun autocomplete(executor: AutocompleteExecutor<T>) {
        this.autocompleteExecutor = executor
    }

    override fun get(option: OptionMapping): T = option.asString as T

    sealed class Choice {
        class RawChoice(
            val name: String,
            val value: String
        ) : Choice()
    }

    class BooleanDiscordOptionReference<T>(
        name: String,
        description: String,
        required: Boolean
    ) : DiscordOptionReference<T>(name, description, required) {
        override fun get(option: OptionMapping): T {
            return option.asBoolean as T
        }
    }
}