package ru.sablebot.common.worker.command.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.worker.command.model.Command
import ru.sablebot.common.worker.command.model.DiscordCommand
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclaration
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import java.util.*

@Service
class CommandsHolderServiceImpl : CommandsHolderService {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override var commands: Map<String, Command> = mutableMapOf()

    override var publicCommands: Map<String, Command> = mutableMapOf()

    // DSL commands storage
    override var dslCommands: Map<String, SlashCommandDeclaration> = mutableMapOf()
    private var dslCommandsByFullPath: Map<String, SlashCommandDeclaration> = mutableMapOf()

    private lateinit var localizedCommands: Map<Locale, Map<String, Command>>

    /**
     * Находит команду по локализованному ключу в указанных локалях.
     *
     * @param localizedKey Локализованный ключ команды (поиск нечувствителен к регистру).
     * @param locale Предпочитаемая локаль для поиска; если `null`, используется английская карта по умолчанию.
     * @param anyLocale Если `true`, сначала ищет во всех доступных локалях и при отсутствии результата пытается в `locale`; если `false`, ищет только в `locale`.
     * @return Соответствующий объект `Command`, либо `null`, если команда не найдена.
     */
    override fun getByLocale(localizedKey: String, locale: Locale?, anyLocale: Boolean): Command? {
        val lowerCaseKey = localizedKey.lowercase()

        return if (anyLocale) {
            localizedCommands.values.firstNotNullOfOrNull { it[lowerCaseKey] }
                ?: getLocalizedMap(locale).getOrElse(lowerCaseKey) { null }
        } else {
            getLocalizedMap(locale).getOrElse(lowerCaseKey) { null }
        }
    }

    /**
     * Определяет, соответствует ли переданный ключ какой-либо зарегистрированной команде — либо устаревшей (legacy), либо DSL-команде.
     *
     * @param key Ключ команды или полный путь для DSL-команды (например "cmd subcmd" или "cmd group subcmd").
     * @return `true` если ключ соответствует legacy-команде или DSL-команде, `false` в противном случае.
     */
    override fun isAnyCommand(key: String): Boolean {
        val lowerKey = key.lowercase()
        val isLegacyCommand = commands.containsKey(lowerKey)
        val isDslCommand = dslCommandsByFullPath.containsKey(lowerKey)

        logger.debug { "isAnyCommand($key): legacy=$isLegacyCommand, dsl=$isDslCommand" }

        return isLegacyCommand || isDslCommand
    }

    /**
         * Получает карту команд, соответствующих заданной локали.
         *
         * @param locale Локаль для поиска карты команд; если аргумент опущен, используется Locale.ENGLISH.
         * @return Карту команд (ключ → Command) для указанной локали, либо пустую карту если для локали нет записей.
         */
        private fun getLocalizedMap(locale: Locale? = /*contextService.locale*/ Locale.ENGLISH): Map<String, Command> =
        localizedCommands[locale] ?: emptyMap()

    /**
     * Регистрирует набор команд и готовит внутренние индексы для быстрого поиска по ключам и локалям.
     *
     * Заполняет поля сервиса:
     * - `commands` — все команды по исходному ключу;
     * - `publicCommands` — команды, помеченные как публичные (не скрытые);
     * - `localizedCommands` — карта локалей к отображению локализованного (нижнего регистра) ключа -> команда.
     *
     * @param commands Список команд для регистрации (аннотированных `@DiscordCommand`).
     */
    @Autowired
    private fun registerCommands(commands: List<Command>) {
        val commandMap = mutableMapOf<String, Command>()
        val publicCommandMap = mutableMapOf<String, Command>()
        val localizedKeyMap = mutableMapOf<String, Command>()

        commands.filter { it::class.java.isAnnotationPresent(DiscordCommand::class.java) }
            .forEach {
                val annotation = it::class.java.getAnnotation(DiscordCommand::class.java)
                val rawKey = annotation.key

                commandMap[rawKey] = it

                if (!annotation.hidden)
                    publicCommandMap[rawKey] = it

                localizedKeyMap[rawKey.lowercase()] = it
            }

        this.commands = commandMap.toMap()
        this.publicCommands = publicCommandMap.toMap()
        // TODO: Implement real i18n localization
        this.localizedCommands = mapOf(Locale.ENGLISH to localizedKeyMap.toMap())
    }

    /**
     * Регистрирует DSL-команды и формирует два словаря: по имени команды и по её полному пути.
     *
     * Для каждой обёртки извлекается декларация команды; основная команда регистрируется по имени,
     * субкоманды — по пути "command subcommand", а субкоманды внутри групп — по пути "command group subcommand".
     * Результаты сохраняются в полях сервиса `dslCommands` и `dslCommandsByFullPath`.
     *
     * @param dslWrappers список обёрток деклараций slash-команд
     */
    @Autowired
    private fun registerDslCommands(dslWrappers: List<SlashCommandDeclarationWrapper>) {
        val dslCommandMap = mutableMapOf<String, SlashCommandDeclaration>()
        val dslCommandsByPathMap = mutableMapOf<String, SlashCommandDeclaration>()

        logger.info { "Registering ${dslWrappers.size} DSL command wrappers" }

        dslWrappers.forEach { wrapper ->
            val declaration = wrapper.command().build()
            logger.debug { "Processing DSL command: ${declaration.name}" }

            // Register main command by name (ADD TO BOTH MAPS!)
            dslCommandMap[declaration.name] = declaration  // ← ДОБАВИТЬ ЭТУ СТРОКУ
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

    override val descriptors: Map<CommandCategory, List<DiscordCommand>> by lazy {
        commands.values
            .asSequence()
            .mapNotNull { it.javaClass.getAnnotation(DiscordCommand::class.java) }
            .filterNot { it.hidden }
            .sortedBy { it.priority }
            .toList()
            .groupBy { it.group }
            .toSortedMap()
    }
}