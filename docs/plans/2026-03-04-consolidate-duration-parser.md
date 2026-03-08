# Consolidate DurationParser Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Merge `DurationParser` (moderation) and `TimeSequenceParser` (common) into a single `DurationParser` in `sb-common` with Russian+English support, then delete the duplicates.

**Architecture:** Replace both parsers with a single `DurationParser` object in `sb-common/utils`. Uses TimeSequenceParser's regex (supports years, months, weeks, days, hours, minutes, seconds, millis in Russian+English) but returns `Duration?` like the current moderation `DurationParser`. Keeps the `format()` method. Drops `parseFull`.

**Tech Stack:** Kotlin, `java.time.Duration`, JUnit 5, MockK

---

### Task 1: Create consolidated DurationParser in sb-common

**Files:**
- Create: `sb-common/src/main/kotlin/ru/sablebot/common/utils/DurationParser.kt`

**Step 1: Write the new DurationParser**

```kotlin
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
```

**Step 2: Verify it compiles**

Run: `./gradlew :sb-common:compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add sb-common/src/main/kotlin/ru/sablebot/common/utils/DurationParser.kt
git commit -m "feat: add consolidated DurationParser to sb-common with Russian+English support"
```

---

### Task 2: Write tests for consolidated DurationParser

**Files:**
- Create: `sb-common/src/test/kotlin/ru/sablebot/common/utils/DurationParserTest.kt`

**Step 1: Write the tests**

Adapt all tests from `TimeSequenceParserTest` to use `Duration?` return type, plus add `format()` tests. Key test cases:

```kotlin
package ru.sablebot.common.utils

import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DurationParserTest {

    // --- parse: English single units ---

    @Test
    fun `parse should handle single English units`() {
        assertEquals(Duration.ofSeconds(1), DurationParser.parse("1s"))
        assertEquals(Duration.ofMinutes(1), DurationParser.parse("1min"))
        assertEquals(Duration.ofMinutes(1), DurationParser.parse("1m"))
        assertEquals(Duration.ofHours(1), DurationParser.parse("1h"))
        assertEquals(Duration.ofDays(1), DurationParser.parse("1d"))
        assertEquals(Duration.ofDays(7), DurationParser.parse("1w"))
    }

    @Test
    fun `parse should handle alternative English forms`() {
        assertEquals(Duration.ofSeconds(1), DurationParser.parse("1sec"))
        assertEquals(Duration.ofSeconds(1), DurationParser.parse("1second"))
        assertEquals(Duration.ofSeconds(2), DurationParser.parse("2seconds"))
        assertEquals(Duration.ofMinutes(1), DurationParser.parse("1minute"))
        assertEquals(Duration.ofMinutes(2), DurationParser.parse("2minutes"))
        assertEquals(Duration.ofHours(1), DurationParser.parse("1hour"))
        assertEquals(Duration.ofHours(2), DurationParser.parse("2hours"))
        assertEquals(Duration.ofDays(1), DurationParser.parse("1day"))
        assertEquals(Duration.ofDays(2), DurationParser.parse("2days"))
        assertEquals(Duration.ofDays(7), DurationParser.parse("1week"))
        assertEquals(Duration.ofDays(14), DurationParser.parse("2weeks"))
    }

    // --- parse: English combinations ---

    @Test
    fun `parse should handle combined English units`() {
        assertEquals(Duration.ofMillis(61_000), DurationParser.parse("1min1s"))
        assertEquals(Duration.ofMillis(3_661_000), DurationParser.parse("1h1min1s"))
        assertEquals(Duration.ofMillis(90_061_000), DurationParser.parse("1d1h1min1s"))
        assertEquals(Duration.ofMillis(694_861_000), DurationParser.parse("1w1d1h1min1s"))
    }

    // --- parse: Russian units ---

    @Test
    fun `parse should handle Russian units`() {
        assertEquals(Duration.ofSeconds(1), DurationParser.parse("1с"))
        assertEquals(Duration.ofSeconds(1), DurationParser.parse("1c"))
        assertEquals(Duration.ofSeconds(1), DurationParser.parse("1сек"))
        assertEquals(Duration.ofMinutes(1), DurationParser.parse("1мин"))
        assertEquals(Duration.ofHours(1), DurationParser.parse("1ч"))
        assertEquals(Duration.ofHours(1), DurationParser.parse("1час"))
        assertEquals(Duration.ofDays(1), DurationParser.parse("1д"))
        assertEquals(Duration.ofDays(1), DurationParser.parse("1день"))
        assertEquals(Duration.ofDays(7), DurationParser.parse("1н"))
        assertEquals(Duration.ofDays(7), DurationParser.parse("1нед"))
        assertEquals(Duration.ofDays(7), DurationParser.parse("1неделя"))
    }

    @Test
    fun `parse should handle Russian month and year`() {
        assertEquals(Duration.ofDays(30), DurationParser.parse("1мес"))
        assertEquals(Duration.ofDays(30), DurationParser.parse("1месяц"))
        assertEquals(Duration.ofDays(60), DurationParser.parse("2месяца"))
        assertEquals(Duration.ofDays(365), DurationParser.parse("1г"))
        assertEquals(Duration.ofDays(365), DurationParser.parse("1год"))
        assertEquals(Duration.ofDays(730), DurationParser.parse("2года"))
    }

    // --- parse: case insensitive ---

    @Test
    fun `parse should be case insensitive`() {
        assertEquals(Duration.ofSeconds(1), DurationParser.parse("1S"))
        assertEquals(Duration.ofMinutes(1), DurationParser.parse("1MIN"))
        assertEquals(Duration.ofHours(1), DurationParser.parse("1H"))
        assertEquals(Duration.ofDays(1), DurationParser.parse("1D"))
        assertEquals(Duration.ofDays(7), DurationParser.parse("1W"))
        assertEquals(Duration.ofSeconds(1), DurationParser.parse("1С"))
        assertEquals(Duration.ofMinutes(1), DurationParser.parse("1МИН"))
    }

    // --- parse: invalid inputs return null ---

    @Test
    fun `parse should return null for invalid inputs`() {
        assertNull(DurationParser.parse(""))
        assertNull(DurationParser.parse("   "))
        assertNull(DurationParser.parse("invalid"))
        assertNull(DurationParser.parse("abc123"))
        assertNull(DurationParser.parse("1x"))
        assertNull(DurationParser.parse("123"))
        assertNull(DurationParser.parse("s"))
        assertNull(DurationParser.parse("0s"))
        assertNull(DurationParser.parse("0h0m0s"))
    }

    // --- parse: large values ---

    @Test
    fun `parse should handle large values`() {
        assertEquals(Duration.ofMillis(3_599_999_000), DurationParser.parse("999h59min59s"))
        assertEquals(Duration.ofSeconds(1_000_000), DurationParser.parse("1000000s"))
    }

    // --- parse: millis ---

    @Test
    fun `parse should handle milliseconds`() {
        assertEquals(Duration.ofMillis(500), DurationParser.parse("500ms"))
        assertEquals(Duration.ofMillis(1500), DurationParser.parse("1s500ms"))
    }

    // --- format ---

    @Test
    fun `format should produce human-readable output`() {
        assertEquals("1d", DurationParser.format(86_400_000))
        assertEquals("1d 2h", DurationParser.format(93_600_000))
        assertEquals("1d 2h 30m", DurationParser.format(95_400_000))
        assertEquals("3600s", DurationParser.format(3_600_000)) // exactly 1h but format shows s when no d/h/m? No — 1h = 0 days, 1 hour, 0 min → "1h"
    }

    @Test
    fun `format should show seconds when less than a minute`() {
        assertEquals("30s", DurationParser.format(30_000))
        assertEquals("0s", DurationParser.format(500))
    }

    @Test
    fun `format edge case hours only`() {
        assertEquals("1h", DurationParser.format(3_600_000))
        assertEquals("2h 30m", DurationParser.format(9_000_000))
    }
}
```

**Step 2: Run tests to verify they pass**

Run: `./gradlew :sb-common:test --tests "ru.sablebot.common.utils.DurationParserTest"`
Expected: All tests PASS

**Step 3: Commit**

```bash
git add sb-common/src/test/kotlin/ru/sablebot/common/utils/DurationParserTest.kt
git commit -m "test: add DurationParser tests covering English, Russian, format, edge cases"
```

---

### Task 3: Update moderation module imports

**Files:**
- Modify: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/command/AutoModCommand.kt:16`
- Modify: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/command/BanCommand.kt:14`
- Modify: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/command/CaseCommand.kt:15`
- Modify: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/command/TimeoutCommand.kt:14`
- Modify: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/service/impl/ModerationServiceImpl.kt:27`

**Step 1: In each file, replace the import**

```
// Old:
import ru.sablebot.module.moderation.model.DurationParser
// New:
import ru.sablebot.common.utils.DurationParser
```

No other code changes needed — the API is identical (`parse()` returns `Duration?`, `format()` takes `Long`).

**Step 2: Verify compilation**

Run: `./gradlew :sb-module-moderation:compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/command/AutoModCommand.kt
git add modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/command/BanCommand.kt
git add modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/command/CaseCommand.kt
git add modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/command/TimeoutCommand.kt
git add modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/service/impl/ModerationServiceImpl.kt
git commit -m "refactor: use consolidated DurationParser from sb-common in moderation module"
```

---

### Task 4: Delete old parsers

**Files:**
- Delete: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/model/DurationParser.kt`
- Delete: `sb-common/src/main/kotlin/ru/sablebot/common/utils/TimeSequenceParser.kt`
- Delete: `sb-common/src/test/kotlin/ru/sablebot/common/utils/TimeSequenceParserTest.kt`

**Step 1: Delete the files**

```bash
rm modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/model/DurationParser.kt
rm sb-common/src/main/kotlin/ru/sablebot/common/utils/TimeSequenceParser.kt
rm sb-common/src/test/kotlin/ru/sablebot/common/utils/TimeSequenceParserTest.kt
```

**Step 2: Full build to verify nothing is broken**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add -u
git commit -m "refactor: remove old DurationParser and TimeSequenceParser after consolidation"
```

---

### Regex note: `m` vs `min` ambiguity

The old moderation `DurationParser` used `m` for minutes. The old `TimeSequenceParser` used `min` (no bare `m`). The consolidated regex includes `m` in the minutes group (`min|mins|minute|minutes|...|m`) so existing moderation inputs like `30m` or `1h30m` continue to work. The `m` alternative is placed last in the group to avoid greedily matching before `ms`/`millis` — this works because `ms` is in a separate later group and the regex is non-overlapping by design.
