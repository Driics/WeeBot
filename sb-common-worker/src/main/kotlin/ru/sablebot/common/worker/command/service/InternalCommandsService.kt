package ru.sablebot.common.worker.command.service

interface InternalCommandsService : CommandsService, CommandHandler {
    companion object {
        const val COMMANDS_EXECUTED_COUNTER = "sablebot.commands.executed"
        const val COMMANDS_DURATION_TIMER = "sablebot.commands.duration"
        const val COMMANDS_ERRORS_COUNTER = "sablebot.commands.errors"
    }
}