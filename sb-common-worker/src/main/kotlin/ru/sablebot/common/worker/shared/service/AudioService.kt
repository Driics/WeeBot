package ru.sablebot.common.worker.shared.service

import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder

interface AudioService {
    fun configure(discordService: DiscordService, builder: DefaultShardManagerBuilder)
}