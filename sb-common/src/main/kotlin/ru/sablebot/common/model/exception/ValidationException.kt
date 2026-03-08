package ru.sablebot.common.model.exception

class ValidationException(
    message: String,
    vararg args: Any
) : DiscordException(message, null, false, false, *args)