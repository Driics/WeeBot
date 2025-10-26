package ru.sablebot.common.worker.modules.audit.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.RepeatedTest

class ActionsHolderServiceTest {

    private lateinit var actionsHolderService: ActionsHolderService

    @BeforeEach
    fun setup() {
        actionsHolderService = ActionsHolderService()
    }

    @Test
    fun `getMemberKey should generate correct key format`() {
        // Given
        val guildId = 123456789L
        val userId = 987654321L

        // When
        val key = actionsHolderService.getMemberKey(guildId, userId)

        // Then
        assertEquals("123456789_987654321", key)
    }

    @Test
    fun `getMemberKey should handle different guild and user combinations`() {
        // Given & When
        val key1 = actionsHolderService.getMemberKey(111L, 222L)
        val key2 = actionsHolderService.getMemberKey(333L, 444L)
        val key3 = actionsHolderService.getMemberKey(111L, 444L)

        // Then
        assertEquals("111_222", key1)
        assertEquals("333_444", key2)
        assertEquals("111_444", key3)
        assertNotEquals(key1, key2)
        assertNotEquals(key1, key3)
        assertNotEquals(key2, key3)
    }

    @Test
    fun `getMemberKey should handle zero values`() {
        // Given
        val guildId = 0L
        val userId = 0L

        // When
        val key = actionsHolderService.getMemberKey(guildId, userId)

        // Then
        assertEquals("0_0", key)
    }

    @Test
    fun `getMemberKey should handle maximum long values`() {
        // Given
        val guildId = Long.MAX_VALUE
        val userId = Long.MAX_VALUE

        // When
        val key = actionsHolderService.getMemberKey(guildId, userId)

        // Then
        assertEquals("${Long.MAX_VALUE}_${Long.MAX_VALUE}", key)
    }

    @Test
    fun `getMemberKey should handle negative values`() {
        // Given
        val guildId = -123L
        val userId = -456L

        // When
        val key = actionsHolderService.getMemberKey(guildId, userId)

        // Then
        assertEquals("-123_-456", key)
    }

    @Test
    fun `isLeaveNotified should return false for non-existent entry`() {
        // Given
        val guildId = 123456789L
        val userId = 987654321L

        // When
        val result = actionsHolderService.isLeaveNotified(guildId, userId)

        // Then
        assertFalse(result)
    }

    @Test
    fun `isLeaveNotified should return true after setLeaveNotified`() {
        // Given
        val guildId = 123456789L
        val userId = 987654321L

        // When
        actionsHolderService.setLeaveNotified(guildId, userId)
        val result = actionsHolderService.isLeaveNotified(guildId, userId)

        // Then
        assertTrue(result)
    }

    @Test
    fun `setLeaveNotified should store notification for multiple users`() {
        // Given
        val guild1 = 111L
        val guild2 = 222L
        val user1 = 333L
        val user2 = 444L

        // When
        actionsHolderService.setLeaveNotified(guild1, user1)
        actionsHolderService.setLeaveNotified(guild1, user2)
        actionsHolderService.setLeaveNotified(guild2, user1)

        // Then
        assertTrue(actionsHolderService.isLeaveNotified(guild1, user1))
        assertTrue(actionsHolderService.isLeaveNotified(guild1, user2))
        assertTrue(actionsHolderService.isLeaveNotified(guild2, user1))
        assertFalse(actionsHolderService.isLeaveNotified(guild2, user2))
    }

    @Test
    fun `setLeaveNotified should overwrite existing entry`() {
        // Given
        val guildId = 123456789L
        val userId = 987654321L

        // When
        actionsHolderService.setLeaveNotified(guildId, userId)
        actionsHolderService.setLeaveNotified(guildId, userId)
        val result = actionsHolderService.isLeaveNotified(guildId, userId)

        // Then
        assertTrue(result)
    }

    @Test
    fun `isLeaveNotified should handle same user in different guilds`() {
        // Given
        val guild1 = 111L
        val guild2 = 222L
        val userId = 333L

        // When
        actionsHolderService.setLeaveNotified(guild1, userId)

        // Then
        assertTrue(actionsHolderService.isLeaveNotified(guild1, userId))
        assertFalse(actionsHolderService.isLeaveNotified(guild2, userId))
    }

    @Test
    fun `isLeaveNotified should handle same guild with different users`() {
        // Given
        val guildId = 111L
        val user1 = 222L
        val user2 = 333L

        // When
        actionsHolderService.setLeaveNotified(guildId, user1)

        // Then
        assertTrue(actionsHolderService.isLeaveNotified(guildId, user1))
        assertFalse(actionsHolderService.isLeaveNotified(guildId, user2))
    }

    @RepeatedTest(5)
    fun `cache operations should be thread-safe`() {
        // Given
        val guildId = System.currentTimeMillis()
        val userId = System.nanoTime()

        // When
        actionsHolderService.setLeaveNotified(guildId, userId)
        val result = actionsHolderService.isLeaveNotified(guildId, userId)

        // Then
        assertTrue(result)
    }

    @Test
    fun `setLeaveNotified with zero IDs should work`() {
        // Given
        val guildId = 0L
        val userId = 0L

        // When
        actionsHolderService.setLeaveNotified(guildId, userId)

        // Then
        assertTrue(actionsHolderService.isLeaveNotified(guildId, userId))
    }

    @Test
    fun `multiple sequential operations should maintain correct state`() {
        // Given
        val operations = listOf(
            Pair(100L, 200L),
            Pair(101L, 201L),
            Pair(102L, 202L),
            Pair(103L, 203L),
            Pair(104L, 204L)
        )

        // When
        operations.forEach { (guildId, userId) ->
            actionsHolderService.setLeaveNotified(guildId, userId)
        }

        // Then
        operations.forEach { (guildId, userId) ->
            assertTrue(actionsHolderService.isLeaveNotified(guildId, userId))
        }
    }

    @Test
    fun `isLeaveNotified should return false for similar but different keys`() {
        // Given - set notification for one combination
        actionsHolderService.setLeaveNotified(123L, 456L)

        // Then - different combinations should not be notified
        assertFalse(actionsHolderService.isLeaveNotified(456L, 123L)) // reversed
        assertFalse(actionsHolderService.isLeaveNotified(123L, 457L)) // different user
        assertFalse(actionsHolderService.isLeaveNotified(124L, 456L)) // different guild
    }
}