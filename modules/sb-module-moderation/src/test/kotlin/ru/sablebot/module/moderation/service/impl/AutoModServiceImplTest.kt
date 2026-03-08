package ru.sablebot.module.moderation.service.impl

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.coEvery
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.entities.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import ru.sablebot.common.model.AutoModActionType
import ru.sablebot.common.model.LinkFilterMode
import ru.sablebot.common.persistence.entity.AutoModConfig
import ru.sablebot.common.worker.modules.moderation.service.MuteService
import ru.sablebot.module.moderation.service.IAutoModConfigService
import ru.sablebot.module.moderation.service.IModerationService

@ExtendWith(MockKExtension::class)
class AutoModServiceImplTest {

    private lateinit var autoModConfigService: IAutoModConfigService
    private lateinit var moderationService: IModerationService
    private lateinit var muteService: MuteService
    private lateinit var meterRegistry: MeterRegistry
    private lateinit var counter: Counter

    private lateinit var service: AutoModServiceImpl

    @BeforeEach
    fun setUp() {
        autoModConfigService = mockk()
        moderationService = mockk()
        muteService = mockk()
        meterRegistry = mockk()
        counter = mockk(relaxed = true)

        every { meterRegistry.counter(any(), *anyVararg()) } returns counter

        service = AutoModServiceImpl(
            autoModConfigService, moderationService, muteService, meterRegistry
        )
    }

    private fun mockMessage(
        isBot: Boolean = false,
        isSystem: Boolean = false,
        isFromGuild: Boolean = true,
        contentRaw: String = "hello world",
        guildId: Long = 1L,
        authorId: Long = 100L,
        mentionCount: Int = 0
    ): Message {
        val user = mockk<User>(relaxed = true) {
            every { this@mockk.isBot } returns isBot
            every { this@mockk.isSystem } returns isSystem
            every { idLong } returns authorId
        }
        val guild = mockk<Guild>(relaxed = true) {
            every { idLong } returns guildId
            every { id } returns guildId.toString()
        }
        val member = mockk<Member>(relaxed = true)
        val mentions = mockk<Mentions>(relaxed = true) {
            val userList = (1..mentionCount).map { mockk<User>(relaxed = true) }
            every { users } returns userList
        }
        return mockk<Message>(relaxed = true) {
            every { author } returns user
            every { this@mockk.isFromGuild } returns isFromGuild
            every { this@mockk.guild } returns guild
            every { this@mockk.member } returns member
            every { this@mockk.contentRaw } returns contentRaw
            every { this@mockk.mentions } returns mentions
        }
    }

    // --- onMessage tests ---

    @Test
    fun `onMessage skips bot messages`() = runTest {
        val message = mockMessage(isBot = true)
        service.onMessage(message)
        verify(exactly = 0) { autoModConfigService.getByGuildId(any()) }
    }

    @Test
    fun `onMessage skips system messages`() = runTest {
        val message = mockMessage(isSystem = true)
        service.onMessage(message)
        verify(exactly = 0) { autoModConfigService.getByGuildId(any()) }
    }

    @Test
    fun `onMessage skips non-guild messages`() = runTest {
        val message = mockMessage(isFromGuild = false)
        service.onMessage(message)
        verify(exactly = 0) { autoModConfigService.getByGuildId(any()) }
    }

    @Test
    fun `onMessage skips when no config exists`() = runTest {
        val message = mockMessage()
        every { autoModConfigService.getByGuildId(1L) } returns null

        service.onMessage(message)

        verify { autoModConfigService.getByGuildId(1L) }
        // No further action should be taken
        verify(exactly = 0) { counter.increment() }
    }

    // --- checkSpam tests ---

    @Test
    fun `checkSpam triggers after max messages in window`() = runTest {
        val config = AutoModConfig().apply {
            antiSpamEnabled = true
            antiSpamMaxMessages = 3
            antiSpamWindowSeconds = 60
            antiSpamAction = AutoModActionType.DELETE
        }
        every { autoModConfigService.getByGuildId(1L) } returns config

        // Send messages up to threshold - the third should trigger
        repeat(3) {
            val msg = mockMessage(guildId = 1L, authorId = 200L)
            // The executeAction will try to delete; mock it relaxed
            every { msg.delete() } returns mockk(relaxed = true)
            service.onMessage(msg)
        }

        // Counter should have been incremented for the spam trigger
        verify(atLeast = 1) { meterRegistry.counter("sablebot.moderation.automod.triggers", "type", "spam") }
    }

    @Test
    fun `checkSpam does not trigger below threshold`() = runTest {
        val config = AutoModConfig().apply {
            antiSpamEnabled = true
            antiSpamMaxMessages = 5
            antiSpamWindowSeconds = 60
            antiSpamAction = AutoModActionType.DELETE
        }
        every { autoModConfigService.getByGuildId(1L) } returns config

        // Send 2 messages (below threshold of 5)
        repeat(2) {
            val msg = mockMessage(guildId = 1L, authorId = 300L)
            service.onMessage(msg)
        }

        verify(exactly = 0) { meterRegistry.counter("sablebot.moderation.automod.triggers", "type", "spam") }
    }

    // --- checkWordFilter tests ---

    @Test
    fun `checkWordFilter matches pattern in message content`() = runTest {
        val config = AutoModConfig().apply {
            antiSpamEnabled = false
            wordFilterEnabled = true
            wordFilterPatterns = mutableListOf("bad\\s?word", "forbidden")
            wordFilterAction = AutoModActionType.DELETE
        }
        every { autoModConfigService.getByGuildId(1L) } returns config

        val msg = mockMessage(contentRaw = "this contains a badword in it")
        every { msg.delete() } returns mockk(relaxed = true)

        service.onMessage(msg)

        verify { meterRegistry.counter("sablebot.moderation.automod.triggers", "type", "word") }
    }

    @Test
    fun `checkWordFilter skips invalid regex patterns gracefully`() = runTest {
        val config = AutoModConfig().apply {
            antiSpamEnabled = false
            wordFilterEnabled = true
            wordFilterPatterns = mutableListOf("[invalid(regex", "good_pattern")
            wordFilterAction = AutoModActionType.DELETE
        }
        every { autoModConfigService.getByGuildId(1L) } returns config

        // Message matches the valid pattern
        val msg = mockMessage(contentRaw = "this has good_pattern here")
        every { msg.delete() } returns mockk(relaxed = true)

        service.onMessage(msg)

        // Should still match the valid pattern despite the invalid one
        verify { meterRegistry.counter("sablebot.moderation.automod.triggers", "type", "word") }
    }

    @Test
    fun `checkWordFilter does not trigger when no patterns match`() = runTest {
        val config = AutoModConfig().apply {
            antiSpamEnabled = false
            wordFilterEnabled = true
            wordFilterPatterns = mutableListOf("badword", "forbidden")
            wordFilterAction = AutoModActionType.DELETE
        }
        every { autoModConfigService.getByGuildId(1L) } returns config

        val msg = mockMessage(contentRaw = "this is a totally clean message")

        service.onMessage(msg)

        verify(exactly = 0) { meterRegistry.counter("sablebot.moderation.automod.triggers", "type", "word") }
    }

    // --- checkLinkFilter tests ---

    @Test
    fun `checkLinkFilter blocks blacklisted domains`() = runTest {
        val config = AutoModConfig().apply {
            antiSpamEnabled = false
            wordFilterEnabled = false
            linkFilterEnabled = true
            linkFilterMode = LinkFilterMode.BLACKLIST
            linkFilterDomains = mutableListOf("evil.com", "spam.org")
            linkFilterAction = AutoModActionType.DELETE
        }
        every { autoModConfigService.getByGuildId(1L) } returns config

        val msg = mockMessage(contentRaw = "check this out https://evil.com/phishing")
        every { msg.delete() } returns mockk(relaxed = true)

        service.onMessage(msg)

        verify { meterRegistry.counter("sablebot.moderation.automod.triggers", "type", "link") }
    }

    @Test
    fun `checkLinkFilter allows whitelisted domains`() = runTest {
        val config = AutoModConfig().apply {
            antiSpamEnabled = false
            wordFilterEnabled = false
            linkFilterEnabled = true
            linkFilterMode = LinkFilterMode.WHITELIST
            linkFilterDomains = mutableListOf("trusted.com", "safe.org")
            linkFilterAction = AutoModActionType.DELETE
        }
        every { autoModConfigService.getByGuildId(1L) } returns config

        // Message with a whitelisted domain should NOT trigger
        val msg = mockMessage(contentRaw = "check this https://trusted.com/page")

        service.onMessage(msg)

        verify(exactly = 0) { meterRegistry.counter("sablebot.moderation.automod.triggers", "type", "link") }
    }

    @Test
    fun `checkLinkFilter triggers on non-whitelisted domain in whitelist mode`() = runTest {
        val config = AutoModConfig().apply {
            antiSpamEnabled = false
            wordFilterEnabled = false
            linkFilterEnabled = true
            linkFilterMode = LinkFilterMode.WHITELIST
            linkFilterDomains = mutableListOf("trusted.com")
            linkFilterAction = AutoModActionType.DELETE
        }
        every { autoModConfigService.getByGuildId(1L) } returns config

        val msg = mockMessage(contentRaw = "check this https://unknown.com/page")
        every { msg.delete() } returns mockk(relaxed = true)

        service.onMessage(msg)

        verify { meterRegistry.counter("sablebot.moderation.automod.triggers", "type", "link") }
    }

    // --- checkMentionSpam tests ---

    @Test
    fun `checkMentionSpam triggers at threshold`() = runTest {
        val config = AutoModConfig().apply {
            antiSpamEnabled = false
            wordFilterEnabled = false
            linkFilterEnabled = false
            mentionSpamEnabled = true
            mentionSpamThreshold = 3
            mentionSpamAction = AutoModActionType.WARN
        }
        every { autoModConfigService.getByGuildId(1L) } returns config

        val msg = mockMessage(mentionCount = 5)
        every { msg.delete() } returns mockk(relaxed = true)

        // mock the warn call since action will be WARN
        coEvery { moderationService.warn(any(), any(), any(), any()) } returns mockk(relaxed = true)

        service.onMessage(msg)

        verify { meterRegistry.counter("sablebot.moderation.automod.triggers", "type", "mention") }
    }

    @Test
    fun `checkMentionSpam does not trigger below threshold`() = runTest {
        val config = AutoModConfig().apply {
            antiSpamEnabled = false
            wordFilterEnabled = false
            linkFilterEnabled = false
            mentionSpamEnabled = true
            mentionSpamThreshold = 5
            mentionSpamAction = AutoModActionType.WARN
        }
        every { autoModConfigService.getByGuildId(1L) } returns config

        val msg = mockMessage(mentionCount = 2)

        service.onMessage(msg)

        verify(exactly = 0) { meterRegistry.counter("sablebot.moderation.automod.triggers", "type", "mention") }
    }
}
