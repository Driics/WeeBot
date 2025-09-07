package ru.driics.sablebot.common.model.status

import kotlinx.serialization.Serializable

@Serializable
data class StatusDto(
    var guildCount: Long = 0,
    var userCount: Long = 0,
    var textChannelCount: Long = 0,
    var voiceChannelCount: Long = 0,
    var activeConnections: Long = 0,
    var uptimeDuration: Long = 0,
    var executedCommands: Long = 0,
    var shards: List<ShardDto> = emptyList(),
    var linkNodes: List<LavaLinkNodeDto> = emptyList()
)