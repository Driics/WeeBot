package ru.driics.sablebot.common.worker.event.service

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.GenericEvent

interface SourceResolverService {
    fun getGuild(event: GenericEvent?): Guild?

    fun getUser(event: GenericEvent?): User?

    fun getMember(event: GenericEvent?): Member?
}