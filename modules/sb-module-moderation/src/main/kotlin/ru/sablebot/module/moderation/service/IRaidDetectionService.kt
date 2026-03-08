package ru.sablebot.module.moderation.service

import net.dv8tion.jda.api.entities.Member

interface IRaidDetectionService {
    suspend fun onMemberJoin(member: Member)
}
