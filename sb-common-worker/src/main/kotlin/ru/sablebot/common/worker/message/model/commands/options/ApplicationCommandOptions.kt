package ru.sablebot.common.worker.message.model.commands.options

open class ApplicationCommandOptions {
    companion object {
        fun noOptions(): ApplicationCommandOptions = ApplicationCommandOptions()
    }

    val registeredOptions = mutableListOf<OptionReference<*>>()

    fun string(
        name: String,
        description: String,
        range: IntRange? = null,
        builder: StringDiscordOptionReference<String>.() -> Unit = {}
    ) = run {
        require(registeredOptions.none {
            it.name.equals(
                name,
                ignoreCase = true
            )
        }) { "Option '$name' is already registered" }

        if (range != null) {
            require(range.first >= 0 && range.first <= range.last) { "Invalid range: $range" }
        }

        StringDiscordOptionReference<String>(name, description, true, range)
    }.apply(builder).also { registeredOptions.add(it) }

    fun optionalString(
        name: String,
        description: String,
        range: IntRange? = null,
        builder: StringDiscordOptionReference<String>.() -> Unit = {}
    ) = run {
        require(registeredOptions.none {
            it.name.equals(
                name,
                ignoreCase = true
            )
        }) { "Option '$name' is already registered" }

        if (range != null) {
            require(range.first >= 0 && range.first <= range.last) { "Invalid range: $range" }
        }

        StringDiscordOptionReference<String>(name, description, false, range)
    }.apply(builder).also { registeredOptions.add(it) }

    fun boolean(name: String, description: String, builder: BooleanDiscordOptionReference<Boolean>.() -> (Unit) = {}) =
        BooleanDiscordOptionReference<Boolean>(name, description, true)
            .apply(builder)
            .also { registeredOptions.add(it) }

    fun optionalBoolean(
        name: String,
        description: String,
        builder: BooleanDiscordOptionReference<Boolean?>.() -> (Unit) = {}
    ) = BooleanDiscordOptionReference<Boolean?>(name, description, false)
        .apply(builder)
        .also { registeredOptions.add(it) }
}