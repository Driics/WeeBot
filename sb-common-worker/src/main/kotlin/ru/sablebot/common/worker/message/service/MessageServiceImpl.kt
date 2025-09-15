package ru.sablebot.common.worker.message.service

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.exceptions.PermissionException
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service
import ru.sablebot.common.worker.event.service.ContextService
import java.awt.Color
import java.util.*

@Service
class MessageServiceImpl @Autowired constructor(
    private val contextService: ContextService,
    private val context: ApplicationContext,
) : MessageService {
    override fun getBaseEmbed(copyright: Boolean): EmbedBuilder =
        EmbedBuilder().setColor(Color.CYAN).apply {
            setFooter("Test embed footer")
        }

    override fun getMessage(code: String, vararg args: Any?): String? {
        return getMessageByLocale(code, contextService.getLocale(), *args)
    }

    override fun getMessageByLocale(key: String?, locale: Locale?, vararg args: Any?): String? {
        return key?.let {
            val resolvedLocale = locale ?: contextService.getLocale()
            context.getMessage(it, args as Array<Any>?, it, resolvedLocale)
        }
    }

    override fun getMessageByLocale(key: String, locale: String?, vararg args: Any?): String? {
        val resolvedLocale = when {
            locale.isNullOrBlank() -> contextService.getLocale()
            else -> contextService.getLocale(locale)
        }
        return getMessageByLocale(key, resolvedLocale, *args)
    }

    override fun hasMessage(code: String?): Boolean {
        return !code.isNullOrBlank() &&
                context.getMessage(code, null, null, contextService.getLocale()) != null
    }

    override fun <T> sendMessageSilent(action: (T) -> RestAction<Message>, embed: T) =
        sendMessageSilentQueue(action, embed)

    override fun <T> sendMessageSilentQueue(
        action: (T) -> RestAction<Message>,
        embed: T,
        messageConsumer: ((Message) -> Unit)?
    ) = try {
        action(embed).queue(messageConsumer)
    } catch (_: PermissionException) {
        // We don't care about errors (so it's quiet).
    }

    override fun <T> replySilent(
        action: (T) -> ReplyCallbackAction,
        embed: T,
        ephemeral: Boolean,
        consumer: ((InteractionHook) -> Unit)?
    ) =
        try {
            action(embed).setEphemeral(ephemeral).queue(consumer)
        } catch (_: PermissionException) {
            // Ignore
        }
}