package ru.sablebot.module.moderation.listener

import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import ru.sablebot.common.support.CoroutineLauncher
import ru.sablebot.common.worker.event.DiscordEvent
import ru.sablebot.common.worker.event.listeners.DiscordEventListener
import ru.sablebot.module.moderation.service.IRaidDetectionService

@DiscordEvent
class RaidDetectionListener(
    private val raidDetectionService: IRaidDetectionService,
    private val coroutineLauncher: CoroutineLauncher
) : DiscordEventListener() {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        coroutineLauncher.launchMessageJob(event) {
            try {
                raidDetectionService.onMemberJoin(event.member)
            } catch (e: Exception) {
                logger.error(e) { "Raid detection processing failed for member ${event.member.id} in guild ${event.guild.id}" }
            }
        }
    }
}
