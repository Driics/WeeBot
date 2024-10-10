package ru.driics.sablebot.common.worker.command.service

import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import ru.driics.sablebot.common.worker.command.model.Command

interface InternalCommandsService : CommandsService, CommandHandler {
    companion object {
        const val EXECUTIONS_METER = "commands.executions.rate"
        const val EXECUTIONS_COUNTER = "commands.executions.persist"
    }

    fun isApplicable(
        command: Command,
        user: User,
        member: Member,
        channel: TextChannel
    ): Boolean
}