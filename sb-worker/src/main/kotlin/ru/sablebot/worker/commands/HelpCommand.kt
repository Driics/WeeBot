package ru.sablebot.worker.commands

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.bouncycastle.asn1.x500.style.RFC4519Style.title
import ru.driics.sablebot.common.worker.command.model.AbstractCommand
import ru.driics.sablebot.common.worker.command.model.BotContext
import ru.driics.sablebot.common.worker.command.model.DiscordCommand

@DiscordCommand(
    key = "help",
    description = "This helps you with all the commands.",
    priority = 1
)
class HelpCommand: AbstractCommand() {

    override fun execute(event: SlashCommandInteractionEvent, context: BotContext) {
        val embedBuilder = messageService.getBaseEmbed(true).apply {
            setTitle("Available Commands:")
            setThumbnail(event.jda.selfUser.avatarUrl)
        }

        val desc = "You can get detailed help information for every command\n\n"
        val commandsList = event.guild!!.retrieveCommands().complete()

        val commandsAsMention = commandsList.joinToString("\t") { it.asMention }

        context.reply(false) {
            embed {
                title = "Available Commands"
                thumbnail = event.jda.selfUser.avatarUrl
                description = desc + commandsAsMention
            }
        }
    }
}