package ru.sablebot.module.moderation.model

import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import ru.sablebot.common.model.AutoModActionType

data class AutoModResult(
    val trigger: String,
    val action: AutoModActionType,
    val message: Message,
    val member: Member,
    val reason: String,
    val muteDuration: Long? = null,
    val deleteMessage: Boolean = false
)
