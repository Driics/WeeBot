package ru.driics.sablebot.common.utils

import kotlinx.coroutines.future.await
import net.dv8tion.jda.api.requests.RestAction

object CommonUtils {
    fun trimTo(content: String?, length: Int): String? {
        if (content.isNullOrEmpty()) return content
        if (length <= 3) return "..."  // Handle cases where length is too short for content trimming
        return if (content.length > length) {
            content.substring(0, length - 3) + "..."
        } else {
            content
        }
    }
}

suspend fun <T> RestAction<T>.await(): T = this.submit().await()