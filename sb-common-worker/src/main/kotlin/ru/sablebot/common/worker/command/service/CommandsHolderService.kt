package ru.sablebot.common.worker.command.service

import ru.sablebot.common.worker.command.model.Command
import ru.sablebot.common.worker.command.model.DiscordCommand
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclaration
import java.util.*

interface CommandsHolderService {

    var commands: Map<String, Command>

    var publicCommands: Map<String, Command>
    
    var dslCommands: Map<String, SlashCommandDeclaration>

    val descriptors: Map<String, List<DiscordCommand>>

    fun getByLocale(
        localizedKey: String,
        locale: Locale? = null,
        anyLocale: Boolean = false
    ): Command?
    
    fun getDslCommandByName(name: String): SlashCommandDeclaration?

    fun isAnyCommand(key: String): Boolean

}