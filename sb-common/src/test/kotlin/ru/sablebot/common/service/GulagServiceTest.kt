package ru.sablebot.common.service

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import ru.sablebot.common.persistence.entity.Gulag
import ru.sablebot.common.persistence.entity.LocalUser
import ru.sablebot.common.persistence.repository.GulagRepository

class GulagServiceTest {

    private lateinit var gulagRepository: GulagRepository
    private lateinit var userService: UserService
    private lateinit var gulagService: GulagService

    @BeforeEach
    fun setup() {
        gulagRepository = mock(GulagRepository::class.java)
        userService = mock(UserService::class.java)
        gulagService = GulagService(gulagRepository, userService)
    }

    @Test
    fun `send should return false when snowflake already exists`() {
        // Given
        val moderator = mock(Member::class.java)
        val snowflake = 123456789L
        val reason = "Test reason"
        `when`(gulagRepository.existsBySnowflake(snowflake)).thenReturn(true)

        // When
        val result = gulagService.send(moderator, snowflake, reason)

        // Then
        assertFalse(result)
        verify(gulagRepository).existsBySnowflake(snowflake)
        verify(gulagRepository, never()).save(any(Gulag::class.java))
    }

    @Test
    fun `send should return false when user service returns null`() {
        // Given
        val moderator = mock(Member::class.java)
        val moderatorUser = mock(User::class.java)
        `when`(moderator.user).thenReturn(moderatorUser)
        val snowflake = 123456789L
        val reason = "Test reason"
        `when`(gulagRepository.existsBySnowflake(snowflake)).thenReturn(false)
        `when`(userService.get(moderatorUser)).thenReturn(null)

        // When
        val result = gulagService.send(moderator, snowflake, reason)

        // Then
        assertFalse(result)
        verify(gulagRepository).existsBySnowflake(snowflake)
        verify(userService).get(moderatorUser)
        verify(gulagRepository, never()).save(any(Gulag::class.java))
    }

    @Test
    fun `send should save gulag and return true when conditions are met`() {
        // Given
        val moderator = mock(Member::class.java)
        val moderatorUser = mock(User::class.java)
        `when`(moderator.user).thenReturn(moderatorUser)
        val snowflake = 123456789L
        val reason = "Violation of rules"
        val localUser = LocalUser()
        `when`(gulagRepository.existsBySnowflake(snowflake)).thenReturn(false)
        `when`(userService.get(moderatorUser)).thenReturn(localUser)
        
        val captor = ArgumentCaptor.forClass(Gulag::class.java)
        `when`(gulagRepository.save(captor.capture())).thenAnswer { it.arguments[0] }

        // When
        val result = gulagService.send(moderator, snowflake, reason)

        // Then
        assertTrue(result)
        verify(gulagRepository).existsBySnowflake(snowflake)
        verify(userService).get(moderatorUser)
        verify(gulagRepository).save(any(Gulag::class.java))
        
        val savedGulag = captor.value
        assertEquals(snowflake, savedGulag.snowflake)
        assertEquals(reason, savedGulag.reason)
        assertEquals(localUser, savedGulag.moderator)
    }

    @Test
    fun `send with Member parameter should delegate to snowflake version`() {
        // Given
        val moderator = mock(Member::class.java)
        val member = mock(Member::class.java)
        val moderatorUser = mock(User::class.java)
        val memberIdLong = 987654321L
        `when`(moderator.user).thenReturn(moderatorUser)
        `when`(member.idLong).thenReturn(memberIdLong)
        val reason = "Test"
        val localUser = LocalUser()
        `when`(gulagRepository.existsBySnowflake(memberIdLong)).thenReturn(false)
        `when`(userService.get(moderatorUser)).thenReturn(localUser)
        
        val captor = ArgumentCaptor.forClass(Gulag::class.java)
        `when`(gulagRepository.save(captor.capture())).thenAnswer { it.arguments[0] }

        // When
        val result = gulagService.send(moderator, member, reason)

        // Then
        assertTrue(result)
        verify(member).idLong
        val savedGulag = captor.value
        assertEquals(memberIdLong, savedGulag.snowflake)
    }

    @Test
    fun `getGulag for Guild should return gulag by owner ID first`() {
        // Given
        val guild = mock(Guild::class.java)
        val ownerIdLong = 111222333L
        val guildIdLong = 444555666L
        `when`(guild.ownerIdLong).thenReturn(ownerIdLong)
        `when`(guild.idLong).thenReturn(guildIdLong)
        val expectedGulag = Gulag(snowflake = ownerIdLong, reason = "Owner gulag", moderator = LocalUser())
        `when`(gulagRepository.findBySnowflake(ownerIdLong)).thenReturn(expectedGulag)

        // When
        val result = gulagService.getGulag(guild)

        // Then
        assertNotNull(result)
        assertEquals(expectedGulag, result)
        verify(gulagRepository).findBySnowflake(ownerIdLong)
        verify(gulagRepository, never()).findBySnowflake(guildIdLong)
    }

    @Test
    fun `getGulag for Guild should fallback to guild ID when owner not gulaged`() {
        // Given
        val guild = mock(Guild::class.java)
        val ownerIdLong = 111222333L
        val guildIdLong = 444555666L
        `when`(guild.ownerIdLong).thenReturn(ownerIdLong)
        `when`(guild.idLong).thenReturn(guildIdLong)
        val expectedGulag = Gulag(snowflake = guildIdLong, reason = "Guild gulag", moderator = LocalUser())
        `when`(gulagRepository.findBySnowflake(ownerIdLong)).thenReturn(null)
        `when`(gulagRepository.findBySnowflake(guildIdLong)).thenReturn(expectedGulag)

        // When
        val result = gulagService.getGulag(guild)

        // Then
        assertNotNull(result)
        assertEquals(expectedGulag, result)
        verify(gulagRepository).findBySnowflake(ownerIdLong)
        verify(gulagRepository).findBySnowflake(guildIdLong)
    }

    @Test
    fun `getGulag for Guild should return null when neither owner nor guild is gulaged`() {
        // Given
        val guild = mock(Guild::class.java)
        val ownerIdLong = 111222333L
        val guildIdLong = 444555666L
        `when`(guild.ownerIdLong).thenReturn(ownerIdLong)
        `when`(guild.idLong).thenReturn(guildIdLong)
        `when`(gulagRepository.findBySnowflake(ownerIdLong)).thenReturn(null)
        `when`(gulagRepository.findBySnowflake(guildIdLong)).thenReturn(null)

        // When
        val result = gulagService.getGulag(guild)

        // Then
        assertNull(result)
        verify(gulagRepository).findBySnowflake(ownerIdLong)
        verify(gulagRepository).findBySnowflake(guildIdLong)
    }

    @Test
    fun `getGulag for User should return gulag by user ID`() {
        // Given
        val user = mock(User::class.java)
        val userIdLong = 777888999L
        `when`(user.idLong).thenReturn(userIdLong)
        val expectedGulag = Gulag(snowflake = userIdLong, reason = "User gulag", moderator = LocalUser())
        `when`(gulagRepository.findBySnowflake(userIdLong)).thenReturn(expectedGulag)

        // When
        val result = gulagService.getGulag(user)

        // Then
        assertNotNull(result)
        assertEquals(expectedGulag, result)
        verify(gulagRepository).findBySnowflake(userIdLong)
    }

    @Test
    fun `getGulag for User should return null when user not gulaged`() {
        // Given
        val user = mock(User::class.java)
        val userIdLong = 777888999L
        `when`(user.idLong).thenReturn(userIdLong)
        `when`(gulagRepository.findBySnowflake(userIdLong)).thenReturn(null)

        // When
        val result = gulagService.getGulag(user)

        // Then
        assertNull(result)
        verify(gulagRepository).findBySnowflake(userIdLong)
    }

    @Test
    fun `send should handle empty reason`() {
        // Given
        val moderator = mock(Member::class.java)
        val moderatorUser = mock(User::class.java)
        `when`(moderator.user).thenReturn(moderatorUser)
        val snowflake = 123L
        val reason = ""
        val localUser = LocalUser()
        `when`(gulagRepository.existsBySnowflake(snowflake)).thenReturn(false)
        `when`(userService.get(moderatorUser)).thenReturn(localUser)
        
        val captor = ArgumentCaptor.forClass(Gulag::class.java)
        `when`(gulagRepository.save(captor.capture())).thenAnswer { it.arguments[0] }

        // When
        val result = gulagService.send(moderator, snowflake, reason)

        // Then
        assertTrue(result)
        val savedGulag = captor.value
        assertEquals("", savedGulag.reason)
    }

    @Test
    fun `send should handle very long reason`() {
        // Given
        val moderator = mock(Member::class.java)
        val moderatorUser = mock(User::class.java)
        `when`(moderator.user).thenReturn(moderatorUser)
        val snowflake = 123L
        val reason = "A".repeat(10000)
        val localUser = LocalUser()
        `when`(gulagRepository.existsBySnowflake(snowflake)).thenReturn(false)
        `when`(userService.get(moderatorUser)).thenReturn(localUser)
        
        val captor = ArgumentCaptor.forClass(Gulag::class.java)
        `when`(gulagRepository.save(captor.capture())).thenAnswer { it.arguments[0] }

        // When
        val result = gulagService.send(moderator, snowflake, reason)

        // Then
        assertTrue(result)
        val savedGulag = captor.value
        assertEquals(reason, savedGulag.reason)
    }

    @Test
    fun `send should handle special characters in reason`() {
        // Given
        val moderator = mock(Member::class.java)
        val moderatorUser = mock(User::class.java)
        `when`(moderator.user).thenReturn(moderatorUser)
        val snowflake = 123L
        val reason = "Reason with <special> & characters 日本語"
        val localUser = LocalUser()
        `when`(gulagRepository.existsBySnowflake(snowflake)).thenReturn(false)
        `when`(userService.get(moderatorUser)).thenReturn(localUser)
        
        val captor = ArgumentCaptor.forClass(Gulag::class.java)
        `when`(gulagRepository.save(captor.capture())).thenAnswer { it.arguments[0] }

        // When
        val result = gulagService.send(moderator, snowflake, reason)

        // Then
        assertTrue(result)
        val savedGulag = captor.value
        assertEquals(reason, savedGulag.reason)
    }

    @Test
    fun `send should handle zero snowflake`() {
        // Given
        val moderator = mock(Member::class.java)
        val moderatorUser = mock(User::class.java)
        `when`(moderator.user).thenReturn(moderatorUser)
        val snowflake = 0L
        val reason = "Test"
        val localUser = LocalUser()
        `when`(gulagRepository.existsBySnowflake(snowflake)).thenReturn(false)
        `when`(userService.get(moderatorUser)).thenReturn(localUser)
        
        val captor = ArgumentCaptor.forClass(Gulag::class.java)
        `when`(gulagRepository.save(captor.capture())).thenAnswer { it.arguments[0] }

        // When
        val result = gulagService.send(moderator, snowflake, reason)

        // Then
        assertTrue(result)
        val savedGulag = captor.value
        assertEquals(0L, savedGulag.snowflake)
    }

    @Test
    fun `send should handle maximum long snowflake`() {
        // Given
        val moderator = mock(Member::class.java)
        val moderatorUser = mock(User::class.java)
        `when`(moderator.user).thenReturn(moderatorUser)
        val snowflake = Long.MAX_VALUE
        val reason = "Test"
        val localUser = LocalUser()
        `when`(gulagRepository.existsBySnowflake(snowflake)).thenReturn(false)
        `when`(userService.get(moderatorUser)).thenReturn(localUser)
        
        val captor = ArgumentCaptor.forClass(Gulag::class.java)
        `when`(gulagRepository.save(captor.capture())).thenAnswer { it.arguments[0] }

        // When
        val result = gulagService.send(moderator, snowflake, reason)

        // Then
        assertTrue(result)
        val savedGulag = captor.value
        assertEquals(Long.MAX_VALUE, savedGulag.snowflake)
    }
}