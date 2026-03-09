package ru.sablebot.common.worker.command.service

import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclaration

interface CommandsHolderService {

    // DSL commands
    val dslCommands: Map<String, SlashCommandDeclaration>

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