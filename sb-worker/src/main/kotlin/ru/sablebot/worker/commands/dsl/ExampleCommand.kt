package ru.sablebot.worker.commands.dsl

import org.springframework.stereotype.Component
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.command.model.dsl.slashCommand
import java.util.*

@Component
class ExampleCommand : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "why",
        "you don't work",
        CommandCategory.GENERAL,
        UUID.fromString("37269d60-c881-49a9-b30f-397c890081d0")
    ) {
        subcommand(
            "test",
            "demo subcommand",
            UUID.fromString("5910c81e-1715-4d21-97c9-13798ae1a013")
        ) {
            executor = ExampleCommandExecutor()
        }
    }

    inner class ExampleCommandExecutor : SlashCommandExecutor() {
        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            context.reply(true, "Works")
        }
    }
}