package ru.sablebot.common.worker.message.model.commands.options

import dev.minn.jda.ktx.interactions.commands.Option
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import ru.sablebot.common.worker.message.model.commands.autocomplete.AutocompleteExecutor

sealed class OptionReference<T>(val name: String) {
    open fun toOptionData(): List<OptionData> = emptyList()
}

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

    override fun toOptionData() = listOf(
        Option<String>(name, description, required)
    )

    sealed class Choice {
        class RawChoice(
            val name: String,
            val value: String
        ) : Choice()
    }
}

class BooleanDiscordOptionReference<T>(
    name: String,
    description: String,
    required: Boolean
) : DiscordOptionReference<T>(name, description, required) {
    override fun get(option: OptionMapping): T {
        return option.asBoolean as T
    }

    override fun toOptionData(): List<OptionData> = listOf(
        OptionData(OptionType.BOOLEAN, name, description, required)
    )
}

class ChannelDiscordOptionReference<T>(
    name: String,
    description: String,
    required: Boolean
) : DiscordOptionReference<T>(name, description, required) {
    override fun get(option: OptionMapping): T = option.asChannel as T

    override fun toOptionData(): List<OptionData> = listOf(
        OptionData(OptionType.CHANNEL, name, description, required)
    )
}

class RoleDiscordOptionReference<T>(
    name: String,
    description: String,
    required: Boolean
) : DiscordOptionReference<T>(name, description, required) {
    override fun get(option: OptionMapping): T = option.asRole as T

    override fun toOptionData(): List<OptionData> = listOf(
        OptionData(OptionType.ROLE, name, description, required)
    )
}

class UserDiscordOptionReference<T>(
    name: String,
    description: String,
    required: Boolean
) : DiscordOptionReference<T>(name, description, required) {
    override fun get(option: OptionMapping): T {
        val user = option.asUser
        val member = option.asMember

        return UserAndMember(user, member) as T
    }

    override fun toOptionData(): List<OptionData> = listOf(
        OptionData(OptionType.USER, name, description, required)
    )
}

data class UserAndMember(
    val user: User,
    val member: Member?
)