package ru.driics.sablebot.common.worker.message.service

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.exceptions.PermissionException
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.interactions.InteractionCallbackAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import ru.driics.sablebot.common.configuration.CommonConfiguration
import java.awt.Color

@Service
class MessageServiceImpl @Autowired constructor(
    private val context: ApplicationContext,
    @Qualifier(CommonConfiguration.SCHEDULER) private val scheduler: TaskScheduler,
) : MessageService {
    override fun getBaseEmbed(copyright: Boolean): EmbedBuilder =
        EmbedBuilder().setColor(Color.CYAN).apply {
            setFooter("Test embed footer")
        }

    override fun <T> sendMessageSilent(action: (T) -> RestAction<Message>, embed: T) =
        sendMessageSilentQueue(action, embed)

    override fun <T> sendMessageSilentQueue(
        action: (T) -> RestAction<Message>,
        embed: T,
        messageConsumer: ((Message) -> Unit)?
    ) = try {
        action(embed).queue(messageConsumer)
    } catch (e: PermissionException) {
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
        } catch (e: PermissionException) {
            // Ignore
        }
}