package ru.sablebot.module.audio.service

import dev.arbjerg.lavalink.client.LavalinkClient
import dev.arbjerg.lavalink.client.player.LavalinkPlayer
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import ru.sablebot.common.worker.shared.service.AudioService

interface ILavalinkV4AudioService : AudioService {
    val lavalink: LavalinkClient

    fun player(guildId: Long): LavalinkPlayer

    fun connect(channel: VoiceChannel)
    fun disconnect(guild: Guild)

    fun isConnected(guild: Guild): Boolean

    fun isReady(): Boolean

    fun shutdown()

    fun addOnConfiguredCallback(callback: () -> Unit)
}
