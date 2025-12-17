package ru.sablebot.common.utils

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

object TimeSequenceParser {

    enum class FieldType(
        val type: ChronoUnit,
        val maxUnits: Int,
        vararg val patterns: Regex
    ) {
        MONTH(ChronoUnit.MONTHS, 11, "^месяц(а|ев)?$".toRegex(), "^months?$".toRegex()),
        WEEK(ChronoUnit.WEEKS, 31, "^недел[юиь]$".toRegex(), "^weeks?$".toRegex()),
        DAY(ChronoUnit.DAYS, 6, "^(день|дн(я|ей))$".toRegex(), "^days?$".toRegex()),
        HOUR(ChronoUnit.HOURS, 23, "^час(а|ов)?$".toRegex(), "^hours?$".toRegex()),
        MINUTE(ChronoUnit.MINUTES, 59, "^минут[уы]?$".toRegex(), "^minutes?$".toRegex()),
        SECOND(ChronoUnit.SECONDS, 59, "^секунд[уы]?$".toRegex(), "^seconds?$".toRegex()),
        MILLISECOND(ChronoUnit.MILLIS, 999, "^миллисекунд[уы]?$".toRegex(), "^milliseconds?$".toRegex());

        fun matches(value: String): Boolean = patterns.any { it.containsMatchIn(value) }

        companion object {
            fun find(value: String): FieldType? = entries.firstOrNull { it.matches(value) }
        }
    }

    private val PART_PATTERN = Regex("""(\d+)\s+([a-zA-Zа-яА-Я]+)""")

    private val SHORT_SEQ_PATTERN = Regex(
        "^" +
                """((\d+)(y|year|years|г|год|года|лет))?""" +
                """((\d+)(mo|mos|month|months|мес|месяц|месяца|месяцев))?""" +
                """((\d+)(w|week|weeks|н|нед|неделя|недели|недель|неделю))?""" +
                """((\d+)(d|day|days|д|день|дня|дней))?""" +
                """((\d+)(h|hour|hours|ч|час|часа|часов))?""" +
                """((\d+)(min|mins|minute|minutes|мин|минута|минуту|минуты|минут))?""" +
                """((\d+)(s|sec|secs|second|seconds|с|c|сек|секунда|секунду|секунды|секунд))?""" +
                """((\d+)(ms|millis|millisecond|milliseconds|мс|миллисекунда|миллисекунды|миллисекунд))?$"""
    )


    fun parseFull(input: String): Long? {
        val values = mutableMapOf<FieldType, Int>()

        for ((units, fieldName) in PART_PATTERN.findAll(input).map {
            it.groupValues[1].toInt() to it.groupValues[2]
        }) {
            if (units == 0) return null

            val type = FieldType.find(fieldName) ?: return null

            if (type in values) return null // double declaration
            if (values.keys.any { it.ordinal >= type.ordinal }) return null // invalid sequence

            values[type] = units
        }

        if (values.size > 1 && values.any { (type, value) -> value > type.maxUnits }) {
            return null // strict sequence violation
        }

        if (values.isEmpty()) return null

        return values.entries.fold(LocalDateTime.now(ZoneOffset.UTC)) { acc, (type, units) ->
            type.type.addTo(acc, units.toLong())
        }.toEpochSecond(ZoneOffset.UTC) * 1000 - System.currentTimeMillis()
    }

    /**
     * Parses duration string
     *
     * Months and years use calendar arithmetic from epoch 0 (1970-01-01), so their exact duration depends on the reference date
     * Very large numeric values may cause overflow or incorrect results
     *
     * @param value String to parse
     * @return Amount of duration in milliseconds
     * @throws IllegalArgumentException if the format is invalid
     */
    fun parseShort(value: String): Long {
        val input = value.lowercase()
        val matcher = SHORT_SEQ_PATTERN.find(input)
            ?: throw IllegalArgumentException("Incorrect period/duration: $value")

        val groups = matcher.groupValues

        if (groups.drop(1).all { it.isEmpty() }) {
            throw IllegalArgumentException("Incorrect period/duration: $value")
        }

        return listOf(
            ChronoUnit.YEARS to groups.getOrNull(2),
            ChronoUnit.MONTHS to groups.getOrNull(5),
            ChronoUnit.WEEKS to groups.getOrNull(8),
            ChronoUnit.DAYS to groups.getOrNull(11),
            ChronoUnit.HOURS to groups.getOrNull(14),
            ChronoUnit.MINUTES to groups.getOrNull(17),
            ChronoUnit.SECONDS to groups.getOrNull(20),
            ChronoUnit.MILLIS to groups.getOrNull(23)
        ).fold(LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC)) { acc, (unit, amount) ->
            if (amount?.isNotEmpty() == true && amount.all { it.isDigit() }) {
                unit.addTo(acc, amount.toLong())
            } else {
                acc
            }
        }.toEpochSecond(ZoneOffset.UTC) * 1000
    }
}