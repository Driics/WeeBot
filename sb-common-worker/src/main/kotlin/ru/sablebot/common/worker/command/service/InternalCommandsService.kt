package ru.sablebot.common.worker.command.service

import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import ru.sablebot.common.worker.command.model.Command

interface InternalCommandsService : CommandsService, CommandHandler {
    companion object {
        const val COMMANDS_EXECUTED_COUNTER = "sablebot.commands.executed"
        const val COMMANDS_DURATION_TIMER = "sablebot.commands.duration"
        const val COMMANDS_ERRORS_COUNTER = "sablebot.commands.errors"
    }

    fun isApplicable(
        command: Command,
        user: User,
        member: Member,
        channel: TextChannel
    ): Boolean
}