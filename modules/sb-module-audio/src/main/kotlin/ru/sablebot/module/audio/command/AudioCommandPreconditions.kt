package ru.sablebot.module.audio.command

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import ru.sablebot.common.model.exception.DiscordException
import ru.sablebot.common.persistence.entity.MusicConfig
import ru.sablebot.module.audio.model.PlaybackInstance
import ru.sablebot.module.audio.service.PlayerServiceV4

object AudioCommandPreconditions {

    fun requireVoiceChannel(member: Member): VoiceChannel {
        val voiceState = member.voiceState
            ?: throw DiscordException("audio.error.not_in_voice")
        return voiceState.channel?.asVoiceChannel()
            ?: throw DiscordException("audio.error.not_in_voice")
    }

    fun requireSameChannel(member: Member, guild: Guild, playerService: PlayerServiceV4) {
        val memberChannel = requireVoiceChannel(member)
        val botChannel = playerService.connectedChannel(guild)
        if (botChannel != null && botChannel.idLong != memberChannel.idLong) {
            throw DiscordException("audio.error.not_same_channel")
        }
    }

    fun requireActivePlayer(guild: Guild, playerService: PlayerServiceV4): PlaybackInstance {
        return playerService.get(guild)
            ?: throw DiscordException("audio.error.no_active_player")
    }

    fun requireMusicRole(member: Member, musicConfig: MusicConfig?) {
        val roles = musicConfig?.roles
        if (roles.isNullOrEmpty()) return

        val memberRoleIds = member.roles.map { it.idLong }
        if (memberRoleIds.none { it in roles }) {
            throw DiscordException("audio.error.no_permission")
        }
    }
}
