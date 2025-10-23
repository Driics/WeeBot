package ru.sablebot.common.persistence.repository

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.ContextConfiguration
import ru.sablebot.common.configuration.CommonConfiguration
import ru.sablebot.common.persistence.entity.Gulag
import ru.sablebot.common.persistence.entity.LocalUser

@DataJpaTest
@ContextConfiguration(classes = [CommonConfiguration::class])
class GulagRepositoryTest {

    @Autowired
    private lateinit var gulagRepository: GulagRepository

    @Autowired
    private lateinit var entityManager: TestEntityManager

    @Test
    fun `existsBySnowflake should return false when snowflake does not exist`() {
        // Given
        val snowflake = 123456789L

        // When
        val result = gulagRepository.existsBySnowflake(snowflake)

        // Then
        assertFalse(result)
    }

    @Test
    fun `existsBySnowflake should return true when snowflake exists`() {
        // Given
        val moderator = LocalUser().apply {
            userId = "999888777"
            name = "Moderator"
        }
        entityManager.persist(moderator)
        
        val snowflake = 123456789L
        val gulag = Gulag(
            snowflake = snowflake,
            reason = "Test reason",
            moderator = moderator
        )
        entityManager.persist(gulag)
        entityManager.flush()

        // When
        val result = gulagRepository.existsBySnowflake(snowflake)

        // Then
        assertTrue(result)
    }

    @Test
    fun `findBySnowflake should return null when snowflake does not exist`() {
        // Given
        val snowflake = 987654321L

        // When
        val result = gulagRepository.findBySnowflake(snowflake)

        // Then
        assertNull(result)
    }

    @Test
    fun `findBySnowflake should return gulag when snowflake exists`() {
        // Given
        val moderator = LocalUser().apply {
            userId = "111222333"
            name = "TestModerator"
        }
        entityManager.persist(moderator)
        
        val snowflake = 555666777L
        val reason = "Violation of terms"
        val gulag = Gulag(
            snowflake = snowflake,
            reason = reason,
            moderator = moderator
        )
        entityManager.persist(gulag)
        entityManager.flush()

        // When
        val result = gulagRepository.findBySnowflake(snowflake)

        // Then
        assertNotNull(result)
        assertEquals(snowflake, result?.snowflake)
        assertEquals(reason, result?.reason)
        assertEquals(moderator.userId, result?.moderator?.userId)
    }

    @Test
    fun `save should persist new gulag entity`() {
        // Given
        val moderator = LocalUser().apply {
            userId = "444555666"
            name = "SaveTestModerator"
        }
        entityManager.persist(moderator)
        entityManager.flush()
        
        val snowflake = 777888999L
        val reason = "Save test"
        val gulag = Gulag(
            snowflake = snowflake,
            reason = reason,
            moderator = moderator
        )

        // When
        val saved = gulagRepository.save(gulag)
        entityManager.flush()

        // Then
        assertNotNull(saved.id)
        assertEquals(snowflake, saved.snowflake)
        assertEquals(reason, saved.reason)
        
        // Verify it can be found
        val found = gulagRepository.findBySnowflake(snowflake)
        assertNotNull(found)
        assertEquals(saved.id, found?.id)
    }

    @Test
    fun `multiple gulags can be saved with different snowflakes`() {
        // Given
        val moderator = LocalUser().apply {
            userId = "321654987"
            name = "MultipleModerator"
        }
        entityManager.persist(moderator)
        
        val gulag1 = Gulag(snowflake = 100L, reason = "First", moderator = moderator)
        val gulag2 = Gulag(snowflake = 200L, reason = "Second", moderator = moderator)
        val gulag3 = Gulag(snowflake = 300L, reason = "Third", moderator = moderator)

        // When
        gulagRepository.save(gulag1)
        gulagRepository.save(gulag2)
        gulagRepository.save(gulag3)
        entityManager.flush()

        // Then
        assertTrue(gulagRepository.existsBySnowflake(100L))
        assertTrue(gulagRepository.existsBySnowflake(200L))
        assertTrue(gulagRepository.existsBySnowflake(300L))
        
        val all = gulagRepository.findAll()
        assertTrue(all.size >= 3)
    }

    @Test
    fun `findBySnowflake should return correct gulag among multiple`() {
        // Given
        val moderator = LocalUser().apply {
            userId = "147258369"
            name = "MultiSearchModerator"
        }
        entityManager.persist(moderator)
        
        gulagRepository.save(Gulag(snowflake = 1001L, reason = "First", moderator = moderator))
        gulagRepository.save(Gulag(snowflake = 1002L, reason = "Second", moderator = moderator))
        gulagRepository.save(Gulag(snowflake = 1003L, reason = "Third", moderator = moderator))
        entityManager.flush()

        // When
        val result = gulagRepository.findBySnowflake(1002L)

        // Then
        assertNotNull(result)
        assertEquals(1002L, result?.snowflake)
        assertEquals("Second", result?.reason)
    }

    @Test
    fun `gulag should have creation date set automatically`() {
        // Given
        val moderator = LocalUser().apply {
            userId = "987123654"
            name = "DateModerator"
        }
        entityManager.persist(moderator)
        
        val gulag = Gulag(
            snowflake = 2001L,
            reason = "Date test",
            moderator = moderator
        )

        // When
        val saved = gulagRepository.save(gulag)
        entityManager.flush()

        // Then
        assertNotNull(saved.date)
    }

    @Test
    fun `gulag with empty reason should be saved`() {
        // Given
        val moderator = LocalUser().apply {
            userId = "456789123"
            name = "EmptyReasonModerator"
        }
        entityManager.persist(moderator)
        
        val gulag = Gulag(
            snowflake = 3001L,
            reason = "",
            moderator = moderator
        )

        // When
        val saved = gulagRepository.save(gulag)
        entityManager.flush()

        // Then
        assertNotNull(saved.id)
        assertEquals("", saved.reason)
    }

    @Test
    fun `gulag with very long reason should be saved`() {
        // Given
        val moderator = LocalUser().apply {
            userId = "753951456"
            name = "LongReasonModerator"
        }
        entityManager.persist(moderator)
        
        val longReason = "A".repeat(5000)
        val gulag = Gulag(
            snowflake = 4001L,
            reason = longReason,
            moderator = moderator
        )

        // When
        val saved = gulagRepository.save(gulag)
        entityManager.flush()

        // Then
        assertNotNull(saved.id)
        assertEquals(longReason, saved.reason)
    }

    @Test
    fun `findAll should return all gulags`() {
        // Given
        val moderator = LocalUser().apply {
            userId = "159357258"
            name = "FindAllModerator"
        }
        entityManager.persist(moderator)
        
        val initialCount = gulagRepository.findAll().size
        
        gulagRepository.save(Gulag(snowflake = 5001L, reason = "A", moderator = moderator))
        gulagRepository.save(Gulag(snowflake = 5002L, reason = "B", moderator = moderator))
        entityManager.flush()

        // When
        val all = gulagRepository.findAll()

        // Then
        assertEquals(initialCount + 2, all.size)
    }

    @Test
    fun `gulag with zero snowflake should be saved`() {
        // Given
        val moderator = LocalUser().apply {
            userId = "951753852"
            name = "ZeroSnowflakeModerator"
        }
        entityManager.persist(moderator)
        
        val gulag = Gulag(
            snowflake = 0L,
            reason = "Zero test",
            moderator = moderator
        )

        // When
        val saved = gulagRepository.save(gulag)
        entityManager.flush()

        // Then
        assertNotNull(saved.id)
        assertEquals(0L, saved.snowflake)
        assertTrue(gulagRepository.existsBySnowflake(0L))
    }

    @Test
    fun `gulag with maximum long snowflake should be saved`() {
        // Given
        val moderator = LocalUser().apply {
            userId = "852741963"
            name = "MaxSnowflakeModerator"
        }
        entityManager.persist(moderator)
        
        val gulag = Gulag(
            snowflake = Long.MAX_VALUE,
            reason = "Max test",
            moderator = moderator
        )

        // When
        val saved = gulagRepository.save(gulag)
        entityManager.flush()

        // Then
        assertNotNull(saved.id)
        assertEquals(Long.MAX_VALUE, saved.snowflake)
        assertTrue(gulagRepository.existsBySnowflake(Long.MAX_VALUE))
    }
}