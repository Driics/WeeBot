package ru.sablebot.common.service

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.sablebot.common.persistence.entity.LocalMember
import ru.sablebot.common.persistence.repository.LocalMemberRepository

@Service
class MemberService(
    private val memberRepository: LocalMemberRepository,
    private val userService: UserService
) {
    @Transactional(readOnly = true)
    fun findLike(
        guildId: Long,
        query: String
    ) = memberRepository.findLike(guildId, query)

    @Transactional(readOnly = true)
    fun get(member: Member): LocalMember? = get(member.guild, member.user)

    @Transactional(readOnly = true)
    fun get(guild: Guild, user: User): LocalMember? = get(guild.idLong, user.idLong)

    @Transactional(readOnly = true)
    fun get(guildId: Long, userId: Long): LocalMember? = memberRepository.findByGuildIdAndUserId(guildId, userId)

    @Transactional
    fun save(localMember: LocalMember): LocalMember = memberRepository.save(localMember)

    fun isApplicable(member: Member): Boolean =
        member.guild.selfMember != member
                && userService.isApplicable(member.user)
}