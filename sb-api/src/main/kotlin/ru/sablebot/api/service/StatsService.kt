package ru.sablebot.api.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.sablebot.api.dto.stats.*
import ru.sablebot.common.persistence.repository.ModerationCaseRepository

@Service
class StatsService(
    private val moderationCaseRepository: ModerationCaseRepository
) {
    private val logger = KotlinLogging.logger {}

    @Transactional(readOnly = true)
    fun getOverview(guildId: Long): StatsOverviewResponse {
        val cases = moderationCaseRepository.findAllByGuildId(guildId)
        val activeCases = cases.count { it.active }

        return StatsOverviewResponse(
            memberCount = 0, // Would need Discord API or cached value
            commandsLast24h = 0, // Would need command execution tracking
            modActionsLast7d = cases.size.toLong(),
            activeCases = activeCases.toLong()
        )
    }

    fun getCommandUsage(guildId: Long, period: String): CommandUsageResponse {
        // Placeholder — requires command execution tracking table
        return CommandUsageResponse(
            period = period,
            data = emptyList()
        )
    }

    fun getMemberGrowth(guildId: Long, period: String): MemberGrowthResponse {
        // Placeholder — requires member join/leave tracking
        return MemberGrowthResponse(
            period = period,
            data = emptyList()
        )
    }

    fun getAudioStats(guildId: Long, period: String): AudioStatsResponse {
        // Placeholder — requires audio tracking table
        return AudioStatsResponse(
            period = period,
            totalTracksPlayed = 0,
            totalListeningMinutes = 0,
            topTracks = emptyList()
        )
    }
}
