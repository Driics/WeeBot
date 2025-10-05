package ru.sablebot.common.worker.command.model

import net.dv8tion.jda.api.Permission
import org.springframework.stereotype.Component
import ru.sablebot.common.model.CommandCategory
import java.lang.annotation.Inherited

@Component
@Inherited
@Target(AnnotationTarget.TYPE, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class DiscordCommand(
    val key: String,
    val description: String,
    val permissions: Array<Permission> = [Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS],
    val memberRequiredPermissions: Array<Permission> = [],
    val group: CommandCategory = CommandCategory.GENERAL,
    val priority: Int = 1,
    val hidden: Boolean = false,
    val nsfw: Boolean = false,
)
