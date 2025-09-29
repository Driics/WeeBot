package ru.sablebot.common.worker.command.model.context

import dev.minn.jda.ktx.messages.InlineMessage
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.Interaction
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import ru.sablebot.common.worker.message.model.InteractionHook
import ru.sablebot.common.worker.message.model.InteractionMessage

abstract class UnleashedContext(
    val discordGuildLocale: DiscordLocale?,
    val discordUserLocale: DiscordLocale,
    val jda: JDA,
    val user: User,
    val memberOrNull: Member?,
    val guildOrNull: Guild?,
    val channelOrNull: MessageChannel?,
    val discordInteractionOrNull: Interaction? = null,
) {
    var alwaysEphemeral = false

    val guildId
        get() = guildOrNull?.idLong

    val guild: Guild
        get() = guildOrNull ?: error("This interaction was not sent a guild!")

    val member: Member
        get() = memberOrNull ?: error("This interaction was not sent in a guild!")

    val channel: MessageChannel
        get() = channelOrNull ?: error("This interaction was not sent in a message channel!")

    val discordInteraction: Interaction
        get() = discordInteractionOrNull ?: error("This is not executed by an interaction!")

    abstract fun deferChannelMessage(ephemeral: Boolean): InteractionHook

    fun reply(ephemeral: Boolean, content: String) = reply(ephemeral) {
        this.content = content
    }

    abstract fun reply(
        ephemeral: Boolean,
        builder: InlineMessage<MessageCreateData>.() -> Unit = {}
    ): InteractionMessage

    abstract fun reply(ephemeral: Boolean, builder: MessageCreateData): InteractionMessage
}