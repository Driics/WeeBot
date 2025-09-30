package ru.sablebot.common.worker.command.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import ru.sablebot.common.utils.LocaleUtils
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

    private lateinit var descriptorsMap: Map<String, List<DiscordCommand>>
    private lateinit var reverseCommandKeys: Set<String>
    private lateinit var localizedCommands: Map<Locale, Map<String, Command>>

    override fun getByLocale(localizedKey: String, locale: Locale?, anyLocale: Boolean): Command? {
        val lowerCaseKey = localizedKey.lowercase()

        return if (anyLocale) {
            localizedCommands.values.firstNotNullOfOrNull { it[lowerCaseKey] }
                ?: getLocalizedMap(locale).getOrElse(lowerCaseKey) { null }
        } else {
            getLocalizedMap(locale).getOrElse(lowerCaseKey) { null }
        }
    }

    override fun isAnyCommand(key: String): Boolean {
        // Check legacy commands
        val isLegacyCommand = reverseCommandKeys.any { key.reversed().lowercase().startsWith(it) }
        
        // Check DSL commands (by full path)
        val isDslCommand = dslCommandsByFullPath.containsKey(key)
        
        logger.debug { "isAnyCommand($key): legacy=$isLegacyCommand, dsl=$isDslCommand" }
        
        return isLegacyCommand || isDslCommand
    }

    private fun getLocalizedMap(locale: Locale? = /*contextService.locale*/ Locale.ENGLISH): Map<String, Command> =
        localizedCommands[locale] ?: emptyMap()

    @Autowired
    private fun registerCommands(commands: List<Command>) {
        val commandMap = mutableMapOf<String, Command>()
        val publicCommandMap = mutableMapOf<String, Command>()

        val mutableLocalizedCommands = mutableMapOf<Locale, MutableMap<String, Command>>()
        val mutablePublicCommandKeys = mutableSetOf<String>()
        val mutableReverseCommandKeys = mutableSetOf<String>()

        val locales = LocaleUtils.SUPPORTED_LOCALES.values

        commands.filter { it::class.java.isAnnotationPresent(DiscordCommand::class.java) }
            .forEach {
                val annotation = it::class.java.getAnnotation(DiscordCommand::class.java)
                val rawKey = annotation.key

                commandMap[rawKey] = it

                if (!annotation.hidden)
                    publicCommandMap[rawKey] = it

                locales.forEach { locale ->
                    val localeCommands = mutableLocalizedCommands.computeIfAbsent(locale) { mutableMapOf() }
                    val localizedKey = rawKey.lowercase()
                    mutableReverseCommandKeys.add(localizedKey.reversed())
                    if (!annotation.hidden) {
                        mutablePublicCommandKeys.add(localizedKey)
                    }
                    localeCommands[localizedKey] = it
                }
            }

        this.commands = commandMap.toMap()
        this.publicCommands = publicCommandMap.toMap()
        this.reverseCommandKeys = mutableReverseCommandKeys.toSet()
        this.localizedCommands = mutableLocalizedCommands.toMap()
    }

    @Autowired
    private fun registerDslCommands(dslWrappers: List<SlashCommandDeclarationWrapper>) {
        val dslCommandMap = mutableMapOf<String, SlashCommandDeclaration>()
        val dslCommandsByPathMap = mutableMapOf<String, SlashCommandDeclaration>()

        logger.info { "Registering ${dslWrappers.size} DSL command wrappers" }

        dslWrappers.forEach { wrapper ->
            val declaration = wrapper.command().build()
            dslCommandMap[declaration.name] = declaration
            
            // Register main command by name
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

    override fun getDslCommandByFullPath(fullPath: String): SlashCommandDeclaration? {
        return dslCommandsByFullPath[fullPath]
    }

    override val descriptors: Map<String, List<DiscordCommand>>
        get() {
            if (!::descriptorsMap.isInitialized) {
                descriptorsMap = commands.values
                    .asSequence()
                    .mapNotNull { it.javaClass.getAnnotation(DiscordCommand::class.java) }
                    .filterNot { it.hidden }
                    .sortedBy { it.priority }
                    .toList()
                    .groupBy { it.group.first() }
                    .toSortedMap()
            }

            return descriptorsMap
        }
}