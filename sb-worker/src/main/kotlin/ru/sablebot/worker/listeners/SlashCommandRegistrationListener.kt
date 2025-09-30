package ru.sablebot.worker.listeners

import dev.minn.jda.ktx.interactions.commands.Option
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

    /**
     * Формирует полный набор команд (наследуемых и DSL), сравнивает его с текущими командами и при необходимости выполняет обновление.
     *
     * Собирает декларации команд из holderService, сравнивает их с командами, уже зарегистрированными в JDA для указанного контекста,
     * и при обнаружении расхождений вызывает переданное действие для применения обновления.
     *
     * @param guildId Идентификатор гильдии; значение 0 означает глобальную область (global commands).
     * @param jda Экземпляр JDA, используемый для чтения текущих команд в указанном контексте.
     * @param action Функция, которая принимает список деклараций команд JDA для регистрации и возвращает список зарегистрированных CommandJDA после выполнения обновления.
     * @return Список существующих либо только что обновлённых команд в виде объектов CommandJDA.
     */
    private fun updateCommands(
        guildId: Long,
        jda: JDA,
        action: (List<CommandData>) -> List<CommandJDA>
    ): List<CommandJDA> {
        logger.info { "Starting command update for ${if (guildId == 0L) "global scope" else "guild ID: $guildId"}" }

        // Combine legacy commands and DSL commands
        val legacyCommands = holderService.publicCommands.values.map { toJdaDeclaration(it) }
        val dslCommands = holderService.dslCommands.values.map { toDslJdaDeclaration(it) }
        
        val allCommands = legacyCommands + dslCommands

        logger.info { "Registering ${legacyCommands.size} legacy commands and ${dslCommands.size} DSL commands" }

        // Log command type breakdown once instead of per command
        CommandJDA.Type.entries
            .filter { it != CommandJDA.Type.UNKNOWN }
            .forEach { type ->
                logger.info { "Type ${type.name}: ${allCommands.count { it.type == type }} commands" }
            }

        val existingCommands = fetchExistingCommands(guildId, jda) ?: return emptyList()

        val needsUpdate = allCommands.size != existingCommands.size ||
                allCommands.any { appCommand ->
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
            val updatedCommands = action(allCommands)
            logger.info { "Successfully updated ${updatedCommands.size} commands for ${if (guildId == 0L) "global" else "guild ID: $guildId"}" }
            updatedCommands
        } else {
            logger.info { "No update needed. Commands are already up-to-date for ${if (guildId == 0L) "global" else "guild ID: $guildId"}" }
            existingCommands
        }
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

            // If the command has an executor and no subcommands/groups, add options from the executor
            val executor = declaration.executor
            if (executor != null && declaration.subcommands.isEmpty() && declaration.subcommandGroups.isEmpty()) {
                for (reference in executor.options.registeredOptions) {
                    try {
                        addOptions(*createOption(reference).toTypedArray())
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to register DSL command option: ${reference.name}" }
                    }
                }
            }

            // Add subcommands (direct subcommands without groups)
            declaration.subcommands.forEach { subcommand ->
                addSubcommands(toSubcommandData(subcommand))
            }

            // Add subcommand groups
            declaration.subcommandGroups.forEach { group ->
                addSubcommandGroups(toSubcommandGroupData(group))
            }

            logger.debug { 
                "Registered DSL command: ${declaration.name} with ${declaration.subcommands.size} subcommands and ${declaration.subcommandGroups.size} groups" 
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

    /**
     * Получает список существующих команд для глобальной области или указанной гильдии.
     *
     * @param guildId Идентификатор гильдии; используйте 0 для глобальных команд.
     * @param jda Экземпляр JDA, используемый для запроса команд.
     * @return Список обнаруженных команд JDA; `null`, если гильдия с указанным ID не найдена.
     */
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
}