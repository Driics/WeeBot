package ru.driics.sablebot.common.worker.modules.audit.model

import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import ru.driics.sablebot.common.model.AuditActionType
import ru.driics.sablebot.common.persistence.entity.AuditAction
import ru.driics.sablebot.common.persistence.entity.LocalMember
import ru.driics.sablebot.common.persistence.entity.LocalUser
import ru.driics.sablebot.common.persistence.entity.base.NamedReference
import java.util.*

abstract class AuditActionBuilder(
    guildId: Long,
    actionType: AuditActionType
) {

    protected val action: AuditAction = AuditAction(guildId, actionType).apply {
        actionDate = Date()
        attributes = mutableMapOf()
    }

    protected val attachments: MutableMap<String, ByteArray> = mutableMapOf()

    fun withUser(user: User?) = apply {
        user?.let { action.user = getReference(it) }
    }

    fun withUser(member: Member?) = apply {
        member?.let { action.user = getReference(it) }
    }

    fun withUser(user: LocalUser?) = apply {
        user?.let { action.user = getReference(it) }
    }

    fun withUser(member: LocalMember?) = apply {
        member?.let { action.user = getReference(it) }
    }

    fun withTargetUser(user: User?) = apply {
        user?.let { action.targetUser = getReference(it) }
    }

    fun withTargetUser(member: Member?) = apply {
        member?.let { action.targetUser = getReference(it) }
    }

    fun withTargetUser(user: LocalUser?) = apply {
        user?.let { action.targetUser = getReference(it) }
    }

    fun withTargetUser(member: LocalMember?) = apply {
        member?.let { action.targetUser = getReference(it) }
    }

    fun withChannel(channel: GuildChannel?) = apply {
        channel?.let { action.channel = getReference(it) }
    }

    fun withAttribute(key: String, value: Any?) = apply {
        val v = getReferenceForObject(value)
        if (v != null) action.attributes[key] = v
    }

    fun withAttachment(key: String, data: ByteArray) = apply {
        attachments[key] = data
    }

    private fun getReferenceForObject(obj: Any?): Any? = when (obj) {
        is User -> getReference(obj)
        is LocalUser -> getReference(obj)
        is Member -> getReference(obj)
        is LocalMember -> getReference(obj)
        is GuildChannel -> getReference(obj)
        is String, is Number, is Boolean, null -> obj
        else -> obj.toString()
    }

    private fun getReference(user: User) = NamedReference(user.id, user.name)
    private fun getReference(user: LocalUser) = NamedReference(user.userId, user.name!!)
    private fun getReference(member: Member) =
        NamedReference(member.user.id, member.effectiveName)
    private fun getReference(member: LocalMember): NamedReference {
        val u = member.user
            ?: return NamedReference("unknown", member.effectiveName ?: "unknown")
        val displayName = member.effectiveName ?: (u.name ?: u.userId)
        return NamedReference(u.userId, displayName)
    }
    private fun getReference(channel: GuildChannel) =
        NamedReference(channel.id, channel.name)

    abstract fun save(): AuditAction
}
