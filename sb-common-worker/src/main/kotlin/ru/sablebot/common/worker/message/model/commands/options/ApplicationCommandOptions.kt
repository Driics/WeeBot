package ru.sablebot.common.worker.message.model.commands.options

import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel

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

    fun channel(
        name: String,
        description: String
    ) = ChannelDiscordOptionReference<GuildChannel>(
        name,
        description,
        true
    ).also { registeredOptions.add(it) }

    fun optionalChannel(
        name: String,
        description: String
    ) = ChannelDiscordOptionReference<GuildChannel?>(
        name,
        description,
        false
    ).also { registeredOptions.add(it) }

    fun user(
        name: String,
        description: String
    ) = UserDiscordOptionReference<UserAndMember>(
        name,
        description,
        true
    )
        .also { registeredOptions.add(it) }

    fun optionalUser(
        name: String,
        description: String
    ) = UserDiscordOptionReference<UserAndMember?>(name, description, false)
        .also { registeredOptions.add(it) }

    fun role(
        name: String,
        description: String
    ) = RoleDiscordOptionReference<Role>(name, description, true)
        .also { registeredOptions.add(it) }

    fun optionalRole(
        name: String,
        description: String
    ) = RoleDiscordOptionReference<Role?>(name, description, false)
        .also { registeredOptions.add(it) }
}