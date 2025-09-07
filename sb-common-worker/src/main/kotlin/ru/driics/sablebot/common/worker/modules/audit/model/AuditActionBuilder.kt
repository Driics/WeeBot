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

    protected val action: AuditAction = AuditAction(guildId).apply {
        actionDate = Date()
        this.actionType = actionType
        attributes = mutableMapOf()
    }

    protected val attachments: MutableMap<String, ByteArray> = mutableMapOf()

    fun withUser(user: User?) = apply {
        action.user = user?.let { getReference(it) }!!
    }

    fun withUser(member: Member?) = apply {
        action.user = member?.let { getReference(it) }!!
    }

    fun withUser(user: LocalUser?) = apply {
        action.user = user?.let { getReference(it) }!!
    }

    fun withUser(member: LocalMember?) = apply {
        action.user = member?.let { getReference(it) }!!
    }

    fun withTargetUser(user: User?) = apply {
        action.targetUser = user?.let { getReference(it) }!!
    }

    fun withTargetUser(member: Member?) = apply {
        action.targetUser = member?.let { getReference(it) }!!
    }

    fun withTargetUser(user: LocalUser?) = apply {
        action.targetUser = user?.let { getReference(it) }!!
    }

    fun withTargetUser(member: LocalMember?) = apply {
        action.targetUser = member?.let { getReference(it) }!!
    }

    fun withChannel(channel: GuildChannel?) = apply {
        action.channel = channel?.let { getReference(it) }!!
    }

    fun withAttribute(key: String, value: Any?) = apply {
        action.attributes[key] = getReferenceForObject(value)!!
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
        else -> obj
    }

    private fun getReference(user: User) = NamedReference(user.id, user.name)
    private fun getReference(user: LocalUser) = NamedReference(user.userId, user.name!!)
    private fun getReference(member: Member) =
        NamedReference(member.user.id, member.effectiveName)
    private fun getReference(member: LocalMember) =
        NamedReference(member.user.userId, member.effectiveName)
    private fun getReference(channel: GuildChannel) =
        NamedReference(channel.id, channel.name)

    abstract fun save(): AuditAction
}
