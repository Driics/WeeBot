package ru.driics.sablebot.common.worker.command.service

import ru.driics.sablebot.common.worker.command.model.Command
import ru.driics.sablebot.common.worker.command.model.DiscordCommand
import java.util.*

interface CommandsHolderService {

    var commands: Map<String, Command>

    var publicCommands: Map<String, Command>

    val descriptors: Map<String, List<DiscordCommand>>

    fun getByLocale(
        localizedKey: String,
        locale: Locale? = null,
        anyLocale: Boolean = false
    ): Command?

    fun isAnyCommand(key: String): Boolean

}