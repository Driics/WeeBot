package ru.driics.sablebot.common.worker.shared.service

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.sharding.ShardManager

interface DiscordService {
    val jda: JDA
    val shardManager: ShardManager
    val selfUser: User

    fun isConnected(guildId: Long = 0L): Boolean

    fun getShardById(shardId: Int): JDA?

    fun getShard(guildId: Long): JDA?

    fun getGuildById(guildId: Long): Guild?

    fun isSuperUser(user: User): Boolean
}