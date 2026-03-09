package ru.sablebot.common.worker.command.service

import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclaration

interface CommandsHolderService {

    // DSL commands
    val dslCommands: Map<String, SlashCommandDeclaration>

    /**
     * Получает команду по локализованному ключу с учётом указанной локали.
     *
     * @deprecated Legacy method - returns null as legacy commands have been removed
     * @param localizedKey Локализованный ключ команды (идентификатор команды, возможно содержащий информацию о локали).
     * @param locale Опциональная локаль для уточнения поиска; если `null`, используется поведение без уточнения по локали.
     * @param anyLocale Если `true`, поиск будет возвращать команду независимо от локали соответствия.
     * @return Always returns `null` as legacy commands have been removed.
     */
    @Deprecated("Legacy commands have been removed, this always returns null")
    fun getByLocale(
        localizedKey: String,
        locale: java.util.Locale? = null,
        anyLocale: Boolean = false
    ): Nothing?

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