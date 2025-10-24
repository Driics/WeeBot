package ru.sablebot.common.worker.shared.service

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.sablebot.common.persistence.entity.GuildConfig
import ru.sablebot.common.persistence.entity.LocalMember
import ru.sablebot.common.persistence.entity.LocalUser
import ru.sablebot.common.persistence.repository.LocalMemberRepository
import ru.sablebot.common.persistence.repository.LocalUserRepository
import ru.sablebot.common.service.ConfigService
import ru.sablebot.common.service.MemberService
import ru.sablebot.common.service.UserService
import java.util.*

@Service
open class DiscordEntityAccessor(
    protected val configService: ConfigService,
    protected val userService: UserService,
    protected val userRepository: LocalUserRepository,
    protected val memberService: MemberService,
    protected val memberRepository: LocalMemberRepository,
) {
    private val `$userLock`: Any = arrayOfNulls<Any>(0)

    // TODO: refactoring to avoid using this
    private val `$memberLock`: Any = arrayOfNulls<Any>(0)

    @Transactional
    open fun getOrCreate(guild: Guild): GuildConfig {
        val config = configService.getOrCreate(guild.idLong)
        return updateIfRequired(guild, config)
    }

    @Transactional
    open fun getOrCreate(user: User): LocalUser? {
        if (!userService.isApplicable(user))
            return null

        var localUser = userService.get(user)
        if (localUser == null) {
            synchronized(`$userLock`) {
                localUser = userService.get(user)
                if (localUser == null) {
                    localUser = LocalUser().apply {
                        userId = user.id
                    }
                    updateIfRequired(user, localUser)
                    userRepository.flush()
                    return localUser
                }
            }
        }

        return updateIfRequired(user, localUser)
    }

    @Transactional
    open fun getOrCreate(member: Member): LocalMember? {
        if (!memberService.isApplicable(member))
            return null

        var localMember = memberService.get(member)
        if (localMember == null) {
            synchronized(`$memberLock`) {
                localMember = memberService.get(member)
                if (localMember == null) {
                    localMember = LocalMember(
                        user = getOrCreate(member.user),
                        effectiveName = member.effectiveName
                    ).apply { guildId = member.guild.idLong }

                    updateIfRequired(member, localMember)
                    memberRepository.flush()
                    return localMember
                }
            }
        }
        return updateIfRequired(member, localMember)
    }

    private fun updateIfRequired(guild: Guild, config: GuildConfig): GuildConfig {
        try {
            var shouldSave = false
            if (!Objects.equals(config.name, guild.name)) {
                config.name = guild.name
                shouldSave = true
            }
            if (!Objects.equals(config.iconUrl, guild.getIconUrl())) {
                config.iconUrl = guild.getIconUrl()
                shouldSave = true
            }
            if (shouldSave) {
                configService.save(config)
            }
        } catch (_: ObjectOptimisticLockingFailureException) {
            // it's ok to ignore optlock here, anyway it will be updated later
        }
        return config
    }

    private fun updateIfRequired(user: User?, localUser: LocalUser?): LocalUser? {
        if (localUser == null) return null

        return try {
            var shouldSave = localUser.id == null

            user?.let {
                if (it.name != localUser.name) {
                    localUser.name = it.name
                    shouldSave = true
                }
                if (it.discriminator != localUser.discriminator) {
                    localUser.discriminator = it.discriminator
                    shouldSave = true
                }
            }

            if (shouldSave) userService.save(localUser) else localUser
        } catch (_: ObjectOptimisticLockingFailureException) {
            localUser
        }
    }

    private fun updateIfRequired(member: Member, localMember: LocalMember?): LocalMember? {
        try {
            var shouldSave = false
            if (localMember?.id == null) {
                shouldSave = true;
            }

            updateIfRequired(member.user, localMember?.user);

            if (shouldSave) {
                memberService.save(localMember!!);
            }
        } catch (_: ObjectOptimisticLockingFailureException) {
        }
        return localMember
    }
}