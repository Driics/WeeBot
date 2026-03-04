package ru.sablebot.common.utils

import java.time.Duration

object DurationParser {

    private val PATTERN = Regex(
        "^" +
                """((\d+)(y|year|years|г|год|года|лет))?""" +
                """((\d+)(mo|mos|month|months|мес|месяц|месяца|месяцев))?""" +
                """((\d+)(w|week|weeks|н|нед|неделя|недели|недель|неделю))?""" +
                """((\d+)(d|day|days|д|день|дня|дней))?""" +
                """((\d+)(h|hour|hours|ч|час|часа|часов))?""" +
                """((\d+)(min|mins|minute|minutes|мин|минута|минуту|минуты|минут|m))?""" +
                """((\d+)(s|sec|secs|second|seconds|с|c|сек|секунда|секунду|секунды|секунд))?""" +
                """((\d+)(ms|millis|millisecond|milliseconds|мс|миллисекунда|миллисекунды|миллисекунд))?$"""
    )

    private const val MS_IN_SECOND = 1000L
    private const val MS_IN_MINUTE = 60 * MS_IN_SECOND
    private const val MS_IN_HOUR = 60 * MS_IN_MINUTE
    private const val MS_IN_DAY = 24 * MS_IN_HOUR
    private const val MS_IN_WEEK = 7 * MS_IN_DAY
    private const val MS_IN_MONTH = 30 * MS_IN_DAY
    private const val MS_IN_YEAR = 365 * MS_IN_DAY

    /**
     * Parses a compact duration string into a [Duration].
     *
     * Supports English and Russian units:
     * years, months, weeks, days, hours, minutes, seconds, milliseconds.
     *
     * Months use fixed 30-day arithmetic, years use 365-day.
     *
     * @return parsed Duration, or null if the input is invalid or zero
     */
    fun parse(input: String): Duration? {
        val text = input.trim().lowercase()
        val match = PATTERN.matchEntire(text) ?: return null
        val groups = match.groupValues

        if (groups.drop(1).all { it.isEmpty() }) return null

        var millis = 0L
        groups.getOrNull(2)?.takeIf { it.isNotEmpty() }?.let { millis += it.toLong() * MS_IN_YEAR }
        groups.getOrNull(5)?.takeIf { it.isNotEmpty() }?.let { millis += it.toLong() * MS_IN_MONTH }
        groups.getOrNull(8)?.takeIf { it.isNotEmpty() }?.let { millis += it.toLong() * MS_IN_WEEK }
        groups.getOrNull(11)?.takeIf { it.isNotEmpty() }?.let { millis += it.toLong() * MS_IN_DAY }
        groups.getOrNull(14)?.takeIf { it.isNotEmpty() }?.let { millis += it.toLong() * MS_IN_HOUR }
        groups.getOrNull(17)?.takeIf { it.isNotEmpty() }?.let { millis += it.toLong() * MS_IN_MINUTE }
        groups.getOrNull(20)?.takeIf { it.isNotEmpty() }?.let { millis += it.toLong() * MS_IN_SECOND }
        groups.getOrNull(23)?.takeIf { it.isNotEmpty() }?.let { millis += it.toLong() }

        return if (millis > 0) Duration.ofMillis(millis) else null
    }

    /**
     * Formats a duration in milliseconds into a human-readable string like "2d 3h 15m".
     */
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
