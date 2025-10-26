package ru.sablebot.common.worker.modules.audit.provider

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.sablebot.common.model.AuditActionType
import ru.sablebot.common.persistence.entity.AuditAction
import ru.sablebot.common.persistence.entity.base.NamedReference

class NicknameChangeAuditForwardProviderTest {

    private lateinit var provider: NicknameChangeAuditForwardProvider

    @BeforeEach
    fun setup() {
        provider = NicknameChangeAuditForwardProvider()
    }

    @Test
    fun `build should add old nickname field`() {
        // Given
        val action = AuditAction(
            guildId = 123456789L,
            actionType = AuditActionType.MEMBER_NAME_CHANGE
        ).apply {
            attributes[NicknameChangeAuditForwardProvider.OLD_NAME] = "OldNick"
            attributes[NicknameChangeAuditForwardProvider.NEW_NAME] = "NewNick"
            user = NamedReference("123", "TestUser")
        }
        val messageBuilder = MessageCreateBuilder()
        val embedBuilder = EmbedBuilder()

        // When
        provider.build(action, messageBuilder, embedBuilder)

        // Then
        val embed = embedBuilder.build()
        val fields = embed.fields
        assertTrue(fields.any { it.name == "Old Nickname" && it.value == "OldNick" })
    }

    @Test
    fun `build should add new nickname field`() {
        // Given
        val action = AuditAction(
            guildId = 123456789L,
            actionType = AuditActionType.MEMBER_NAME_CHANGE
        ).apply {
            attributes[NicknameChangeAuditForwardProvider.OLD_NAME] = "OldNick"
            attributes[NicknameChangeAuditForwardProvider.NEW_NAME] = "NewNick"
            user = NamedReference("456", "TestUser")
        }
        val messageBuilder = MessageCreateBuilder()
        val embedBuilder = EmbedBuilder()

        // When
        provider.build(action, messageBuilder, embedBuilder)

        // Then
        val embed = embedBuilder.build()
        val fields = embed.fields
        assertTrue(fields.any { it.name == "New Nickname" && it.value == "NewNick" })
    }

    @Test
    fun `build should show dash when old name is null`() {
        // Given
        val action = AuditAction(
            guildId = 123456789L,
            actionType = AuditActionType.MEMBER_NAME_CHANGE
        ).apply {
            attributes[NicknameChangeAuditForwardProvider.NEW_NAME] = "NewNick"
            user = NamedReference("789", "TestUser")
        }
        val messageBuilder = MessageCreateBuilder()
        val embedBuilder = EmbedBuilder()

        // When
        provider.build(action, messageBuilder, embedBuilder)

        // Then
        val embed = embedBuilder.build()
        val fields = embed.fields
        assertTrue(fields.any { it.name == "Old Nickname" && it.value == "-" })
    }

    @Test
    fun `build should show dash when new name is null`() {
        // Given
        val action = AuditAction(
            guildId = 123456789L,
            actionType = AuditActionType.MEMBER_NAME_CHANGE
        ).apply {
            attributes[NicknameChangeAuditForwardProvider.OLD_NAME] = "OldNick"
            user = NamedReference("321", "TestUser")
        }
        val messageBuilder = MessageCreateBuilder()
        val embedBuilder = EmbedBuilder()

        // When
        provider.build(action, messageBuilder, embedBuilder)

        // Then
        val embed = embedBuilder.build()
        val fields = embed.fields
        assertTrue(fields.any { it.name == "New Nickname" && it.value == "-" })
    }

    @Test
    fun `build should show dash for both fields when both names are null`() {
        // Given
        val action = AuditAction(
            guildId = 123456789L,
            actionType = AuditActionType.MEMBER_NAME_CHANGE
        ).apply {
            user = NamedReference("654", "TestUser")
        }
        val messageBuilder = MessageCreateBuilder()
        val embedBuilder = EmbedBuilder()

        // When
        provider.build(action, messageBuilder, embedBuilder)

        // Then
        val embed = embedBuilder.build()
        val fields = embed.fields
        assertEquals(2, fields.size)
        assertTrue(fields.any { it.name == "Old Nickname" && it.value == "-" })
        assertTrue(fields.any { it.name == "New Nickname" && it.value == "-" })
    }

    @Test
    fun `build should set description with member ID`() {
        // Given
        val userId = "987654321"
        val action = AuditAction(
            guildId = 123456789L,
            actionType = AuditActionType.MEMBER_NAME_CHANGE
        ).apply {
            attributes[NicknameChangeAuditForwardProvider.OLD_NAME] = "Old"
            attributes[NicknameChangeAuditForwardProvider.NEW_NAME] = "New"
            user = NamedReference(userId, "TestUser")
        }
        val messageBuilder = MessageCreateBuilder()
        val embedBuilder = EmbedBuilder()

        // When
        provider.build(action, messageBuilder, embedBuilder)

        // Then
        val embed = embedBuilder.build()
        assertEquals("Member ID: $userId", embed.description)
    }

    @Test
    fun `build should mark fields as inline`() {
        // Given
        val action = AuditAction(
            guildId = 123456789L,
            actionType = AuditActionType.MEMBER_NAME_CHANGE
        ).apply {
            attributes[NicknameChangeAuditForwardProvider.OLD_NAME] = "OldNick"
            attributes[NicknameChangeAuditForwardProvider.NEW_NAME] = "NewNick"
            user = NamedReference("111", "TestUser")
        }
        val messageBuilder = MessageCreateBuilder()
        val embedBuilder = EmbedBuilder()

        // When
        provider.build(action, messageBuilder, embedBuilder)

        // Then
        val embed = embedBuilder.build()
        val fields = embed.fields
        assertEquals(2, fields.size)
        assertTrue(fields.all { it.isInline })
    }

    @Test
    fun `build should handle empty string nicknames`() {
        // Given
        val action = AuditAction(
            guildId = 123456789L,
            actionType = AuditActionType.MEMBER_NAME_CHANGE
        ).apply {
            attributes[NicknameChangeAuditForwardProvider.OLD_NAME] = ""
            attributes[NicknameChangeAuditForwardProvider.NEW_NAME] = ""
            user = NamedReference("222", "TestUser")
        }
        val messageBuilder = MessageCreateBuilder()
        val embedBuilder = EmbedBuilder()

        // When
        provider.build(action, messageBuilder, embedBuilder)

        // Then
        val embed = embedBuilder.build()
        val fields = embed.fields
        assertEquals(2, fields.size)
    }

    @Test
    fun `build should handle special characters in nicknames`() {
        // Given
        val oldName = "User!@#$%"
        val newName = "New<User>123"
        val action = AuditAction(
            guildId = 123456789L,
            actionType = AuditActionType.MEMBER_NAME_CHANGE
        ).apply {
            attributes[NicknameChangeAuditForwardProvider.OLD_NAME] = oldName
            attributes[NicknameChangeAuditForwardProvider.NEW_NAME] = newName
            user = NamedReference("333", "TestUser")
        }
        val messageBuilder = MessageCreateBuilder()
        val embedBuilder = EmbedBuilder()

        // When
        provider.build(action, messageBuilder, embedBuilder)

        // Then
        val embed = embedBuilder.build()
        val fields = embed.fields
        assertTrue(fields.any { it.name == "Old Nickname" && it.value == oldName })
        assertTrue(fields.any { it.name == "New Nickname" && it.value == newName })
    }

    @Test
    fun `build should handle very long nicknames`() {
        // Given
        val longName = "A".repeat(100)
        val action = AuditAction(
            guildId = 123456789L,
            actionType = AuditActionType.MEMBER_NAME_CHANGE
        ).apply {
            attributes[NicknameChangeAuditForwardProvider.OLD_NAME] = longName
            attributes[NicknameChangeAuditForwardProvider.NEW_NAME] = longName
            user = NamedReference("444", "TestUser")
        }
        val messageBuilder = MessageCreateBuilder()
        val embedBuilder = EmbedBuilder()

        // When
        provider.build(action, messageBuilder, embedBuilder)

        // Then
        val embed = embedBuilder.build()
        val fields = embed.fields
        assertTrue(fields.any { it.value == longName })
    }

    @Test
    fun `build should handle unicode characters in nicknames`() {
        // Given
        val oldName = "ユーザー"
        val newName = "用户名"
        val action = AuditAction(
            guildId = 123456789L,
            actionType = AuditActionType.MEMBER_NAME_CHANGE
        ).apply {
            attributes[NicknameChangeAuditForwardProvider.OLD_NAME] = oldName
            attributes[NicknameChangeAuditForwardProvider.NEW_NAME] = newName
            user = NamedReference("555", "TestUser")
        }
        val messageBuilder = MessageCreateBuilder()
        val embedBuilder = EmbedBuilder()

        // When
        provider.build(action, messageBuilder, embedBuilder)

        // Then
        val embed = embedBuilder.build()
        val fields = embed.fields
        assertTrue(fields.any { it.value == oldName })
        assertTrue(fields.any { it.value == newName })
    }

    @Test
    fun `build should not modify message builder`() {
        // Given
        val action = AuditAction(
            guildId = 123456789L,
            actionType = AuditActionType.MEMBER_NAME_CHANGE
        ).apply {
            attributes[NicknameChangeAuditForwardProvider.OLD_NAME] = "Old"
            attributes[NicknameChangeAuditForwardProvider.NEW_NAME] = "New"
            user = NamedReference("666", "TestUser")
        }
        val messageBuilder = MessageCreateBuilder()
        val embedBuilder = EmbedBuilder()

        // When
        provider.build(action, messageBuilder, embedBuilder)

        // Then
        assertTrue(messageBuilder.isEmpty)
    }

    @Test
    fun `class should have ForwardProvider annotation`() {
        // When
        val annotation = provider::class.java.getAnnotation(ForwardProvider::class.java)

        // Then
        assertNotNull(annotation)
        assertEquals(AuditActionType.MEMBER_NAME_CHANGE, annotation.value)
    }

    @Test
    fun `constants should have correct values`() {
        // Then
        assertEquals("old", NicknameChangeAuditForwardProvider.OLD_NAME)
        assertEquals("new", NicknameChangeAuditForwardProvider.NEW_NAME)
    }
}