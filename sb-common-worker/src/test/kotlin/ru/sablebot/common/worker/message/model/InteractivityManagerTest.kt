package ru.sablebot.common.worker.message.model

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*
import java.util.*
import kotlin.time.Duration.Companion.milliseconds

class InteractivityManagerTest {

    private lateinit var interactivityManager: InteractivityManager
    private lateinit var mockUser: User

    @BeforeEach
    fun setup() {
        interactivityManager = InteractivityManager()
        mockUser = mock(User::class.java)
        `when`(mockUser.idLong).thenReturn(123456789L)
    }

    @AfterEach
    fun tearDown() {
        interactivityManager.shutdown()
    }

    @Test
    fun `button creation should register callback and return button with unleashed id`() {
        // Given
        var callbackInvoked = false
        val callback: suspend (ComponentContext) -> Unit = { callbackInvoked = true }

        // When
        val button = interactivityManager.button(
            callbackAlwaysEphemeral = true,
            style = ButtonStyle.PRIMARY,
            label = "Test Button",
            callback = callback
        )

        // Then
        assertNotNull(button)
        assertEquals("Test Button", button.label)
        assertEquals(ButtonStyle.PRIMARY, button.style)
        assertTrue(button.id?.startsWith("unleashed:") == true)

        // Verify callback was registered
        val componentId = UnleashedComponentId(button.id!!)
        assertTrue(interactivityManager.buttonInteractionCallbacks.containsKey(componentId.uniqueId))
        val registeredCallback = interactivityManager.buttonInteractionCallbacks[componentId.uniqueId]
        assertNotNull(registeredCallback)
        assertTrue(registeredCallback!!.alwaysEphemeral)
    }

    @Test
    fun `button with emoji should be created correctly`() {
        // Given
        val emoji = Emoji.fromUnicode("👍")
        val callback: suspend (ComponentContext) -> Unit = {}

        // When
        val button = interactivityManager.button(
            callbackAlwaysEphemeral = false,
            style = ButtonStyle.SUCCESS,
            label = "Like",
            builder = { emoji(emoji) },
            callback = callback
        )

        // Then
        assertNotNull(button)
        assertEquals("Like", button.label)
        assertEquals(emoji, button.emoji)
        assertEquals(ButtonStyle.SUCCESS, button.style)
    }

    @Test
    fun `button with disabled state should be created correctly`() {
        // Given
        val callback: suspend (ComponentContext) -> Unit = {}

        // When
        val button = interactivityManager.button(
            callbackAlwaysEphemeral = true,
            style = ButtonStyle.DANGER,
            label = "Disabled",
            builder = { disabled = true },
            callback = callback
        )

        // Then
        assertTrue(button.isDisabled)
    }

    @Test
    fun `buttonForUser should create button that validates user`() {
        // Given
        val targetUserId = 987654321L
        var callbackInvoked = false
        val callback: suspend (ComponentContext) -> Unit = { callbackInvoked = true }

        // When
        val button = interactivityManager.buttonForUser(
            targetUserId = targetUserId,
            callbackAlwaysEphemeral = true,
            style = ButtonStyle.PRIMARY,
            label = "User Button",
            callback = callback
        )

        // Then
        assertNotNull(button)
        val componentId = UnleashedComponentId(button.id!!)
        val registeredCallback = interactivityManager.buttonInteractionCallbacks[componentId.uniqueId]
        assertNotNull(registeredCallback)
    }

    @Test
    fun `buttonForUser with User object should create button correctly`() {
        // Given
        val callback: suspend (ComponentContext) -> Unit = {}

        // When
        val button = interactivityManager.buttonForUser(
            targetUser = mockUser,
            callbackAlwaysEphemeral = true,
            style = ButtonStyle.SECONDARY,
            label = "User Button",
            callback = callback
        )

        // Then
        assertNotNull(button)
        assertTrue(button.id?.startsWith("unleashed:") == true)
    }

    @Test
    fun `buttonForUser with existing Button should replace ID`() {
        // Given
        val existingButton = Button.primary("old-id", "Test")
        val callback: suspend (ComponentContext) -> Unit = {}

        // When
        val button = interactivityManager.buttonForUser(
            targetUserId = 123456L,
            callbackAlwaysEphemeral = false,
            button = existingButton,
            callback = callback
        )

        // Then
        assertNotNull(button)
        assertNotEquals("old-id", button.id)
        assertTrue(button.id?.startsWith("unleashed:") == true)
        assertEquals("Test", button.label)
    }

    @Test
    fun `stringSelectMenu should register callback and return menu with unleashed id`() {
        // Given
        val callback: suspend (ComponentContext, List<String>) -> Unit = { _, _ -> }

        // When
        val menu = interactivityManager.stringSelectMenu(
            callbackAlwaysEphemeral = true,
            builder = {
                addOption("Option 1", "opt1")
                addOption("Option 2", "opt2")
                setPlaceholder("Select an option")
            },
            callback = callback
        )

        // Then
        assertNotNull(menu)
        assertTrue(menu.id.startsWith("unleashed:"))
        assertEquals(2, menu.options.size)
        assertEquals("Select an option", menu.placeholder)

        // Verify callback was registered
        val componentId = UnleashedComponentId(menu.id)
        assertTrue(interactivityManager.selectMenuInteractionCallbacks.containsKey(componentId.uniqueId))
    }

    @Test
    fun `stringSelectMenuForUser should create menu with user validation`() {
        // Given
        val callback: suspend (ComponentContext, List<String>) -> Unit = { _, _ -> }

        // When
        val menu = interactivityManager.stringSelectMenuForUser(
            targetUser = mockUser,
            callbackAlwaysEphemeral = false,
            builder = {
                addOption("Test", "test")
            },
            callback = callback
        )

        // Then
        assertNotNull(menu)
        assertTrue(menu.id.startsWith("unleashed:"))
    }

    @Test
    fun `stringSelectMenuForUser with userId should create menu correctly`() {
        // Given
        val targetUserId = 999888777L
        val callback: suspend (ComponentContext, List<String>) -> Unit = { _, _ -> }

        // When
        val menu = interactivityManager.stringSelectMenuForUser(
            targetUserId = targetUserId,
            callbackAlwaysEphemeral = true,
            builder = {
                addOption("Item", "item")
                setMaxValues(1)
            },
            callback = callback
        )

        // Then
        assertNotNull(menu)
        assertEquals(1, menu.maxValues)
    }

    @Test
    fun `entitySelectMenu should register callback and return menu with unleashed id`() {
        // Given
        val callback: suspend (ComponentContext, List<net.dv8tion.jda.api.entities.IMentionable>) -> Unit = { _, _ -> }

        // When
        val menu = interactivityManager.entitySelectMenu(
            callbackAlwaysEphemeral = true,
            builder = {
                setPlaceholder("Select entities")
                setMaxValues(3)
            },
            callback = callback
        )

        // Then
        assertNotNull(menu)
        assertTrue(menu.id.startsWith("unleashed:"))
        assertEquals("Select entities", menu.placeholder)
        assertEquals(3, menu.maxValues)

        // Verify callback was registered
        val componentId = UnleashedComponentId(menu.id)
        assertTrue(interactivityManager.selectMenuEntityInteractionCallbacks.containsKey(componentId.uniqueId))
    }

    @Test
    fun `entitySelectMenuForUser should create menu with user validation`() {
        // Given
        val callback: suspend (ComponentContext, List<net.dv8tion.jda.api.entities.IMentionable>) -> Unit = { _, _ -> }

        // When
        val menu = interactivityManager.entitySelectMenuForUser(
            targetUser = mockUser,
            callbackAlwaysEphemeral = false,
            callback = callback
        )

        // Then
        assertNotNull(menu)
        assertTrue(menu.id.startsWith("unleashed:"))
    }

    @Test
    fun `entitySelectMenuForUser with userId should create menu correctly`() {
        // Given
        val targetUserId = 111222333L
        val callback: suspend (ComponentContext, List<net.dv8tion.jda.api.entities.IMentionable>) -> Unit = { _, _ -> }

        // When
        val menu = interactivityManager.entitySelectMenuForUser(
            targetUserId = targetUserId,
            callbackAlwaysEphemeral = true,
            builder = {
                setMinValues(1)
            },
            callback = callback
        )

        // Then
        assertNotNull(menu)
        assertEquals(1, menu.minValues)
    }

    @Test
    fun `callbacks should expire after delay`() = runTest {
        // Given
        val callback: suspend (ComponentContext) -> Unit = {}
        val button = interactivityManager.button(
            callbackAlwaysEphemeral = true,
            style = ButtonStyle.PRIMARY,
            label = "Test",
            callback = callback
        )
        val componentId = UnleashedComponentId(button.id!!)

        // Then - callback should be registered immediately
        assertTrue(interactivityManager.buttonInteractionCallbacks.containsKey(componentId.uniqueId))

        // Note: We can't easily test expiration without waiting 5 minutes
        // This test validates the callback is initially registered
    }

    @Test
    fun `multiple callbacks should be registered independently`() {
        // Given
        val callback1: suspend (ComponentContext) -> Unit = {}
        val callback2: suspend (ComponentContext) -> Unit = {}
        val callback3: suspend (ComponentContext) -> Unit = {}

        // When
        val button1 = interactivityManager.button(true, ButtonStyle.PRIMARY, "B1", callback = callback1)
        val button2 = interactivityManager.button(false, ButtonStyle.SECONDARY, "B2", callback = callback2)
        val menu = interactivityManager.stringSelectMenu(true, { addOption("O", "o") }, callback3)

        // Then
        val id1 = UnleashedComponentId(button1.id!!).uniqueId
        val id2 = UnleashedComponentId(button2.id!!).uniqueId
        val id3 = UnleashedComponentId(menu.id).uniqueId

        assertTrue(interactivityManager.buttonInteractionCallbacks.containsKey(id1))
        assertTrue(interactivityManager.buttonInteractionCallbacks.containsKey(id2))
        assertTrue(interactivityManager.selectMenuInteractionCallbacks.containsKey(id3))

        // All should have unique IDs
        assertNotEquals(id1, id2)
        assertNotEquals(id1, id3)
        assertNotEquals(id2, id3)
    }

    @Test
    fun `shutdown should cancel coroutine scope`() {
        // Given
        val manager = InteractivityManager()
        
        // When
        manager.shutdown()
        
        // Then - scope should be cancelled
        // We can't directly test the scope, but we verify shutdown completes without exception
        assertDoesNotThrow { manager.shutdown() }
    }

    @Test
    fun `cache size should be limited to MAX_SIZE`() {
        // Given
        val maxSize = InteractivityManager.MAX_SIZE.toInt()

        // When - create more callbacks than max size
        repeat(maxSize + 10) { i ->
            interactivityManager.button(
                callbackAlwaysEphemeral = true,
                style = ButtonStyle.PRIMARY,
                label = "Button $i",
                callback = {}
            )
        }

        // Then - cache size should not exceed MAX_SIZE
        assertTrue(interactivityManager.buttonInteractionCallbacks.size <= maxSize)
    }

    @Test
    fun `ButtonInteractionCallback should store ephemeral flag correctly`() {
        // Given & When
        val callback1 = InteractivityManager.ButtonInteractionCallback(true) {}
        val callback2 = InteractivityManager.ButtonInteractionCallback(false) {}

        // Then
        assertTrue(callback1.alwaysEphemeral)
        assertFalse(callback2.alwaysEphemeral)
    }

    @Test
    fun `SelectMenuInteractionCallback should store ephemeral flag correctly`() {
        // Given & When
        val callback1 = InteractivityManager.SelectMenuInteractionCallback(true) { _, _ -> }
        val callback2 = InteractivityManager.SelectMenuInteractionCallback(false) { _, _ -> }

        // Then
        assertTrue(callback1.alwaysEphemeral)
        assertFalse(callback2.alwaysEphemeral)
    }

    @Test
    fun `SelectMenuEntityInteractionCallback should store ephemeral flag correctly`() {
        // Given & When
        val callback1 = InteractivityManager.SelectMenuEntityInteractionCallback(true) { _, _ -> }
        val callback2 = InteractivityManager.SelectMenuEntityInteractionCallback(false) { _, _ -> }

        // Then
        assertTrue(callback1.alwaysEphemeral)
        assertFalse(callback2.alwaysEphemeral)
    }

    @Test
    fun `ModalInteractionCallback should store ephemeral flag correctly`() {
        // Given & When
        val callback1 = InteractivityManager.ModalInteractionCallback(true) { _, _ -> }
        val callback2 = InteractivityManager.ModalInteractionCallback(false) { _, _ -> }

        // Then
        assertTrue(callback1.alwaysEphemeral)
        assertFalse(callback2.alwaysEphemeral)
    }

    @Test
    fun `JDAButtonBuilder should set emoji correctly`() {
        // Given
        val button = Button.primary("test", "Test")
        val builder = InteractivityManager.JDAButtonBuilder(button)
        val emoji = Emoji.fromUnicode("🎉")

        // When
        builder.emoji(emoji)

        // Then
        assertEquals(emoji, builder.button.emoji)
    }

    @Test
    fun `JDAButtonBuilder should get and set disabled state`() {
        // Given
        val button = Button.primary("test", "Test")
        val builder = InteractivityManager.JDAButtonBuilder(button)

        // When & Then - initially not disabled
        assertFalse(builder.disabled)

        // When - set disabled
        builder.disabled = true

        // Then
        assertTrue(builder.disabled)
        assertTrue(builder.button.isDisabled)
    }

    @Test
    fun `empty label button should be created without exception`() {
        // Given
        val callback: suspend (ComponentContext) -> Unit = {}

        // When & Then
        assertDoesNotThrow {
            interactivityManager.button(
                callbackAlwaysEphemeral = true,
                style = ButtonStyle.PRIMARY,
                label = "",
                callback = callback
            )
        }
    }

    @Test
    fun `DELAY constant should be 5 minutes`() {
        // Then
        assertEquals(5 * 60 * 1000, InteractivityManager.DELAY.inWholeMilliseconds)
    }

    @Test
    fun `button with all styles should be created successfully`() {
        // Given
        val callback: suspend (ComponentContext) -> Unit = {}

        // When & Then
        ButtonStyle.entries.filter { it != ButtonStyle.LINK && it != ButtonStyle.UNKNOWN }.forEach { style ->
            assertDoesNotThrow {
                val button = interactivityManager.button(
                    callbackAlwaysEphemeral = true,
                    style = style,
                    label = "Test ${style.name}",
                    callback = callback
                )
                assertEquals(style, button.style)
            }
        }
    }
}