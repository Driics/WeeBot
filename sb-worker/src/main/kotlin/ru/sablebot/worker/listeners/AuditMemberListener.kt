package ru.sablebot.worker.listeners

import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent
import ru.sablebot.common.model.AuditActionType
import ru.sablebot.common.service.MemberService
import ru.sablebot.common.service.UserService
import ru.sablebot.common.worker.event.DiscordEvent
import ru.sablebot.common.worker.event.listeners.DiscordEventListener
import ru.sablebot.common.worker.modules.audit.provider.NicknameChangeAuditForwardProvider
import ru.sablebot.common.worker.modules.audit.service.ActionsHolderService
import ru.sablebot.common.worker.shared.service.DiscordEntityAccessor

@DiscordEvent(priority = 0)
class AuditMemberListener(
    private val actionsHolderService: ActionsHolderService,
    private val userService: UserService,
    private val memberService: MemberService,
    private val entityAccessor: DiscordEntityAccessor
) : DiscordEventListener() {
    override fun onGuildMemberUpdateNickname(event: GuildMemberUpdateNicknameEvent) {
        entityAccessor.getOrCreate(event.member)
        val member = memberService.get(event.member) ?: return

        if (event.member.effectiveName != member.effectiveName) {
            auditService.log(event.guild, AuditActionType.MEMBER_NAME_CHANGE)
                .withUser(member)
                .withAttribute(NicknameChangeAuditForwardProvider.OLD_NAME, event.oldNickname)
                .withAttribute(NicknameChangeAuditForwardProvider.NEW_NAME, event.newNickname)
                .save()

            member.effectiveName = event.member.effectiveName
            memberService.save(member)
        }
    }
}