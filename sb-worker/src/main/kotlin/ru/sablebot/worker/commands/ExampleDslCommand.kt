package ru.sablebot.worker.commands

import org.springframework.stereotype.Component
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.command.model.dsl.slashCommand
import ru.sablebot.common.worker.message.model.commands.options.ApplicationCommandOptions
import ru.sablebot.common.worker.message.model.styled
import java.util.*

/**
 * Example DSL command demonstrating the new DSL-style command registration.
 * This command coexists with legacy @DiscordCommand annotated commands.
 */
@Component
class ExampleDslCommand : SlashCommandDeclarationWrapper {

    override fun command() = slashCommand(
        name = "example",
        description = "Example DSL command with subcommands",
        category = CommandCategory.GENERAL,
        uniqueId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
    ) {
        // Subcommand: hello
        subcommand(
            name = "hello",
            description = "Say hello with DSL command",
            uniqueId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")
        ) {
            executor = HelloExecutor()
        }
        
        // Subcommand: echo
        subcommand(
            name = "echo",
            description = "Echo a message",
            uniqueId = UUID.fromString("550e8400-e29b-41d4-a716-446655440002")
        ) {
            executor = EchoExecutor()
        }
    }

    /**
     * Simple executor that says hello
     */
    inner class HelloExecutor : SlashCommandExecutor() {
        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            context.reply(false) {
                styled(
                    "👋 Hello! This is a DSL command working alongside your legacy commands.",
                    "DSL Command Example"
                )
            }
        }
    }
    
    /**
     * Echo executor with options
     */
    inner class EchoExecutor : SlashCommandExecutor() {
        inner class Options : ApplicationCommandOptions() {
            val message = string("message", "The message to echo")
        }
        
        override val options = Options()
        
        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val messageToEcho = args[options.message]
            
            context.reply(false) {
                styled(
                    "📢 You said: $messageToEcho",
                    "Echo"
                )
            }
        }
    }
}
