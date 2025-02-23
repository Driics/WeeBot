package ru.driics.sablebot.common.worker.command.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import ru.driics.sablebot.common.utils.LocaleUtils
import ru.driics.sablebot.common.worker.command.model.Command
import ru.driics.sablebot.common.worker.command.model.DiscordCommand
import java.util.*

@Service
class CommandsHolderServiceImpl : CommandsHolderService {

    override var commands: Map<String, Command> = mutableMapOf()

    override var publicCommands: Map<String, Command> = mutableMapOf()

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

    override fun isAnyCommand(key: String): Boolean =
        reverseCommandKeys.any { key.reversed().lowercase().startsWith(it) }

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