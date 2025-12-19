package ru.sablebot.module.audio.utils

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.ErrorResponse
import org.springframework.context.ApplicationContext
import ru.sablebot.common.worker.command.service.InternalCommandsService
import ru.sablebot.common.worker.event.service.ContextService
import ru.sablebot.common.worker.message.service.MessageService
import ru.sablebot.module.audio.model.PlaybackInstance
import ru.sablebot.module.audio.model.RepeatMode
import ru.sablebot.module.audio.service.helper.AudioMessageManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

class MessageController(
    context: ApplicationContext,
    message: Message
) {
    private sealed class Action(val commandKey: String, val emoji: String) {
        data object Play : Action(PlayCommand.KEY, "▶")
        data object Pause : Action(PauseCommand.KEY, "\u23F8")
        data object Next : Action(SkipCommand.KEY, "⏭")
        data object Stop : Action(StopCommand.KEY, "\u23F9")
        data object RepeatCurrent : Action(RepeatCommand.KEY, "\uD83D\uDD02")
        data object RepeatAll : Action(RepeatCommand.KEY, "\uD83D\uDD01")
        data object RepeatNone : Action(RepeatCommand.KEY, "➡")
        data object VolumeDown : Action(VolumeCommand.KEY, "\uD83D\uDD09")
        data object VolumeUp : Action(VolumeCommand.KEY, "\uD83D\uDD0A")

        companion object {
            val entries = listOf(
                Play, Pause, Next, Stop,
                RepeatCurrent, RepeatAll, RepeatNone,
                VolumeDown, VolumeUp
            )
            private val byEmoji by lazy { entries.associateBy(Action::emoji) }
            fun fromEmoji(emoji: String): Action? = byEmoji[emoji]
        }
    }

    private val jda = message.jda
    private val messageId = message.idLong
    private val channelId = message.channel.idLong
    private val guildId = message.guild.idLong

    private val reactionsListener: ReactionsListener = context.bean()
    private val playerService: PlayerService = context.bean()
    private val messageManager: AudioMessageManager = context.bean()
    private val contextService: ContextService = context.bean()
    private val commandsService: InternalCommandsService = context.bean()
    private val messageService: MessageService = context.bean()

    @Volatile
    private var cancelled = false

    private val reactionFutures = CopyOnWriteArrayList<CompletableFuture<Void>>()

    private val channel: TextChannel? get() = jda.getTextChannelById(channelId)
    private val guild: Guild? get() = jda.getGuildById(guildId)

    private val availableActions: List<Action>
        get() = Action.entries.filter { it.isAvailable() }

    init {
        message.initReactionControls()
    }

    private fun Message.initReactionControls() {
        val requiredPermissions = listOf(Permission.MESSAGE_MANAGE, Permission.MESSAGE_ADD_REACTION)

        guild.selfMember
            .takeIf { it.hasPermission(channel.asGuildMessageChannel(), requiredPermissions) }
            ?.let {
                addReactions()
                subscribeToReactionEvents()
            }
    }

    private fun Message.addReactions() {
        availableActions.mapNotNull { action ->
            runCatching { addReaction(Emoji.fromUnicode(action.emoji)).submit() }.getOrNull()
        }.forEach { future -> reactionFutures += future }
    }

    private fun Message.subscribeToReactionEvents() {
        reactionsListener.onReactionAdd(id) { event ->
            event.takeUnless { cancelled || it.user.isBot }?.handle()
            false
        }
    }

    private fun MessageReactionAddEvent.handle() {
        Action.fromEmoji(reactionEmote.name)?.let { action ->
            contextService.withContext(guild) { processAction(action, member) }
        }
        tryRemoveReaction()
    }

    private fun MessageReactionAddEvent.tryRemoveReaction() {
        guild.selfMember
            .takeIf { it.hasPermission(textChannel, Permission.MESSAGE_MANAGE) }
            ?.run { reaction.removeReaction(user).queue() }
    }

    private fun processAction(action: Action, member: Member?) {
        val guild = guild ?: return
        val instance = playerService[guild] ?: return

        if (!action.canBePerformedBy(member, guild)) return

        action.execute(member, guild, instance)
            .takeIf { shouldUpdate -> shouldUpdate }
            ?.let { instance.current?.let(messageManager::updateMessage) }
    }

    private fun Action.canBePerformedBy(member: Member?, guild: Guild): Boolean =
        member != null && with(playerService) {
            hasAccess(member) && isInChannel(member) && isActive(guild)
        } && isAvailable(member)

    private fun Action.execute(
        member: Member?,
        guild: Guild,
        instance: PlaybackInstance
    ): Boolean = when (this) {
        Action.Play -> false.also { playerService.resume(guild, false) }
        Action.Pause -> false.also { playerService.pause(guild) }
        Action.Next -> false.also { playerService.skipTrack(member, guild) }
        Action.Stop -> false.also { handleStop(member, guild) }
        Action.VolumeUp -> instance.seekVolume(delta = 10, increase = true)
        Action.VolumeDown -> instance.seekVolume(delta = 10, increase = false)
        Action.RepeatAll -> instance.updateMode(RepeatMode.ALL)
        Action.RepeatNone -> instance.updateMode(RepeatMode.NONE)
        Action.RepeatCurrent -> instance.updateMode(RepeatMode.CURRENT)
    }

    private fun PlaybackInstance.updateMode(newMode: RepeatMode): Boolean =
        (mode != newMode).also { changed -> if (changed) mode = newMode }

    private fun handleStop(member: Member?, guild: Guild) {
        val textChannel = channel ?: return
        val stopped = playerService.stop(member, guild)

        val (messageKey, args) = when {
            stopped && member != null -> "discord.command.audio.stop.member" to arrayOf(member.effectiveName)
            stopped -> "discord.command.audio.stop" to emptyArray()
            else -> "discord.command.audio.notStarted" to emptyArray()
        }

        messageManager.onMessage(textChannel, messageKey, *args)
    }

    fun remove(soft: Boolean) {
        executeForMessage { message ->
            runCatching { if (soft) message.softRemove() else message.hardRemove() }
                .onFailure { error -> if (!error.isMissingAccess) throw error }
        }
    }

    private fun Message.softRemove() {
        cancelled = true

        takeIf { it.canManageReactions() }?.run {
            reactionFutures.onEach { it.cancel(false) }
            clearReactions().queue { reactionsListener.unsubscribe(id) }
        }
    }

    private fun Message.canManageReactions(): Boolean =
        guild.isAvailable && guild.selfMember.hasPermission(textChannel, Permission.MESSAGE_MANAGE)

    private fun Message.hardRemove() {
        messageService.delete(this)
        reactionsListener.unsubscribe(id)
    }

    private fun Action.isAvailable(member: Member? = null): Boolean =
        channel?.let { !commandsService.isRestricted(commandKey, it, member) } ?: false

    inline fun executeForMessage(
        crossinline onSuccess: (Message) -> Unit = {},
        crossinline onError: (Throwable) -> Unit = {}
    ) {
        channel?.retrieveMessageById(messageId)?.queue(
            { contextService.withContext(guildId) { onSuccess(it) } },
            { contextService.withContext(guildId) { onError(it) } }
        )
    }

    private val Throwable.isMissingAccess: Boolean
        get() = this is ErrorResponseException && errorResponse == ErrorResponse.MISSING_ACCESS

    companion object {
        private inline fun <reified T : Any> ApplicationContext.bean(): T = getBean(T::class.java)
    }
}