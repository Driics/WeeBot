package ru.driics.sablebot.common.worker.shared.service

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role

interface SupportService {
    val supportGuild: Guild?

    val donatorRole: Role?

    fun Member.isModerator(): Boolean

    fun grantDonators(donatorIds: Set<String>)
}