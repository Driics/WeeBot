package ru.sablebot.common.worker.command.model

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.internal.interactions.CommandDataImpl

interface Command {
    fun isAvailable(
        user: User,
        membere: Member,
        guild: Guild
    ): Boolean

    fun execute(
        event: SlashCommandInteractionEvent,
        context: BotContext,
        args: SlashCommandArguments
    )

    val annotation: DiscordCommand

    val key: String
        get() = annotation.key

    val permissions: Array<Permission>
        get() = annotation.permissions

    val commandOptions: List<OptionData>
        get() = ArrayList()

    val commandData: CommandData
        get() = CommandDataImpl(key, annotation.description)
            .addOptions(commandOptions)
            .setNSFW(annotation.nsfw)
}