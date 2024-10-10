package ru.driics.sablebot.common.worker.message.service

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction

interface MessageService {
    fun getBaseEmbed(copyright: Boolean = false): EmbedBuilder

    fun <T> sendMessageSilent(action: (T) -> RestAction<Message>, embed: T)

    fun <T> sendMessageSilentQueue(
        action: (T) -> RestAction<Message>,
        embed: T,
        messageConsumer: ((Message) -> Unit)? = null
    )

    fun <T> replySilent(
        action: (T) -> ReplyCallbackAction,
        embed: T,
        ephemeral: Boolean = false,
        consumer: ((InteractionHook) -> Unit)? = null
    )
}