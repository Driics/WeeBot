package ru.sablebot.worker.listeners

import dev.minn.jda.ktx.interactions.commands.Option
import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.build.*
import org.springframework.beans.factory.annotation.Autowired
import ru.sablebot.common.worker.command.model.Command
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclaration
import ru.sablebot.common.worker.command.model.dsl.SlashCommandGroupDeclaration
import ru.sablebot.common.worker.command.service.CommandsHolderService
import ru.sablebot.common.worker.event.DiscordEvent
import ru.sablebot.common.worker.event.listeners.DiscordEventListener
import ru.sablebot.common.worker.message.model.commands.options.BooleanDiscordOptionReference
import ru.sablebot.common.worker.message.model.commands.options.DiscordOptionReference
import ru.sablebot.common.worker.message.model.commands.options.OptionReference
import ru.sablebot.common.worker.message.model.commands.options.StringDiscordOptionReference


@DiscordEvent
class SlashCommandRegistrationListener @Autowired constructor(
    private val holderService: CommandsHolderService,
) : DiscordEventListener() {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun onReady(event: ReadyEvent) {
        logger.info { "=== Starting slash command registration ===" }
        
        try {
            // Prepare legacy and DSL commands
            val legacyCommands = holderService.publicCommands.values.map { toJdaDeclaration(it) }
            val dslCommands = holderService.dslCommands.values.map { toDslJdaDeclaration(it) }
            
            // Deduplicate: DSL commands take priority over legacy with the same name
            val dslCommandNames = dslCommands.map { it.name }.toSet()
            val uniqueLegacyCommands = legacyCommands.filterNot { it.name in dslCommandNames }
            val allCommands = uniqueLegacyCommands + dslCommands
            
            val duplicatesRemoved = legacyCommands.size - uniqueLegacyCommands.size
            
            logger.info { 
                "Подготовлено ${allCommands.size} команд(ы): " +
                "${uniqueLegacyCommands.size} legacy, ${dslCommands.size} DSL" +
                (if (duplicatesRemoved > 0) " ($duplicatesRemoved дубликат(ов) удалено, приоритет DSL)" else "")
            }

            event.jda.updateCommands()
                .addCommands(allCommands)
                .queue(
                    {
                        logger.info { "✓ Глобальные команды успешно обновлены: ${allCommands.size} команд(ы)" }
                        logger.info { "✓ Global command registration complete" }
                    },
                    { error -> logger.error(error) { "Ошибка при обновлении глобальных команд" } }
                )
        } catch (e: Exception) {
            logger.error(e) { "Failed to register global commands" }
        }

        logger.info { "=== Slash command registration complete ===" }
    }

    /**
         * Преобразует устаревший объект команды в JDA-описание slash-команды для регистрации.
         *
         * Преобразование включает имя, описание, флаг NSFW, опции и значения прав по умолчанию; поддержка сабкоманд пока не реализована.
         * При ошибке создания отдельной опции исключение логируется, а проблемная опция пропускается.
         *
         * @param command Устаревшая модель команды, содержащая аннотацию, зарегистрированные опции и требуемые права участников.
         * @return `SlashCommandData` — объект JDA, готовый для добавления/обновления в списке slash-комманд.
         */
    private fun toJdaDeclaration(command: Command): SlashCommandData =
        Commands.slash(command.annotation.key, command.annotation.description).apply {
            isNSFW = command.annotation.nsfw

            for (reference in command.commandOptions.registeredOptions) {
                try {
                    addOptions(*createOption(reference).toTypedArray())
                } catch (e: Exception) {
                    logger.error(e) { "Failed to register command: ${reference.name}" }
                }
            }

            defaultPermissions = DefaultMemberPermissions.enabledFor(*command.annotation.memberRequiredPermissions)
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

                /*declaration.subcommands.forEach { subcommand ->
                    subcommand(subcommand.name, subcommand.description) {
                        val executor = subcommand.executor ?: error("Subcommand does not have a executor!")

                        for (ref in executor.options.registeredOptions) {
                            try {
                                addOptions(*createOption(ref).toTypedArray())
                            } catch (e: Exception) {
                                logger.error(e) { "Something went wrong while trying to add options of $executor" }
                            }
                        }
                    }
                }

                declaration.subcommandGroups.forEach { group ->
                    group(group.name, group.description) {
                        group.subcommands.forEach { subcommand ->
                            subcommand(subcommand.name, subcommand.description) {
                                val executor = subcommand.executor ?: error("Subcommand does not have a executor!")

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
                }*/

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
                }
            }
        }
    }
}