package ru.sablebot.common.worker.command.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.worker.command.model.Command
import ru.sablebot.common.worker.command.model.DiscordCommand
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclaration
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import java.util.*

@Service
class CommandsHolderServiceImpl(
    private val dslWrappers: List<SlashCommandDeclarationWrapper>
) : CommandsHolderService {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    // Legacy command storage - kept for interface compatibility but always empty
    override var commands: Map<String, Command> = emptyMap()

    override var publicCommands: Map<String, Command> = emptyMap()

    // DSL commands storage
    override var dslCommands: Map<String, SlashCommandDeclaration> = mutableMapOf()
    private var dslCommandsByFullPath: Map<String, SlashCommandDeclaration> = mutableMapOf()

    init {
        registerDslCommands(dslWrappers)
    }

    /**
     * Находит команду по локализованному ключу в указанных локалях.
     *
     * Legacy method - returns null as all commands are now DSL-based.
     *
     * @param localizedKey Локализованный ключ команды (поиск нечувствителен к регистру).
     * @param locale Предпочитаемая локаль для поиска; если `null`, используется английская карта по умолчанию.
     * @param anyLocale Если `true`, сначала ищет во всех доступных локалях и при отсутствии результата пытается в `locale`; если `false`, ищет только в `locale`.
     * @return `null` as legacy commands are no longer supported.
     */
    override fun getByLocale(localizedKey: String, locale: Locale?, anyLocale: Boolean): Command? {
        return null
    }

    /**
     * Определяет, соответствует ли переданный ключ какой-либо зарегистрированной DSL-команде.
     *
     * @param key Ключ команды или полный путь для DSL-команды (например "cmd subcmd" или "cmd group subcmd").
     * @return `true` если ключ соответствует DSL-команде, `false` в противном случае.
     */
    override fun isAnyCommand(key: String): Boolean {
        val lowerKey = key.lowercase()
        val isDslCommand = dslCommandsByFullPath.containsKey(lowerKey)

        logger.debug { "isAnyCommand($key): dsl=$isDslCommand" }

        return isDslCommand
    }

    /**
     * Регистрирует DSL-команды и формирует два словаря: по имени команды и по её полному пути.
     *
     * Для каждой обёртки извлекается декларация команды; основная команда регистрируется по имени,
     * субкоманды — по пути "command subcommand", а субкоманды внутри групп — по пути "command group subcommand".
     * Результаты сохраняются в полях сервиса `dslCommands` и `dslCommandsByFullPath`.
     */
    private fun registerDslCommands(dslWrappers: List<SlashCommandDeclarationWrapper>) {
        val dslCommandMap = mutableMapOf<String, SlashCommandDeclaration>()
        val dslCommandsByPathMap = mutableMapOf<String, SlashCommandDeclaration>()

        logger.info { "Registering ${dslWrappers.size} DSL command wrappers" }

        dslWrappers.forEach { wrapper ->
            val declaration = wrapper.command().build()
            logger.debug { "Processing DSL command: ${declaration.name}" }

            // Register main command by name (ADD TO BOTH MAPS!)
            dslCommandMap[declaration.name] = declaration
            dslCommandsByPathMap[declaration.name] = declaration
            logger.debug { "Registered DSL command: ${declaration.name}" }

            // Register subcommands with full path (command subcommand)
            declaration.subcommands.forEach { subcommand ->
                val fullPath = "${declaration.name} ${subcommand.name}"
                dslCommandsByPathMap[fullPath] = subcommand
                logger.debug { "Registered DSL subcommand: $fullPath" }
            }

            // Register subcommand groups (command group subcommand)
            declaration.subcommandGroups.forEach { group ->
                group.subcommands.forEach { subcommand ->
                    val fullPath = "${declaration.name} ${group.name} ${subcommand.name}"
                    dslCommandsByPathMap[fullPath] = subcommand
                    logger.debug { "Registered DSL subcommand group: $fullPath" }
                }
            }
        }

        this.dslCommands = dslCommandMap.toMap()
        this.dslCommandsByFullPath = dslCommandsByPathMap.toMap()

        logger.info { "DSL commands registered: ${dslCommandMap.keys}" }
        logger.info { "Total DSL command paths registered: ${dslCommandsByPathMap.size}" }
    }

    /**
     * Возвращает дескриптор DSL-команды по её полному пути (например: "cmd subcmd" или "cmd group subcmd").
     *
     * @param fullPath Полный путь команды — имя команды и при необходимости имена подкоманды/группы, разделённые пробелом.
     * @return `SlashCommandDeclaration` если команда с таким путём зарегистрирована, `null` в противном случае.
     */
    override fun getDslCommandByFullPath(fullPath: String): SlashCommandDeclaration? {
        return dslCommandsByFullPath[fullPath]
    }

    /**
     * Legacy descriptors - returns empty map as all commands are now DSL-based.
     */
    override val descriptors: Map<CommandCategory, List<DiscordCommand>> = emptyMap()
}
