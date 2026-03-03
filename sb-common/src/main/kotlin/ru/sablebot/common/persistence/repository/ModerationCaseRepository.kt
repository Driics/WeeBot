package ru.sablebot.common.persistence.repository

import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import ru.sablebot.common.model.ModerationCaseType
import ru.sablebot.common.persistence.entity.ModerationCase
import ru.sablebot.common.persistence.repository.base.GuildRepository

@Repository
interface ModerationCaseRepository : GuildRepository<ModerationCase> {

    @Query("SELECT COALESCE(MAX(m.caseNumber), 0) FROM ModerationCase m WHERE m.guildId = :guildId")
    fun findMaxCaseNumber(guildId: Long): Int

    fun findByGuildIdAndCaseNumber(guildId: Long, caseNumber: Int): ModerationCase?

    fun findByGuildIdAndTargetIdOrderByCaseNumberDesc(guildId: Long, targetId: String): List<ModerationCase>

    fun findByGuildIdAndTargetIdAndActionTypeAndActive(
        guildId: Long,
        targetId: String,
        actionType: ModerationCaseType,
        active: Boolean
    ): List<ModerationCase>

    fun countByGuildIdAndTargetIdAndActionTypeAndActive(
        guildId: Long,
        targetId: String,
        actionType: ModerationCaseType,
        active: Boolean
    ): Int
}
