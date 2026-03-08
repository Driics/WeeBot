package ru.sablebot.module.moderation.command

import net.dv8tion.jda.api.entities.Member
import ru.sablebot.common.model.exception.DiscordException

object ModerationPreconditions {
    fun requireCanModerate(moderator: Member, target: Member) {
        if (target.id == moderator.id)
            throw DiscordException("You cannot moderate yourself.")
        if (target.user.isBot)
            throw DiscordException("You cannot moderate bots.")
        if (!moderator.canInteract(target))
            throw DiscordException("You cannot moderate this user — they have a higher or equal role.")
        val selfMember = moderator.guild.selfMember
        if (!selfMember.canInteract(target))
            throw DiscordException("I cannot moderate this user — they have a higher or equal role than me.")
    }
}
