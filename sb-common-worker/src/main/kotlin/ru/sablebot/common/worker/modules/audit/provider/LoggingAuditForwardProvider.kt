package ru.sablebot.common.worker.modules.audit.provider

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.sablebot.common.persistence.entity.AuditAction
import ru.sablebot.common.persistence.entity.AuditConfig
import ru.sablebot.common.persistence.entity.base.NamedReference
import ru.sablebot.common.persistence.repository.AuditConfigRepository
import ru.sablebot.common.worker.event.service.ContextService
import ru.sablebot.common.worker.message.service.MessageService
import ru.sablebot.common.worker.modules.audit.service.AuditService
import ru.sablebot.common.worker.shared.service.DiscordService
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant

@Component
abstract class LoggingAuditForwardProvider : AuditForwardProvider {

    @Autowired
    protected lateinit var discordService: DiscordService

    @Autowired
    protected lateinit var auditService: AuditService

    @Autowired
    protected lateinit var messageService: MessageService

    @Autowired
    protected lateinit var configRepository: AuditConfigRepository

    @Autowired
    protected lateinit var contextService: ContextService

    @OptIn(ExperimentalTime::class)
    @Transactional
    override fun send(
        config: AuditConfig,
        action: AuditAction,
        attachments: Map<String, ByteArray>
    ) {
        val clazz = this::class.java

        if (!shouldProcessAudit(config, action, clazz)) return
        if (!discordService.isConnected(action.guildId)) return

        val guild = discordService.getGuildById(action.guildId) ?: return
        val self = guild.selfMember
        val channel = guild.getTextChannelById(config.forwardChannelId) ?: return

        if (!self.hasPermission(
                channel,
                Permission.VIEW_CHANNEL,
                Permission.MESSAGE_SEND,
                Permission.MESSAGE_EMBED_LINKS
        )) {
            return
        }

        contextService.withContext(guild) {
            val embedBuilder = messageService.getBaseEmbed()
            val messageBuilder = MessageCreateBuilder()

            build(action, messageBuilder, embedBuilder)

            if (!embedBuilder.isEmpty) {
                embedBuilder.apply {
                    setTimestamp(Clock.System.now().toJavaInstant())
                    action.actionType.color?.let(::setColor)
                }

                messageBuilder.setEmbeds(embedBuilder.build())
            }

            if (!messageBuilder.isEmpty || attachments.isNotEmpty()) {
                sendMessage(channel, messageBuilder, attachments, self)
            }
        }
    }

    private fun shouldProcessAudit(config: AuditConfig, action: AuditAction, clazz: Class<*>): Boolean {
        return config.forwardEnabled
                && config.forwardActions.isNotEmpty()
                && action.actionType in config.forwardActions
                && clazz.isAnnotationPresent(ForwardProvider::class.java)
                && clazz.getAnnotation(ForwardProvider::class.java).value == action.actionType
    }

    private fun sendMessage(
        channel: TextChannel,
        messageBuilder: MessageCreateBuilder,
        attachments: Map<String, ByteArray>?,
        self: Member
    ) {
        val messageAction = channel.sendMessage(messageBuilder.build())

        val uploads = attachments?.takeIf {
            self.hasPermission(channel, Permission.MESSAGE_ATTACH_FILES)
        }?.map { (filename, data) ->
            FileUpload.fromData(data, filename)
        }?.toList().orEmpty()

        messageAction.addFiles(uploads)

        messageAction.queue()
    }

    protected fun addChannelField(action: AuditAction, embedBuilder: EmbedBuilder) {
        action.channel.let { channel ->
            embedBuilder.addField(
                messageService.getMessage("audit.channel.title") ?: "",
                getReferenceContent(channel, isChannel = true) ?: "",
                true
            )
        }
    }

    protected fun getReferenceContent(reference: NamedReference, isChannel: Boolean): String? =
        messageService.getMessage(
            "audit.reference.content",
            reference.name,
            if (isChannel) reference.asChannelMention else reference.asUserMention
        )

    protected fun getReferenceShortContent(reference: NamedReference): String? =
        messageService.getMessage("audit.reference.short.content", reference.name)

    protected abstract fun build(
        action: AuditAction,
        messageBuilder: MessageCreateBuilder,
        embedBuilder: EmbedBuilder
    )
}