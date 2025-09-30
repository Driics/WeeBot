package ru.sablebot.common.worker.command.model.context

import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback

class BotContext(
    private val replyCallback: IReplyCallback
)