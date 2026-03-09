package ru.sablebot.worker.commands

import dev.minn.jda.ktx.interactions.components.option
import org.springframework.stereotype.Component
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.command.model.dsl.slashCommand
import ru.sablebot.common.worker.command.service.CommandsHolderService
import ru.sablebot.common.worker.command.util.CommandUuidGenerator
import ru.sablebot.common.worker.message.model.InteractivityManager
import ru.sablebot.common.worker.message.model.commands.options.ApplicationCommandOptions

@Component
class HelpCommand(
    private val holderService: CommandsHolderService,
    private val interactivityManager: InteractivityManager,
    private val uuidGenerator: CommandUuidGenerator
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "help",
        "This helps you with all the commands.",
        CommandCategory.GENERAL,
        uuidGenerator.generate(CommandCategory.GENERAL, "help")
    ) {
        executor = HelpExecutor()
    }

    inner class HelpExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions()

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            // Defer reply first since retrieveCommands() is a blocking REST call
            context.deferChannelMessage(true)

            // Retrieve all registered commands from Discord
            val registeredCommands = context.event.jda.retrieveCommands().complete()

            // Create a map of command name to Discord command for quick lookup
            val commandMap = registeredCommands.associateBy { it.name }

            // Group legacy commands by category
            val legacyByCategory = holderService.publicCommands.values
                .filter { !it.annotation.hidden }
                .groupBy { it.annotation.group }

            // Group DSL commands by category
            val dslByCategory = holderService.dslCommands.values
                .groupBy { it.category }

            // Get all unique categories (using enum order)
            val allCategories = CommandCategory.entries.filter { category ->
                legacyByCategory.containsKey(category) || dslByCategory.containsKey(category)
            }

            context.reply(false) {
                embed {
                    title = "📚 Available Commands"
                    thumbnail = context.event.jda.selfUser.avatarUrl
                    description = "Here are all available commands, grouped by category:"

                    // Iterate through each category in enum order
                    for (category in allCategories) {
                        val commandsList = mutableListOf<String>()

                        // Add legacy commands from this category
                        legacyByCategory[category]?.forEach { command ->
                            commandMap[command.annotation.key]?.let { discordCmd ->
                                commandsList.add(discordCmd.asMention)
                            }
                        }

                        // Add DSL commands from this category
                        dslByCategory[category]?.forEach { dslCommand ->
                            val baseCommand = commandMap[dslCommand.name] ?: return@forEach

                            if (dslCommand.subcommands.isNotEmpty() || dslCommand.subcommandGroups.isNotEmpty()) {
                                // Add each subcommand individually with manual mention formatting
                                dslCommand.subcommands.forEach { subcommand ->
                                    val subcommandMention = "</${dslCommand.name} ${subcommand.name}:${baseCommand.idLong}>"
                                    commandsList.add(subcommandMention)
                                }

                                // Add subcommands from groups
                                dslCommand.subcommandGroups.forEach { group ->
                                    group.subcommands.forEach { subcommand ->
                                        val subcommandMention =
                                            "</${dslCommand.name} ${group.name} ${subcommand.name}:${baseCommand.idLong}>"
                                        commandsList.add(subcommandMention)
                                    }
                                }
                            } else {
                                // No subcommands, show root command
                                commandsList.add(baseCommand.asMention)
                            }
                        }

                        // Add field(s) for this category, splitting if value exceeds 1024 chars
                        if (commandsList.isNotEmpty()) {
                            val categoryEmoji = category.emoji?.formatted ?: ""
                            val categoryLabel = "$categoryEmoji ${category.title}"

                            val chunks = mutableListOf<String>()
                            val current = StringBuilder()
                            for (cmd in commandsList) {
                                val separator = if (current.isEmpty()) "" else " "
                                if (current.length + separator.length + cmd.length > 1024) {
                                    chunks.add(current.toString())
                                    current.clear()
                                }
                                if (current.isNotEmpty()) current.append(" ")
                                current.append(cmd)
                            }
                            if (current.isNotEmpty()) chunks.add(current.toString())

                            chunks.forEachIndexed { index, chunk ->
                                field {
                                    name = if (index == 0) categoryLabel else "$categoryLabel (cont.)"
                                    value = chunk
                                    inline = false
                                }
                            }
                        }
                    }

                    footer {
                        name = "Use /help to see this message again"
                    }
                }
                actionRow(
                    interactivityManager.stringSelectMenu(true, {
                        placeholder = "Select a category to filter commands"

                        // Add options for each category
                        allCategories.forEach { category ->
                            option(category.title, category.name, null, category.emoji)
                        }
                    }, { callbackContext, selected ->
                        val selectedCategories = selected.mapNotNull { categoryName ->
                            try {
                                CommandCategory.valueOf(categoryName)
                            } catch (_: IllegalArgumentException) {
                                null
                            }
                        }

                        callbackContext.reply(true) {
                            embed {
                                title = "🔍 Filtered Commands"
                                thumbnail = callbackContext.event.jda.selfUser.avatarUrl

                                if (selectedCategories.isEmpty()) {
                                    description = "No valid categories selected."
                                } else {
                                    description =
                                        "Commands in selected categor${if (selectedCategories.size > 1) "ies" else "y"}:"

                                    selectedCategories.forEach { category ->
                                        val commandsList =
                                            buildCommandsList(category, commandMap, legacyByCategory, dslByCategory)

                                        if (commandsList.isNotEmpty()) {
                                            val categoryEmoji = category.emoji?.formatted ?: ""
                                            val categoryLabel = "$categoryEmoji ${category.title}"

                                            val chunks = mutableListOf<String>()
                                            val current = StringBuilder()
                                            for (cmd in commandsList) {
                                                val separator = if (current.isEmpty()) "" else "\n"
                                                if (current.length + separator.length + cmd.length > 1024) {
                                                    chunks.add(current.toString())
                                                    current.clear()
                                                }
                                                if (current.isNotEmpty()) current.append("\n")
                                                current.append(cmd)
                                            }
                                            if (current.isNotEmpty()) chunks.add(current.toString())

                                            chunks.forEachIndexed { index, chunk ->
                                                field {
                                                    name = if (index == 0) categoryLabel else "$categoryLabel (cont.)"
                                                    value = chunk
                                                    inline = false
                                                }
                                            }
                                        }
                                    }
                                }

                                footer {
                                    name = "Use /help to see all commands again"
                                }
                            }
                        }
                    })
                )
            }
        }
    }

    private fun buildCommandsList(
        category: CommandCategory,
        commandMap: Map<String, net.dv8tion.jda.api.interactions.commands.Command>,
        legacyByCategory: Map<CommandCategory, List<ru.sablebot.common.worker.command.model.Command>>,
        dslByCategory: Map<CommandCategory, List<ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclaration>>
    ): List<String> {
        val commandsList = mutableListOf<String>()

        legacyByCategory[category]?.forEach { command ->
            commandMap[command.annotation.key]?.let { discordCmd ->
                commandsList.add("${discordCmd.asMention} — ${command.annotation.description}")
            }
        }

        dslByCategory[category]?.forEach { dslCommand ->
            val baseCommand = commandMap[dslCommand.name] ?: return@forEach

            if (dslCommand.subcommands.isNotEmpty() || dslCommand.subcommandGroups.isNotEmpty()) {
                dslCommand.subcommands.forEach { subcommand ->
                    val subcommandMention = "</${dslCommand.name} ${subcommand.name}:${baseCommand.idLong}>"
                    commandsList.add("$subcommandMention — ${subcommand.description}")
                }

                dslCommand.subcommandGroups.forEach { group ->
                    group.subcommands.forEach { subcommand ->
                        val subcommandMention =
                            "</${dslCommand.name} ${group.name} ${subcommand.name}:${baseCommand.idLong}>"
                        commandsList.add("$subcommandMention — ${subcommand.description}")
                    }
                }
            } else {
                commandsList.add("${baseCommand.asMention} — ${dslCommand.description}")
            }
        }

        return commandsList
    }
}