package ru.sablebot.common.worker.command.model.context

import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import ru.sablebot.common.persistence.entity.GuildConfig
import ru.sablebot.common.persistence.entity.LocalUser
import ru.sablebot.common.worker.message.model.InteractionContext

// TODO: add correctly work with guildConfigs

class ApplicationCommandContext(
    val event: GenericCommandInteractionEvent,
    val config: GuildConfig,
    val userLocal: LocalUser?
) : InteractionContext(event)