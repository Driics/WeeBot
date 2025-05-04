package ru.driics.sablebot.common.worker.modules.moderation.model

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import ru.driics.sablebot.common.model.ModerationActionType
import java.io.Serializable
import java.time.Instant
import java.util.*

data class ModerationActionRequest(
    val type: ModerationActionType,
    val violator: Member? = null,
    val violatorId: String? = violator?.id,
    val moderator: Member? = null,
    val moderatorId: String? = moderator?.id,
    val auditLogging: Boolean = true,
    val reason: String? = null,
    val channel: TextChannel? = null,
    val global: Boolean = false,
    val delDays: Int? = null,
    val duration: Long? = null, // duration in millis
    val stateless: Boolean = false,
    val assignRoles: List<Long> = emptyList(),
    val revokeRoles: List<Long> = emptyList()
) : Serializable {

    fun getGuild(): Guild? {
        return moderator?.guild ?: violator?.guild ?: channel?.guild
    }

    fun getDurationDate(): Date? {
        return duration?.let {
            Date.from(Instant.now().plusMillis(it))
        }
    }

    companion object {
        fun build(block: Builder.() -> Unit): ModerationActionRequest {
            return Builder().apply(block).build()
        }
    }

    class Builder {
        var type: ModerationActionType? = null
        var violator: Member? = null
        var moderator: Member? = null
        var auditLogging: Boolean = true
        var reason: String? = null
        var channel: TextChannel? = null
        var global: Boolean = false
        var delDays: Int? = null
        var duration: Long? = null
        var stateless: Boolean = false
        var assignRoles: List<Long> = emptyList()
        var revokeRoles: List<Long> = emptyList()

        fun build(): ModerationActionRequest {
            val typeVal = requireNotNull(type) { "ModerationActionType is required" }
            return ModerationActionRequest(
                type = typeVal,
                violator = violator,
                violatorId = violator?.id,
                moderator = moderator,
                moderatorId = moderator?.id,
                auditLogging = auditLogging,
                reason = reason,
                channel = channel,
                global = global,
                delDays = delDays,
                duration = duration,
                stateless = stateless,
                assignRoles = assignRoles,
                revokeRoles = revokeRoles
            )
        }
    }
}