package ru.sablebot.common.model.exception

class DiscordException : Exception {

    private val args: Array<out Any>?

    constructor() {
        this.args = null
    }

    constructor(message: String, vararg args: Any?) : this(message, null, *args)

    constructor(message: String, cause: Throwable?, vararg args: Any?) : super(message, cause) {
        this.args = arrayOf(args)
    }

    constructor(
        message: String,
        cause: Throwable?,
        enableSuppression: Boolean,
        writableStackTrace: Boolean,
        vararg args: Any?
    ) : super(message, cause, enableSuppression, writableStackTrace) {
        this.args = arrayOf(args)
    }
}
