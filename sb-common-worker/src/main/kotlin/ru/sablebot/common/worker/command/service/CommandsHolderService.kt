package ru.sablebot.common.worker.command.service

import ru.sablebot.common.worker.command.model.Command
import ru.sablebot.common.worker.command.model.DiscordCommand
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclaration
import java.util.*

interface CommandsHolderService {

    var commands: Map<String, Command>

    var publicCommands: Map<String, Command>

    val descriptors: Map<String, List<DiscordCommand>>

    // DSL commands
    val dslCommands: Map<String, SlashCommandDeclaration>

    /**
     * Получает команду по локализованному ключу с учётом указанной локали.
     *
     * @param localizedKey Локализованный ключ команды (идентификатор команды, возможно содержащий информацию о локали).
     * @param locale Опциональная локаль для уточнения поиска; если `null`, используется поведение без уточнения по локали.
     * @param anyLocale Если `true`, поиск будет возвращать команду независимо от локали соответствия.
     * @return `Command`, соответствующая ключу, или `null`, если соответствующая команда не найдена.
     */
    fun getByLocale(
        localizedKey: String,
        locale: Locale? = null,
        anyLocale: Boolean = false
    ): Command?

    /**
 * Находит DSL-описание slash-команды по её полному пути.
 *
 * @param fullPath Полный путь команды в DSL (например "group/subcommand").
 * @return Соответствующий `SlashCommandDeclaration`, или `null`, если команда не найдена.
 */
fun getDslCommandByFullPath(fullPath: String): SlashCommandDeclaration?

    /**
 * Проверяет, соответствует ли заданный ключ любой зарегистрированной команде.
 *
 * @param key Ключ команды — может быть локализованным или общим идентификатором команды.
 * @return `true` если ключ соответствует любой зарегистрированной команде, `false` в противном случае.
 */
fun isAnyCommand(key: String): Boolean

}