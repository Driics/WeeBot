package ru.sablebot.common.worker.message.model.commands.options

open class ApplicationCommandOptions {
    companion object {
        val NO_OPTIONS = object : ApplicationCommandOptions() {}
    }

    val registeredOptions = mutableListOf<OptionReference<*>>()

    fun string(
        name: String,
        description: String,
        range: IntRange? = null,
        builder: StringDiscordOptionReference<String>.() -> Unit = {}
    ) = StringDiscordOptionReference<String>(name, description, true, range)
        .apply(builder)
        .also { registeredOptions.add(it) }

    fun optionalString(
        name: String,
        description: String,
        range: IntRange? = null,
        builder: StringDiscordOptionReference<String>.() -> Unit = {}
    ) = StringDiscordOptionReference<String>(name, description, false, range)
        .apply(builder)
        .also { registeredOptions.add(it) }
}