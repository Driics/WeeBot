package ru.sablebot.common.worker.command.model

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.message.model.commands.options.ApplicationCommandOptions

interface Command {
    fun isAvailable(
        user: User,
        membere: Member,
        guild: Guild
    ): Boolean

    fun execute(
        event: SlashCommandInteractionEvent,
        context: ApplicationCommandContext,
        args: SlashCommandArguments
    )

    val annotation: DiscordCommand

    val key: String
        get() = annotation.key

    val permissions: Array<Permission>
        get() = annotation.permissions

    val subcommands: List<Command>
        get() = emptyList()

    val commandOptions: ApplicationCommandOptions
        get() = ApplicationCommandOptions.noOptions()
}