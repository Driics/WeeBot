package ru.sablebot.module.moderation.model

import java.time.Duration

object DurationParser {

    private val PATTERN = Regex("(?:(\\d+)w)?\\s*(?:(\\d+)d)?\\s*(?:(\\d+)h)?\\s*(?:(\\d+)m)?\\s*(?:(\\d+)s)?")

    fun parse(input: String): Duration? {
        val match = PATTERN.matchEntire(input.trim().lowercase()) ?: return null
        val (weeks, days, hours, minutes, seconds) = match.destructured

        val totalSeconds = (weeks.toLongOrNull() ?: 0) * 7 * 24 * 3600 +
                (days.toLongOrNull() ?: 0) * 24 * 3600 +
                (hours.toLongOrNull() ?: 0) * 3600 +
                (minutes.toLongOrNull() ?: 0) * 60 +
                (seconds.toLongOrNull() ?: 0)

        return if (totalSeconds > 0) Duration.ofSeconds(totalSeconds) else null
    }

    fun format(millis: Long): String {
        val duration = Duration.ofMillis(millis)
        val parts = mutableListOf<String>()
        val days = duration.toDays()
        val hours = duration.toHours() % 24
        val minutes = duration.toMinutes() % 60

        if (days > 0) parts.add("${days}d")
        if (hours > 0) parts.add("${hours}h")
        if (minutes > 0) parts.add("${minutes}m")
        if (parts.isEmpty()) parts.add("${duration.seconds}s")

        return parts.joinToString(" ")
    }
}
