package ru.sablebot.worker.listeners

import dev.minn.jda.ktx.interactions.commands.Option
import dev.minn.jda.ktx.interactions.commands.subcommand
import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData
import org.springframework.beans.factory.annotation.Autowired
import ru.sablebot.common.worker.command.model.Command
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclaration
import ru.sablebot.common.worker.command.model.dsl.SlashCommandGroupDeclaration
import ru.sablebot.common.worker.command.service.CommandsHolderService
import ru.sablebot.common.worker.command.service.CommandsHolderServiceImpl
import ru.sablebot.common.worker.event.DiscordEvent
import ru.sablebot.common.worker.event.listeners.DiscordEventListener
import ru.sablebot.common.worker.message.model.commands.options.BooleanDiscordOptionReference
import ru.sablebot.common.worker.message.model.commands.options.DiscordOptionReference
import ru.sablebot.common.worker.message.model.commands.options.OptionReference
import ru.sablebot.common.worker.message.model.commands.options.StringDiscordOptionReference
import net.dv8tion.jda.api.interactions.commands.Command as CommandJDA


@DiscordEvent
class SlashCommandRegistrationListener @Autowired constructor(
    private val holderService: CommandsHolderService,
) : DiscordEventListener() {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun onReady(event: ReadyEvent) {
        try {
            val globalRegistered = updateCommands(0L, event.jda) { commands ->
                event.jda.updateCommands()
                    .addCommands(commands)
                    .complete()
            }
            logger.info { "${globalRegistered.size} global commands registered" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to register global commands" }
        }

        event.jda.guilds.forEach { guild ->
            try {
                val registeredCommands = updateCommands(guild.idLong, event.jda) { commands ->
                    guild.updateCommands()
                        .addCommands(commands)
                        .complete()
                }
                logger.info { "${registeredCommands.size} commands registered for guild ${guild.idLong}" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to register commands for guild ${guild.idLong}" }
            }
        }
    }

    private fun updateCommands(
        guildId: Long,
        jda: JDA,
        action: (List<CommandData>) -> List<CommandJDA>
    ): List<CommandJDA> {
        logger.info { "Starting command update for ${if (guildId == 0L) "global scope" else "guild ID: $guildId"}" }

        // Collect legacy commands
        val legacyCommands = holderService.publicCommands.values.map { toJdaDeclaration(it) }

        // Collect DSL commands
        val dslCommands = if (holderService is CommandsHolderServiceImpl) {
            holderService.dslCommands.map { wrapper ->
                val declaration = wrapper.command().build()
                toDslJdaDeclaration(declaration)
            }
        } else {
            emptyList()
        }

        // Combine both command types
        val publicCommands = legacyCommands + dslCommands

        // Log command type breakdown once instead of per command
        logger.info { "Legacy commands: ${legacyCommands.size}, DSL commands: ${dslCommands.size}" }
        CommandJDA.Type.entries
            .filter { it != CommandJDA.Type.UNKNOWN }
            .forEach { type ->
                logger.info { "Type ${type.name}: ${publicCommands.count { it.type == type }} commands" }
            }

        val existingCommands = fetchExistingCommands(guildId, jda) ?: return emptyList()

        val needsUpdate = publicCommands.size != existingCommands.size ||
                publicCommands.any { appCommand ->
                    existingCommands.none {
                        val appCommandData = SlashCommandData.fromData(appCommand.toData())

                        it.name == appCommandData.name &&
                                it.type == appCommandData.type &&
                                it.options == appCommandData.options &&
                                it.defaultPermissions == appCommand.defaultPermissions
                    }
                }

        return if (needsUpdate) {
            logger.info { "Command mismatch detected. Updating commands for ${if (guildId == 0L) "global" else "guild ID: $guildId"}" }
            val updatedCommands = action(publicCommands)
            logger.info { "Successfully updated ${updatedCommands.size} commands for ${if (guildId == 0L) "global" else "guild ID: $guildId"}" }
            updatedCommands
        } else {
            logger.info { "No update needed. Commands are already up-to-date for ${if (guildId == 0L) "global" else "guild ID: $guildId"}" }
            existingCommands
        }
    }

    private fun toJdaDeclaration(command: Command): SlashCommandData =
        Commands.slash(command.annotation.key, command.annotation.description).apply {
            isNSFW = command.annotation.nsfw

            /*
            TODO
            if(command.subcommands.isNotEmpty()) {
                command.subcommands.forEach { subcommand ->
                    slashcomand()
                }
            }*/

            for (reference in command.commandOptions.registeredOptions) {
                try {
                    addOptions(*createOption(reference).toTypedArray())
                } catch (e: Exception) {
                    logger.error(e) { "Failed to register command: ${reference.name}" }
                }
            }

            defaultPermissions = DefaultMemberPermissions.enabledFor(*command.annotation.memberRequiredPermissions)
        }

    // Helper function to retrieve commands for either global or specific guild
    private fun fetchExistingCommands(guildId: Long, jda: JDA): List<CommandJDA>? {
        return if (guildId == 0L) {
            logger.info { "Retrieving global commands..." }
            jda.retrieveCommands().complete()
        } else {
            val guild = jda.getGuildById(guildId) ?: run {
                logger.warn { "Guild with ID $guildId not found" }
                return null
            }
            logger.info { "Retrieving commands for guild ID: $guildId" }
            guild.retrieveCommands().complete()
        }
    }

    private fun createOption(interactionOption: OptionReference<*>): List<OptionData> {
        when (interactionOption) {
            is DiscordOptionReference -> {
                when (interactionOption) {
                    is StringDiscordOptionReference -> return listOf(
                        Option<String>(
                            interactionOption.name,
                            interactionOption.description,
                            interactionOption.required
                        ).apply {
                            if (interactionOption.autocompleteExecutor != null) {
                                isAutoComplete = true
                            }
                        }
                    )

                    is BooleanDiscordOptionReference<*> -> return listOf(
                        Option<Boolean>(
                            interactionOption.name,
                            interactionOption.description,
                            interactionOption.required
                        )
                    )
                }
            }
        }
    }

    private fun toDslJdaDeclaration(declaration: SlashCommandDeclaration): SlashCommandData {
        val slashCommand = Commands.slash(declaration.name, declaration.description)

        // Set permissions
        declaration.defaultMemberPermissions?.let {
            slashCommand.defaultPermissions = it
        }

        // Handle subcommands and subcommand groups
        if (declaration.subcommands.isNotEmpty() || declaration.subcommandGroups.isNotEmpty()) {
            // Add subcommand groups
            declaration.subcommandGroups.forEach { group ->
                val subcommandGroup = SubcommandGroupData(group.name, group.description)
                group.subcommands.forEach { subcommand ->
                    val subcommandData = SubcommandData(subcommand.name, subcommand.description)
                    // Add options from executor if present
                    subcommand.executor?.options?.registeredOptions?.forEach { option ->
                        subcommandData.addOptions(*createOption(option).toTypedArray())
                    }
                    subcommandGroup.addSubcommands(subcommandData)
                }
                slashCommand.addSubcommandGroups(subcommandGroup)
            }

            // Add standalone subcommands
            declaration.subcommands.forEach { subcommand ->
                val subcommandData = SubcommandData(subcommand.name, subcommand.description)
                // Add options from executor if present
                subcommand.executor?.options?.registeredOptions?.forEach { option ->
                    subcommandData.addOptions(*createOption(option).toTypedArray())
                }
                slashCommand.addSubcommands(subcommandData)
            }
        } else {
            // Add options for the top-level command if it has an executor
            declaration.executor?.options?.registeredOptions?.forEach { option ->
                slashCommand.addOptions(*createOption(option).toTypedArray())
            }
        }

        return slashCommand
    }
}