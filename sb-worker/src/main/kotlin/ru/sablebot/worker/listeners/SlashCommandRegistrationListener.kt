package ru.sablebot.worker.listeners

import dev.minn.jda.ktx.interactions.commands.Option
import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.build.*
import org.springframework.beans.factory.annotation.Autowired
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclaration
import ru.sablebot.common.worker.command.model.dsl.SlashCommandGroupDeclaration
import ru.sablebot.common.worker.command.service.CommandDiffer
import ru.sablebot.common.worker.command.service.CommandsHolderService
import ru.sablebot.common.worker.command.validation.CommandValidator
import ru.sablebot.common.worker.event.DiscordEvent
import ru.sablebot.common.worker.event.listeners.DiscordEventListener
import ru.sablebot.common.worker.message.model.commands.options.*


@DiscordEvent
class SlashCommandRegistrationListener @Autowired constructor(
    private val holderService: CommandsHolderService,
    private val commandValidator: CommandValidator,
    private val commandDiffer: CommandDiffer,
) : DiscordEventListener() {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun onReady(event: ReadyEvent) {
        logger.info { "=== Starting slash command registration ===" }

        try {
            // Validate DSL commands before registration
            val commandCount = holderService.dslCommands.values.size
            logger.info { "Validating $commandCount command(s)" }
            commandValidator.validate(holderService.dslCommands.values)
            logger.info { "Validation passed" }

            // Retrieve currently registered commands from Discord
            logger.info { "Retrieving registered commands from Discord" }
            val remoteCommands = event.jda.retrieveCommands().complete()
            logger.info { "Retrieved ${remoteCommands.size} registered command(s) from Discord" }

            // Prepare DSL commands for registration
            val allCommands = holderService.dslCommands.values.map { toDslJdaDeclaration(it) }

            logger.info { "Prepared ${allCommands.size} DSL command(s) for registration" }

            // Compute diff between local and remote commands
            logger.info { "Computing diff between local and remote commands" }
            val diff = commandDiffer.computeDiff(allCommands, remoteCommands)

            // Log diff summary
            logger.info {
                "Diff summary: ${diff.toAdd.size} to add, ${diff.toUpdate.size} to update, " +
                "${diff.toRemove.size} to remove, ${diff.unchanged.size} unchanged"
            }

            // Skip updateCommands() if no changes detected
            if (diff.isEmpty()) {
                logger.info { "No changes detected - skipping Discord API call" }
                logger.info { "Successfully completed global command registration (no updates needed)" }
            } else {
                event.jda.updateCommands()
                    .addCommands(allCommands)
                    .queue(
                        {
                            logger.info { "[OK] Глобальные команды успешно обновлены: ${allCommands.size} команд(ы)" }
                            logger.info { "Successfully completed global command registration" }
                        },
                        { error -> logger.error(error) { "Ошибка при обновлении глобальных команд" } }
                    )
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to register global commands" }
        }
    }

    /**
         * Преобразует DSL-описание слэш-команды в объект JDA SlashCommandData с поддержкой подкоманд и групп.
         *
         * @param declaration DSL-описание команды, включая имя, описание, опции исполнителя, подкоманды и группы.
         * @return Экземпляр `SlashCommandData`, соответствующий передённому описанию и готовый к регистрации в JDA.
         */
    private fun toDslJdaDeclaration(declaration: SlashCommandDeclaration): SlashCommandData =
        Commands.slash(declaration.name, declaration.description).apply {
            // Apply default member permissions if set
            declaration.defaultMemberPermissions?.let {
                defaultPermissions = it
            }

            isNSFW = declaration.nsfw

            if (declaration.subcommands.isNotEmpty() || declaration.subcommandGroups.isNotEmpty()) {
                if (declaration.executor != null)
                    error("Command ${declaration::class.simpleName} has a root executor, but it also has subcommand/subcommand groups!")


                addSubcommands(*declaration.subcommands.map(::toSubcommandData).toTypedArray())
                addSubcommandGroups(*declaration.subcommandGroups.map(::toSubcommandGroupData).toTypedArray())
            } else {
                val executor = declaration.executor

                if (executor != null) {
                    for (ref in executor.options.registeredOptions) {
                        try {
                            addOptions(*createOption(ref).toTypedArray())
                        } catch (e: Exception) {
                            logger.error(e) { "Something went wrong while trying to add options of $executor" }
                        }
                    }
                }
            }
        }

    /**
         * Преобразует описание подкоманды в объект SubcommandData для JDA.
         *
         * Если у подкоманды задан executor, его зарегистрированные опции добавляются в результат.
         *
         * @param subcommand Описание подкоманды (SlashCommandDeclaration).
         * @return SubcommandData с именем, описанием и, при наличии, опциями подкоманды.
         */
    private fun toSubcommandData(subcommand: SlashCommandDeclaration): SubcommandData =
        SubcommandData(subcommand.name, subcommand.description).apply {
            // Add options from the subcommand's executor
            subcommand.executor?.let { executor ->
                for (reference in executor.options.registeredOptions) {
                    try {
                        addOptions(*createOption(reference).toTypedArray())
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to register subcommand option: ${reference.name}" }
                    }
                }
            }
        }

    /**
         * Преобразует декларацию группы сабкоманд в объект SubcommandGroupData с вложенными сабкомандами.
         *
         * @return Экземпляр SubcommandGroupData, содержащий имя и описание группы и все её SubcommandData.
         */
    private fun toSubcommandGroupData(group: SlashCommandGroupDeclaration): SubcommandGroupData =
        SubcommandGroupData(group.name, group.description).apply {
            // Add all subcommands in this group
            group.subcommands.forEach { subcommand ->
                addSubcommands(toSubcommandData(subcommand))
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

                    is ChannelDiscordOptionReference -> {
                        return listOf(
                            Option<GuildChannel>(
                                interactionOption.name,
                                interactionOption.description,
                                interactionOption.required
                            )
                        )
                    }

                    is UserDiscordOptionReference -> {
                        return listOf(
                            Option<User>(
                                interactionOption.name,
                                interactionOption.description,
                                interactionOption.required
                            )
                        )
                    }

                    is RoleDiscordOptionReference -> {
                        return listOf(
                            Option<Role>(
                                interactionOption.name,
                                interactionOption.description,
                                interactionOption.required
                            )
                        )
                    }
                }
            }
        }
    }
}
