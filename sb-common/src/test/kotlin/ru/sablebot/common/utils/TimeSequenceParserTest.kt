package ru.sablebot.common.utils

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class TimeSequenceParserTest {

    @Test
    fun `parseShort should parse valid single time units correctly`() {
        // Test single units in English
        assertEquals(1000L, TimeSequenceParser.parseShort("1s"))
        assertEquals(60_000L, TimeSequenceParser.parseShort("1min"))
        assertEquals(3_600_000L, TimeSequenceParser.parseShort("1h"))
        assertEquals(86_400_000L, TimeSequenceParser.parseShort("1d"))
        assertEquals(604_800_000L, TimeSequenceParser.parseShort("1w"))

        // Test alternative English forms
        assertEquals(1000L, TimeSequenceParser.parseShort("1sec"))
        assertEquals(1000L, TimeSequenceParser.parseShort("1second"))
        assertEquals(2000L, TimeSequenceParser.parseShort("2seconds"))
        assertEquals(60_000L, TimeSequenceParser.parseShort("1minute"))
        assertEquals(120_000L, TimeSequenceParser.parseShort("2minutes"))
        assertEquals(3_600_000L, TimeSequenceParser.parseShort("1hour"))
        assertEquals(7_200_000L, TimeSequenceParser.parseShort("2hours"))
        assertEquals(86_400_000L, TimeSequenceParser.parseShort("1day"))
        assertEquals(172_800_000L, TimeSequenceParser.parseShort("2days"))
        assertEquals(604_800_000L, TimeSequenceParser.parseShort("1week"))
        assertEquals(1_209_600_000L, TimeSequenceParser.parseShort("2weeks"))
    }

    @Test
    fun `parseShort should parse valid multiple time units correctly`() {
        // Test combinations
        assertEquals(61_000L, TimeSequenceParser.parseShort("1min1s"))
        assertEquals(3_661_000L, TimeSequenceParser.parseShort("1h1min1s"))
        assertEquals(90_061_000L, TimeSequenceParser.parseShort("1d1h1min1s"))
        assertEquals(694_861_000L, TimeSequenceParser.parseShort("1w1d1h1min1s"))

        // Test with spaces (spaces are handled by the regex)
        assertEquals(7_260_000L, TimeSequenceParser.parseShort("2h1min"))
        assertEquals(90_000L, TimeSequenceParser.parseShort("1min30s"))

        // Test larger combinations
        // FIXME: org.opentest4j.AssertionFailedError: expected: <2678461000> but was: <3373261000>
        assertEquals(2_678_461_000L, TimeSequenceParser.parseShort("1mo1w1d1h1min1s"))
    }

    @Test
    fun `parseShort should parse Russian locale time strings correctly`() {
        // Test Russian time units
        assertEquals(1000L, TimeSequenceParser.parseShort("1с"))
        assertEquals(1000L, TimeSequenceParser.parseShort("1c"))
        assertEquals(1000L, TimeSequenceParser.parseShort("1сек"))
        assertEquals(1000L, TimeSequenceParser.parseShort("1секунда"))
        assertEquals(2000L, TimeSequenceParser.parseShort("2секунды"))
        assertEquals(5000L, TimeSequenceParser.parseShort("5секунд"))

        assertEquals(60_000L, TimeSequenceParser.parseShort("1мин"))
        assertEquals(60_000L, TimeSequenceParser.parseShort("1минута"))
        assertEquals(120_000L, TimeSequenceParser.parseShort("2минуты"))
        assertEquals(300_000L, TimeSequenceParser.parseShort("5минут"))

        assertEquals(3_600_000L, TimeSequenceParser.parseShort("1ч"))
        assertEquals(3_600_000L, TimeSequenceParser.parseShort("1час"))
        assertEquals(7_200_000L, TimeSequenceParser.parseShort("2часа"))
        assertEquals(18_000_000L, TimeSequenceParser.parseShort("5часов"))

        assertEquals(86_400_000L, TimeSequenceParser.parseShort("1д"))
        assertEquals(86_400_000L, TimeSequenceParser.parseShort("1день"))
        assertEquals(172_800_000L, TimeSequenceParser.parseShort("2дня"))
        assertEquals(432_000_000L, TimeSequenceParser.parseShort("5дней"))

        assertEquals(604_800_000L, TimeSequenceParser.parseShort("1н"))
        assertEquals(604_800_000L, TimeSequenceParser.parseShort("1нед"))
        assertEquals(604_800_000L, TimeSequenceParser.parseShort("1неделя"))
        assertEquals(1_209_600_000L, TimeSequenceParser.parseShort("2недели"))
        assertEquals(3_024_000_000L, TimeSequenceParser.parseShort("5недель"))
        assertEquals(604_800_000L, TimeSequenceParser.parseShort("1неделю"))

        assertEquals(2_678_400_000L, TimeSequenceParser.parseShort("1мес"))

        // FIXME: org.opentest4j.AssertionFailedError: expected: <5356800000> but was: <5097600000>
        assertEquals(2_678_400_000L, TimeSequenceParser.parseShort("1месяц"))
        assertEquals(5_356_800_000L, TimeSequenceParser.parseShort("2месяца"))
        assertEquals(13_392_000_000L, TimeSequenceParser.parseShort("5месяцев"))

        assertEquals(31_536_000_000L, TimeSequenceParser.parseShort("1г"))
        assertEquals(31_536_000_000L, TimeSequenceParser.parseShort("1год"))
        assertEquals(63_072_000_000L, TimeSequenceParser.parseShort("2года"))
        assertEquals(157_680_000_000L, TimeSequenceParser.parseShort("5лет"))
    }

    @Test
    fun `parseShort should handle case insensitive parsing correctly`() {
        // Test uppercase
        assertEquals(1000L, TimeSequenceParser.parseShort("1S"))
        assertEquals(60_000L, TimeSequenceParser.parseShort("1MIN"))
        assertEquals(3_600_000L, TimeSequenceParser.parseShort("1H"))
        assertEquals(86_400_000L, TimeSequenceParser.parseShort("1D"))
        assertEquals(604_800_000L, TimeSequenceParser.parseShort("1W"))

        // Test mixed case
        assertEquals(61_000L, TimeSequenceParser.parseShort("1Min1S"))
        assertEquals(3_661_000L, TimeSequenceParser.parseShort("1H1Min1S"))

        // Test Russian uppercase
        assertEquals(1000L, TimeSequenceParser.parseShort("1С"))
        assertEquals(60_000L, TimeSequenceParser.parseShort("1МИН"))
        assertEquals(3_600_000L, TimeSequenceParser.parseShort("1Ч"))
        assertEquals(86_400_000L, TimeSequenceParser.parseShort("1Д"))

        // Test full words with different cases
        assertEquals(1000L, TimeSequenceParser.parseShort("1SECOND"))
        assertEquals(60_000L, TimeSequenceParser.parseShort("1Minute"))
        assertEquals(3_600_000L, TimeSequenceParser.parseShort("1Hour"))
        assertEquals(86_400_000L, TimeSequenceParser.parseShort("1Day"))
        assertEquals(604_800_000L, TimeSequenceParser.parseShort("1Week"))
    }

    @Test
    fun `parseShort should handle zero value inputs correctly`() {
        // Zero values should result in 0 milliseconds
        assertEquals(0L, TimeSequenceParser.parseShort("0s"))
        assertEquals(0L, TimeSequenceParser.parseShort("0min"))
        assertEquals(0L, TimeSequenceParser.parseShort("0h"))
        assertEquals(0L, TimeSequenceParser.parseShort("0d"))
        assertEquals(0L, TimeSequenceParser.parseShort("0w"))

        // Combinations with zeros
        assertEquals(1000L, TimeSequenceParser.parseShort("0min1s"))
        assertEquals(60_000L, TimeSequenceParser.parseShort("1min0s"))
        assertEquals(0L, TimeSequenceParser.parseShort("0h0min0s"))

        // Russian zeros
        assertEquals(0L, TimeSequenceParser.parseShort("0с"))
        assertEquals(0L, TimeSequenceParser.parseShort("0мин"))
        assertEquals(0L, TimeSequenceParser.parseShort("0ч"))
    }

    @Test
    fun `parseShort should handle large number values correctly`() {
        // Test large numbers
        assertEquals(999_000L, TimeSequenceParser.parseShort("999s"))
        assertEquals(59_940_000L, TimeSequenceParser.parseShort("999min"))
        assertEquals(3_596_400_000L, TimeSequenceParser.parseShort("999h"))
        assertEquals(86_313_600_000L, TimeSequenceParser.parseShort("999d"))
        assertEquals(604_195_200_000L, TimeSequenceParser.parseShort("999w"))

        // Test very large numbers
        // FIXME: org.opentest4j.AssertionFailedError: expected: <31536000000> but was: <1000000000>
        assertEquals(31_536_000_000L, TimeSequenceParser.parseShort("1000000s"))
        assertEquals(60_000_000L, TimeSequenceParser.parseShort("1000min"))

        // Test combinations with large numbers
        assertEquals(3_659_999_000L, TimeSequenceParser.parseShort("999h59min59s"))
    }

    @Test
    fun `parseShort should throw IllegalArgumentException for empty string input`() {
        val exception = assertThrows<IllegalArgumentException> {
            TimeSequenceParser.parseShort("")
        }
        assertEquals("Incorrect period/duration: ", exception.message)
    }

    @Test
    fun `parseShort should throw IllegalArgumentException for invalid format strings`() {
        // Test completely invalid strings
        assertThrows<IllegalArgumentException> {
            TimeSequenceParser.parseShort("invalid")
        }

        assertThrows<IllegalArgumentException> {
            TimeSequenceParser.parseShort("abc123")
        }

        assertThrows<IllegalArgumentException> {
            TimeSequenceParser.parseShort("1x")
        }

        assertThrows<IllegalArgumentException> {
            TimeSequenceParser.parseShort("1 invalid")
        }

        // Test numbers without units
        assertThrows<IllegalArgumentException> {
            TimeSequenceParser.parseShort("123")
        }

        // Test units without numbers
        assertThrows<IllegalArgumentException> {
            TimeSequenceParser.parseShort("s")
        }

        assertThrows<IllegalArgumentException> {
            TimeSequenceParser.parseShort("min")
        }

        // Test invalid combinations
        assertThrows<IllegalArgumentException> {
            TimeSequenceParser.parseShort("1s2x")
        }

        assertThrows<IllegalArgumentException> {
            TimeSequenceParser.parseShort("1s invalid 2min")
        }

        // Test whitespace only
        assertThrows<IllegalArgumentException> {
            TimeSequenceParser.parseShort("   ")
        }

        // Test special characters
        assertThrows<IllegalArgumentException> {
            TimeSequenceParser.parseShort("1s!")
        }

        assertThrows<IllegalArgumentException> {
            TimeSequenceParser.parseShort("@1s")
        }
    }
}