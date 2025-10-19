package ru.sablebot.common.worker.command.model.context

import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import ru.sablebot.common.persistence.entity.GuildConfig
import ru.sablebot.common.persistence.entity.LocalUser
import ru.sablebot.common.worker.message.model.InteractionContext

/**
 * Контекст выполнения команды приложения.
 *
 * @param event событие взаимодействия с командой
 * @param guildConfig конфигурация гильдии
 * @param userLocal локальный пользователь, может быть null если пользователь не найден
 */
class ApplicationCommandContext(
    val event: GenericCommandInteractionEvent,
    val guildConfig: GuildConfig,
    val userLocal: LocalUser?
) : InteractionContext(event)