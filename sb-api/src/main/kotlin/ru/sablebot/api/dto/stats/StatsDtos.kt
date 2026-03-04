package ru.sablebot.api.dto.stats

data class StatsOverviewResponse(
    val memberCount: Long,
    val commandsLast24h: Long,
    val modActionsLast7d: Long,
    val activeCases: Long
)

data class CommandUsageResponse(
    val period: String,
    val data: List<CommandUsageEntry>
)

data class CommandUsageEntry(
    val date: String,
    val count: Long
)

data class MemberGrowthResponse(
    val period: String,
    val data: List<MemberGrowthEntry>
)

data class MemberGrowthEntry(
    val date: String,
    val joins: Long,
    val leaves: Long,
    val total: Long
)

data class AudioStatsResponse(
    val period: String,
    val totalTracksPlayed: Long,
    val totalListeningMinutes: Long,
    val topTracks: List<TrackEntry>
)

data class TrackEntry(
    val title: String,
    val playCount: Long
)
