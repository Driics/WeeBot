package ru.sablebot.common.worker.modules.audit.service

import net.dv8tion.jda.api.entities.Guild
import ru.sablebot.common.model.AuditActionType
import ru.sablebot.common.persistence.entity.AuditAction
import ru.sablebot.common.worker.modules.audit.model.AuditActionBuilder

interface AuditService {
    fun runCleanUp()
    fun runCleanUp(durationMonths: Int)
    fun save(action: AuditAction, attachments: Map<String, ByteArray>): AuditAction
    fun log(guildId: Long, type: AuditActionType) : AuditActionBuilder
    fun log(guild: Guild, type: AuditActionType) : AuditActionBuilder = log(guild.idLong, type)
}