package ru.sablebot.common.worker.command.model.dsl

import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.message.model.commands.options.ApplicationCommandOptions

abstract class SlashCommandExecutor {
    open val options: ApplicationCommandOptions = ApplicationCommandOptions.noOptions()

    abstract suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments)
}