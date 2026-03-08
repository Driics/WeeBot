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
        assertEquals("1h", DurationParser.format(3_600_000))
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
