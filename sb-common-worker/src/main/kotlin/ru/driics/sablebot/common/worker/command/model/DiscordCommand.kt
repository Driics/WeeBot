package ru.driics.sablebot.common.worker.command.model

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.internal.interactions.CommandDataImpl
import org.springframework.stereotype.Component
import java.lang.annotation.Inherited

@Component
@Inherited
@Target(AnnotationTarget.TYPE, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class DiscordCommand(
    val key: String,
    val description: String,
    val permissions: Array<Permission> = [Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS],
    val group: Array<String> = ["discord.command.group.common"],
    val priority: Int = 1,
    val hidden: Boolean = false,
    val nsfw: Boolean = false,
)
