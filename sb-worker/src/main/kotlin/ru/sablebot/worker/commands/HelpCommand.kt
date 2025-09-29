package ru.sablebot.worker.commands

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import ru.sablebot.common.worker.command.model.AbstractCommand
import ru.sablebot.common.worker.command.model.DiscordCommand
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.message.model.InteractionContext

@DiscordCommand(
    key = "help",
    description = "This helps you with all the commands.",
    priority = 1
)
class HelpCommand : AbstractCommand() {

    override fun execute(
        event: SlashCommandInteractionEvent,
        context: InteractionContext,
        args: SlashCommandArguments
    ) {

        val desc = "You can get detailed help information for every command\n\n"
        val commandsList = event.guild!!.retrieveCommands().complete()

        val commandsAsMention = commandsList.joinToString("\n") {
            "${it.asMention} -- ${it.description}"
        }

        context.reply(false) {
            embed {
                title = "Available Commands"
                thumbnail = event.jda.selfUser.avatarUrl
                description = desc + commandsAsMention
            }
        }
    }
}