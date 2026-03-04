# Moderation Module Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a full-featured moderation module (`sb-module-moderation`) with manual moderation commands, numbered case system, warning escalation, and auto-moderation suite.

**Architecture:** New Gradle module following the `sb-module-audio` pattern. Core `ModerationServiceImpl` orchestrates all mod actions (ban/kick/warn/timeout/mute), creates numbered cases in DB, logs to audit, sends modlog embeds, optionally DMs targets, and checks warn escalation rules. Auto-mod runs as event listeners using Caffeine caches for rate limiting.

**Tech Stack:** Kotlin, Spring Boot 3.5.6, JDA 5, JPA/Hibernate, Liquibase, Quartz, Caffeine cache.

**Design doc:** `docs/plans/2026-03-03-moderation-module-design.md`

---

### Task 1: Gradle Module Setup

**Files:**
- Modify: `settings.gradle`
- Create: `modules/sb-module-moderation/build.gradle`
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/config/ModerationModuleConfig.kt`

**Step 1: Add module to settings.gradle**

Add this line after `include ':modules:sb-module-audio'`:

```
include ':modules:sb-module-moderation'
```

**Step 2: Create build.gradle**

Create `modules/sb-module-moderation/build.gradle`:

```gradle
plugins {
    id "org.jetbrains.kotlin.plugin.spring"
}
dependencies {
    implementation project(":sb-common-worker")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.2")
    implementation "org.jetbrains.kotlin:kotlin-stdlib"
}
repositories {
    mavenCentral()
}
```

**Step 3: Create Spring config**

Create `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/config/ModerationModuleConfig.kt`:

```kotlin
package ru.sablebot.module.moderation.config

import org.springframework.context.annotation.Configuration

@Configuration
class ModerationModuleConfig
```

**Step 4: Verify compilation**

Run: `./gradlew :modules:sb-module-moderation:compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add settings.gradle modules/sb-module-moderation/
git commit -m "feat(moderation): scaffold sb-module-moderation Gradle module"
```

---

### Task 2: Database Entities & Liquibase Migration

**Files:**
- Create: `sb-common/src/main/kotlin/ru/sablebot/common/persistence/entity/ModerationCase.kt`
- Create: `sb-common/src/main/kotlin/ru/sablebot/common/persistence/entity/WarnEscalationRule.kt`
- Create: `sb-common/src/main/kotlin/ru/sablebot/common/persistence/entity/AutoModConfig.kt`
- Create: `sb-common/src/main/kotlin/ru/sablebot/common/model/ModerationCaseType.kt`
- Create: `sb-common/src/main/kotlin/ru/sablebot/common/model/AutoModActionType.kt`
- Create: `sb-common/src/main/kotlin/ru/sablebot/common/model/LinkFilterMode.kt`
- Modify: `sb-common/src/main/kotlin/ru/sablebot/common/persistence/entity/ModerationConfig.kt` (add `modlogChannelId`)
- Create: Liquibase changelog for new tables

Entities extend existing base classes (`GuildEntity`, `BaseEntity`) following the project's established pattern. `ModerationCase` stores every mod action as a numbered case per guild. `WarnEscalationRule` is a child of `ModerationConfig` for configurable warn thresholds. `AutoModConfig` holds all auto-mod settings per guild.

**Step 1: Create ModerationCaseType enum**

Create `sb-common/src/main/kotlin/ru/sablebot/common/model/ModerationCaseType.kt`:

```kotlin
package ru.sablebot.common.model

enum class ModerationCaseType {
    BAN,
    UNBAN,
    KICK,
    WARN,
    MUTE,
    UNMUTE,
    TIMEOUT,
    UNTIMEOUT
}
```

**Step 2: Create AutoModActionType enum**

Create `sb-common/src/main/kotlin/ru/sablebot/common/model/AutoModActionType.kt`:

```kotlin
package ru.sablebot.common.model

enum class AutoModActionType {
    WARN,
    MUTE,
    KICK,
    BAN,
    DELETE
}
```

**Step 3: Create LinkFilterMode enum**

Create `sb-common/src/main/kotlin/ru/sablebot/common/model/LinkFilterMode.kt`:

```kotlin
package ru.sablebot.common.model

enum class LinkFilterMode {
    WHITELIST,
    BLACKLIST
}
```

**Step 4: Create ModerationCase entity**

Create `sb-common/src/main/kotlin/ru/sablebot/common/persistence/entity/ModerationCase.kt`:

```kotlin
package ru.sablebot.common.persistence.entity

import jakarta.persistence.*
import ru.sablebot.common.model.ModerationCaseType
import ru.sablebot.common.persistence.entity.base.GuildEntity
import java.time.Instant

@Entity
@Table(
    name = "moderation_case",
    indexes = [
        Index(name = "idx_mod_case_guild_number", columnList = "guild_id,case_number", unique = true),
        Index(name = "idx_mod_case_guild_target", columnList = "guild_id,target_id")
    ]
)
class ModerationCase(
    @Column(name = "case_number", nullable = false)
    var caseNumber: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    var actionType: ModerationCaseType = ModerationCaseType.WARN,

    @Column(name = "moderator_id", length = 21, nullable = false)
    var moderatorId: String = "",

    @Column(name = "moderator_name")
    var moderatorName: String = "",

    @Column(name = "target_id", length = 21, nullable = false)
    var targetId: String = "",

    @Column(name = "target_name")
    var targetName: String = "",

    @Column(columnDefinition = "TEXT")
    var reason: String? = null,

    @Column
    var duration: Long? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var active: Boolean = true
) : GuildEntity()
```

**Step 5: Create WarnEscalationRule entity**

Create `sb-common/src/main/kotlin/ru/sablebot/common/persistence/entity/WarnEscalationRule.kt`:

```kotlin
package ru.sablebot.common.persistence.entity

import jakarta.persistence.*
import ru.sablebot.common.model.ModerationActionType
import ru.sablebot.common.persistence.entity.base.BaseEntity

@Entity
@Table(name = "warn_escalation_rule")
class WarnEscalationRule(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id", nullable = false)
    var config: ModerationConfig? = null,

    @Column(nullable = false)
    var threshold: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    var actionType: ModerationActionType = ModerationActionType.MUTE,

    @Column
    var duration: Long? = null
) : BaseEntity()
```

**Step 6: Create AutoModConfig entity**

Create `sb-common/src/main/kotlin/ru/sablebot/common/persistence/entity/AutoModConfig.kt`:

```kotlin
package ru.sablebot.common.persistence.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import ru.sablebot.common.model.AutoModActionType
import ru.sablebot.common.model.LinkFilterMode
import ru.sablebot.common.persistence.entity.base.GuildEntity

@Entity
@Table(name = "automod_config")
class AutoModConfig(
    @Column(name = "anti_spam_enabled", nullable = false)
    var antiSpamEnabled: Boolean = false,

    @Column(name = "anti_spam_max_messages", nullable = false)
    var antiSpamMaxMessages: Int = 5,

    @Column(name = "anti_spam_window_seconds", nullable = false)
    var antiSpamWindowSeconds: Int = 5,

    @Enumerated(EnumType.STRING)
    @Column(name = "anti_spam_action", nullable = false)
    var antiSpamAction: AutoModActionType = AutoModActionType.MUTE,

    @Column(name = "anti_spam_mute_duration")
    var antiSpamMuteDuration: Long? = 300_000L,

    @Column(name = "anti_raid_enabled", nullable = false)
    var antiRaidEnabled: Boolean = false,

    @Column(name = "anti_raid_join_threshold", nullable = false)
    var antiRaidJoinThreshold: Int = 10,

    @Column(name = "anti_raid_window_seconds", nullable = false)
    var antiRaidWindowSeconds: Int = 10,

    @Column(name = "anti_raid_min_account_age_days", nullable = false)
    var antiRaidMinAccountAgeDays: Int = 7,

    @Enumerated(EnumType.STRING)
    @Column(name = "anti_raid_action", nullable = false)
    var antiRaidAction: AutoModActionType = AutoModActionType.KICK,

    @Column(name = "word_filter_enabled", nullable = false)
    var wordFilterEnabled: Boolean = false,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "word_filter_patterns", columnDefinition = "jsonb")
    var wordFilterPatterns: MutableList<String> = mutableListOf(),

    @Enumerated(EnumType.STRING)
    @Column(name = "word_filter_action", nullable = false)
    var wordFilterAction: AutoModActionType = AutoModActionType.DELETE,

    @Column(name = "link_filter_enabled", nullable = false)
    var linkFilterEnabled: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(name = "link_filter_mode", nullable = false)
    var linkFilterMode: LinkFilterMode = LinkFilterMode.BLACKLIST,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "link_filter_domains", columnDefinition = "jsonb")
    var linkFilterDomains: MutableList<String> = mutableListOf(),

    @Enumerated(EnumType.STRING)
    @Column(name = "link_filter_action", nullable = false)
    var linkFilterAction: AutoModActionType = AutoModActionType.DELETE,

    @Column(name = "mention_spam_enabled", nullable = false)
    var mentionSpamEnabled: Boolean = false,

    @Column(name = "mention_spam_threshold", nullable = false)
    var mentionSpamThreshold: Int = 5,

    @Enumerated(EnumType.STRING)
    @Column(name = "mention_spam_action", nullable = false)
    var mentionSpamAction: AutoModActionType = AutoModActionType.WARN,

    @Column(name = "dm_on_action", nullable = false)
    var dmOnAction: Boolean = false
) : GuildEntity() {
    constructor(guildId: Long) : this() {
        this.guildId = guildId
    }
}
```

**Step 7: Modify ModerationConfig — add modlogChannelId and escalation rules**

In `sb-common/src/main/kotlin/ru/sablebot/common/persistence/entity/ModerationConfig.kt`, add:

```kotlin
@Column(name = "modlog_channel_id")
var modlogChannelId: Long? = null,

@OneToMany(
    mappedBy = "config",
    cascade = [CascadeType.ALL],
    fetch = FetchType.LAZY,
    orphanRemoval = true
)
@OrderBy("threshold")
var escalationRules: MutableList<WarnEscalationRule> = mutableListOf()
```

**Step 8: Create Liquibase migration**

Find the existing changelog location and create a new migration file for:
- `moderation_case` table
- `warn_escalation_rule` table
- `automod_config` table
- `ALTER TABLE mod_config ADD COLUMN modlog_channel_id BIGINT`

Follow the existing changelog naming convention found in the project.

**Step 9: Verify compilation**

Run: `./gradlew :sb-common:compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 10: Commit**

```bash
git add sb-common/src/main/kotlin/ru/sablebot/common/model/ModerationCaseType.kt \
  sb-common/src/main/kotlin/ru/sablebot/common/model/AutoModActionType.kt \
  sb-common/src/main/kotlin/ru/sablebot/common/model/LinkFilterMode.kt \
  sb-common/src/main/kotlin/ru/sablebot/common/persistence/entity/ModerationCase.kt \
  sb-common/src/main/kotlin/ru/sablebot/common/persistence/entity/WarnEscalationRule.kt \
  sb-common/src/main/kotlin/ru/sablebot/common/persistence/entity/AutoModConfig.kt \
  sb-common/src/main/kotlin/ru/sablebot/common/persistence/entity/ModerationConfig.kt \
  sb-common/src/main/resources/db/
git commit -m "feat(moderation): add ModerationCase, WarnEscalationRule, AutoModConfig entities and migrations"
```

---

### Task 3: Repositories

**Files:**
- Create: `sb-common/src/main/kotlin/ru/sablebot/common/persistence/repository/ModerationCaseRepository.kt`
- Create: `sb-common/src/main/kotlin/ru/sablebot/common/persistence/repository/WarnEscalationRuleRepository.kt`
- Create: `sb-common/src/main/kotlin/ru/sablebot/common/persistence/repository/AutoModConfigRepository.kt`

**Step 1: Create ModerationCaseRepository**

```kotlin
package ru.sablebot.common.persistence.repository

import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import ru.sablebot.common.model.ModerationCaseType
import ru.sablebot.common.persistence.entity.ModerationCase
import ru.sablebot.common.persistence.repository.base.GuildRepository

@Repository
interface ModerationCaseRepository : GuildRepository<ModerationCase> {

    @Query("SELECT COALESCE(MAX(m.caseNumber), 0) FROM ModerationCase m WHERE m.guildId = :guildId")
    fun findMaxCaseNumber(guildId: Long): Int

    fun findByGuildIdAndCaseNumber(guildId: Long, caseNumber: Int): ModerationCase?

    fun findByGuildIdAndTargetIdOrderByCaseNumberDesc(guildId: Long, targetId: String): List<ModerationCase>

    fun findByGuildIdAndTargetIdAndActionTypeAndActive(
        guildId: Long,
        targetId: String,
        actionType: ModerationCaseType,
        active: Boolean
    ): List<ModerationCase>

    fun countByGuildIdAndTargetIdAndActionTypeAndActive(
        guildId: Long,
        targetId: String,
        actionType: ModerationCaseType,
        active: Boolean
    ): Int
}
```

**Step 2: Create WarnEscalationRuleRepository**

```kotlin
package ru.sablebot.common.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.sablebot.common.persistence.entity.WarnEscalationRule

@Repository
interface WarnEscalationRuleRepository : JpaRepository<WarnEscalationRule, Long>
```

**Step 3: Create AutoModConfigRepository**

```kotlin
package ru.sablebot.common.persistence.repository

import org.springframework.stereotype.Repository
import ru.sablebot.common.persistence.entity.AutoModConfig
import ru.sablebot.common.persistence.repository.base.GuildRepository

@Repository
interface AutoModConfigRepository : GuildRepository<AutoModConfig>
```

**Step 4: Verify compilation**

Run: `./gradlew :sb-common:compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add sb-common/src/main/kotlin/ru/sablebot/common/persistence/repository/ModerationCaseRepository.kt \
  sb-common/src/main/kotlin/ru/sablebot/common/persistence/repository/WarnEscalationRuleRepository.kt \
  sb-common/src/main/kotlin/ru/sablebot/common/persistence/repository/AutoModConfigRepository.kt
git commit -m "feat(moderation): add repositories for ModerationCase, WarnEscalationRule, AutoModConfig"
```

---

### Task 4: DurationParser Utility & AutoModResult Model

**Files:**
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/model/DurationParser.kt`
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/model/AutoModResult.kt`

**Step 1: Create DurationParser**

```kotlin
package ru.sablebot.module.moderation.model

import java.time.Duration

object DurationParser {

    private val PATTERN = Regex("(?:(\\d+)w)?\\s*(?:(\\d+)d)?\\s*(?:(\\d+)h)?\\s*(?:(\\d+)m)?\\s*(?:(\\d+)s)?")

    fun parse(input: String): Duration? {
        val match = PATTERN.matchEntire(input.trim().lowercase()) ?: return null
        val (weeks, days, hours, minutes, seconds) = match.destructured

        val totalSeconds = (weeks.toLongOrNull() ?: 0) * 7 * 24 * 3600 +
                (days.toLongOrNull() ?: 0) * 24 * 3600 +
                (hours.toLongOrNull() ?: 0) * 3600 +
                (minutes.toLongOrNull() ?: 0) * 60 +
                (seconds.toLongOrNull() ?: 0)

        return if (totalSeconds > 0) Duration.ofSeconds(totalSeconds) else null
    }

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

**Step 2: Create AutoModResult**

```kotlin
package ru.sablebot.module.moderation.model

import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import ru.sablebot.common.model.AutoModActionType

data class AutoModResult(
    val trigger: String,
    val action: AutoModActionType,
    val message: Message,
    val member: Member,
    val reason: String,
    val muteDuration: Long? = null,
    val deleteMessage: Boolean = false
)
```

**Step 3: Verify compilation**

Run: `./gradlew :modules:sb-module-moderation:compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/model/
git commit -m "feat(moderation): add DurationParser utility and AutoModResult model"
```

---

### Task 5: ModerationService Interface & Implementation

**Files:**
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/service/IModerationService.kt`
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/service/impl/ModerationServiceImpl.kt`

This is the core orchestrator. Each action: (1) performs JDA action, (2) creates ModerationCase, (3) logs to audit, (4) sends modlog embed, (5) optionally DMs target, (6) for warns checks escalation.

**Step 1: Create IModerationService interface**

```kotlin
package ru.sablebot.module.moderation.service

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import ru.sablebot.common.persistence.entity.ModerationCase

interface IModerationService {

    suspend fun ban(guild: Guild, target: Member, moderator: Member, reason: String?, duration: Long?, deleteDays: Int?): ModerationCase
    suspend fun unban(guild: Guild, targetUser: User, moderator: Member, reason: String?): ModerationCase
    suspend fun kick(guild: Guild, target: Member, moderator: Member, reason: String?): ModerationCase
    suspend fun warn(guild: Guild, target: Member, moderator: Member, reason: String): ModerationCase
    suspend fun timeout(guild: Guild, target: Member, moderator: Member, duration: Long, reason: String?): ModerationCase
    suspend fun removeTimeout(guild: Guild, target: Member, moderator: Member, reason: String?): ModerationCase
    suspend fun purgeMessages(channel: TextChannel, count: Int, filterUser: User?): Int
    suspend fun lockChannel(channel: TextChannel, moderator: Member, reason: String?)
    suspend fun unlockChannel(channel: TextChannel, moderator: Member)
    fun getWarnings(guildId: Long, targetId: String): List<ModerationCase>
    fun clearWarnings(guildId: Long, targetId: String): Int
    fun getCase(guildId: Long, caseNumber: Int): ModerationCase?
    fun getModLog(guildId: Long, targetId: String): List<ModerationCase>
}
```

**Step 2: Create ModerationServiceImpl**

```kotlin
package ru.sablebot.module.moderation.service.impl

import dev.minn.jda.ktx.coroutines.await
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.quartz.*
import org.slf4j.LoggerFactory
import org.springframework.scheduling.quartz.SchedulerFactoryBean
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.sablebot.common.model.AuditActionType
import ru.sablebot.common.model.ModerationActionType
import ru.sablebot.common.model.ModerationCaseType
import ru.sablebot.common.persistence.entity.ModerationCase
import ru.sablebot.common.persistence.repository.ModerationCaseRepository
import ru.sablebot.common.service.ModerationConfigService
import ru.sablebot.common.worker.modules.audit.service.AuditService
import ru.sablebot.common.worker.modules.moderation.model.ModerationActionRequest
import ru.sablebot.common.worker.modules.moderation.service.MuteService
import ru.sablebot.module.moderation.job.UnBanJob
import ru.sablebot.module.moderation.model.DurationParser
import ru.sablebot.module.moderation.service.IModerationService
import java.awt.Color
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit

@Service
class ModerationServiceImpl(
    private val caseRepository: ModerationCaseRepository,
    private val moderationConfigService: ModerationConfigService,
    private val auditService: AuditService,
    private val muteService: MuteService,
    private val schedulerFactoryBean: SchedulerFactoryBean
) : IModerationService {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun createCase(
        guildId: Long,
        actionType: ModerationCaseType,
        moderatorId: String,
        moderatorName: String,
        targetId: String,
        targetName: String,
        reason: String?,
        duration: Long?,
        active: Boolean = true
    ): ModerationCase {
        val nextCaseNumber = caseRepository.findMaxCaseNumber(guildId) + 1
        val case = ModerationCase(
            caseNumber = nextCaseNumber,
            actionType = actionType,
            moderatorId = moderatorId,
            moderatorName = moderatorName,
            targetId = targetId,
            targetName = targetName,
            reason = reason,
            duration = duration,
            createdAt = Instant.now(),
            active = active
        )
        case.guildId = guildId
        return caseRepository.save(case)
    }

    override suspend fun ban(
        guild: Guild,
        target: Member,
        moderator: Member,
        reason: String?,
        duration: Long?,
        deleteDays: Int?
    ): ModerationCase {
        val config = moderationConfigService.getOrCreate(guild.idLong)

        // DM target before action
        if (config.modlogChannelId != null) {
            dmTarget(target.user, guild, "banned", reason, duration)
        }

        // Execute ban
        guild.ban(target, deleteDays ?: 0, TimeUnit.DAYS)
            .reason(reason)
            .await()

        // Schedule unban if temporary
        if (duration != null) {
            scheduleUnban(guild.id, target.id, duration)
        }

        // Create case
        val case = createCase(
            guildId = guild.idLong,
            actionType = ModerationCaseType.BAN,
            moderatorId = moderator.id,
            moderatorName = moderator.effectiveName,
            targetId = target.id,
            targetName = target.effectiveName,
            reason = reason,
            duration = duration
        )

        // Audit log
        auditService.log(guild.idLong, AuditActionType.MEMBER_BAN)
            .withUser(moderator)
            .withTargetUser(target)
            .withAttribute("reason", reason)
            .withAttribute("duration", duration?.let { DurationParser.format(it) })
            .withAttribute("case", case.caseNumber)
            .save()

        // Modlog embed
        sendModlogEmbed(guild, case)

        return case
    }

    override suspend fun unban(
        guild: Guild,
        targetUser: User,
        moderator: Member,
        reason: String?
    ): ModerationCase {
        guild.unban(targetUser).reason(reason).await()

        val case = createCase(
            guildId = guild.idLong,
            actionType = ModerationCaseType.UNBAN,
            moderatorId = moderator.id,
            moderatorName = moderator.effectiveName,
            targetId = targetUser.id,
            targetName = targetUser.effectiveName,
            reason = reason,
            duration = null,
            active = false
        )

        auditService.log(guild.idLong, AuditActionType.MEMBER_UNBAN)
            .withUser(moderator)
            .withTargetUser(targetUser)
            .withAttribute("reason", reason)
            .withAttribute("case", case.caseNumber)
            .save()

        sendModlogEmbed(guild, case)
        return case
    }

    override suspend fun kick(
        guild: Guild,
        target: Member,
        moderator: Member,
        reason: String?
    ): ModerationCase {
        dmTarget(target.user, guild, "kicked", reason, null)

        guild.kick(target).reason(reason).await()

        val case = createCase(
            guildId = guild.idLong,
            actionType = ModerationCaseType.KICK,
            moderatorId = moderator.id,
            moderatorName = moderator.effectiveName,
            targetId = target.id,
            targetName = target.effectiveName,
            reason = reason,
            duration = null,
            active = false
        )

        auditService.log(guild.idLong, AuditActionType.MEMBER_KICK)
            .withUser(moderator)
            .withTargetUser(target)
            .withAttribute("reason", reason)
            .withAttribute("case", case.caseNumber)
            .save()

        sendModlogEmbed(guild, case)
        return case
    }

    @Transactional
    override suspend fun warn(
        guild: Guild,
        target: Member,
        moderator: Member,
        reason: String
    ): ModerationCase {
        val case = createCase(
            guildId = guild.idLong,
            actionType = ModerationCaseType.WARN,
            moderatorId = moderator.id,
            moderatorName = moderator.effectiveName,
            targetId = target.id,
            targetName = target.effectiveName,
            reason = reason,
            duration = null
        )

        dmTarget(target.user, guild, "warned", reason, null)

        auditService.log(guild.idLong, AuditActionType.MEMBER_WARN)
            .withUser(moderator)
            .withTargetUser(target)
            .withAttribute("reason", reason)
            .withAttribute("case", case.caseNumber)
            .save()

        sendModlogEmbed(guild, case)

        // Check escalation
        checkEscalation(guild, target, moderator)

        return case
    }

    override suspend fun timeout(
        guild: Guild,
        target: Member,
        moderator: Member,
        duration: Long,
        reason: String?
    ): ModerationCase {
        dmTarget(target.user, guild, "timed out", reason, duration)

        target.timeoutFor(Duration.ofMillis(duration)).reason(reason).await()

        val case = createCase(
            guildId = guild.idLong,
            actionType = ModerationCaseType.TIMEOUT,
            moderatorId = moderator.id,
            moderatorName = moderator.effectiveName,
            targetId = target.id,
            targetName = target.effectiveName,
            reason = reason,
            duration = duration
        )

        auditService.log(guild.idLong, AuditActionType.MEMBER_MUTE)
            .withUser(moderator)
            .withTargetUser(target)
            .withAttribute("reason", reason)
            .withAttribute("duration", DurationParser.format(duration))
            .withAttribute("case", case.caseNumber)
            .save()

        sendModlogEmbed(guild, case)
        return case
    }

    override suspend fun removeTimeout(
        guild: Guild,
        target: Member,
        moderator: Member,
        reason: String?
    ): ModerationCase {
        target.removeTimeout().reason(reason).await()

        val case = createCase(
            guildId = guild.idLong,
            actionType = ModerationCaseType.UNTIMEOUT,
            moderatorId = moderator.id,
            moderatorName = moderator.effectiveName,
            targetId = target.id,
            targetName = target.effectiveName,
            reason = reason,
            duration = null,
            active = false
        )

        auditService.log(guild.idLong, AuditActionType.MEMBER_UNMUTE)
            .withUser(moderator)
            .withTargetUser(target)
            .withAttribute("reason", reason)
            .withAttribute("case", case.caseNumber)
            .save()

        sendModlogEmbed(guild, case)
        return case
    }

    override suspend fun purgeMessages(channel: TextChannel, count: Int, filterUser: User?): Int {
        val messages = channel.iterableHistory.takeAsync(count).await()
        val filtered = if (filterUser != null) {
            messages.filter { it.author.id == filterUser.id }
        } else {
            messages
        }

        if (filtered.isEmpty()) return 0

        if (filtered.size == 1) {
            filtered.first().delete().await()
        } else {
            channel.purgeMessages(filtered)
        }

        return filtered.size
    }

    override suspend fun lockChannel(channel: TextChannel, moderator: Member, reason: String?) {
        val publicRole = channel.guild.publicRole
        channel.upsertPermissionOverride(publicRole)
            .deny(Permission.MESSAGE_SEND)
            .reason(reason)
            .await()
    }

    override suspend fun unlockChannel(channel: TextChannel, moderator: Member) {
        val publicRole = channel.guild.publicRole
        val override = channel.getPermissionOverride(publicRole)
        if (override != null) {
            override.manager.clear(Permission.MESSAGE_SEND).await()
        }
    }

    override fun getWarnings(guildId: Long, targetId: String): List<ModerationCase> {
        return caseRepository.findByGuildIdAndTargetIdAndActionTypeAndActive(
            guildId, targetId, ModerationCaseType.WARN, true
        )
    }

    @Transactional
    override fun clearWarnings(guildId: Long, targetId: String): Int {
        val warnings = getWarnings(guildId, targetId)
        warnings.forEach { it.active = false }
        caseRepository.saveAll(warnings)
        return warnings.size
    }

    override fun getCase(guildId: Long, caseNumber: Int): ModerationCase? {
        return caseRepository.findByGuildIdAndCaseNumber(guildId, caseNumber)
    }

    override fun getModLog(guildId: Long, targetId: String): List<ModerationCase> {
        return caseRepository.findByGuildIdAndTargetIdOrderByCaseNumberDesc(guildId, targetId)
    }

    private suspend fun checkEscalation(guild: Guild, target: Member, moderator: Member) {
        val config = moderationConfigService.getOrCreate(guild.idLong)
        val activeWarns = caseRepository.countByGuildIdAndTargetIdAndActionTypeAndActive(
            guild.idLong, target.id, ModerationCaseType.WARN, true
        )

        val rule = config.escalationRules
            .filter { it.threshold <= activeWarns }
            .maxByOrNull { it.threshold }
            ?: return

        when (rule.actionType) {
            ModerationActionType.MUTE -> {
                val request = ModerationActionRequest.build {
                    type = ModerationActionType.MUTE
                    violator = target
                    this.moderator = moderator
                    reason = "Auto-escalation: $activeWarns warnings reached"
                    global = true
                    duration = rule.duration
                }
                muteService.mute(request)
            }
            ModerationActionType.KICK -> kick(guild, target, moderator, "Auto-escalation: $activeWarns warnings reached")
            ModerationActionType.BAN -> ban(guild, target, moderator, "Auto-escalation: $activeWarns warnings reached", rule.duration, null)
            else -> {}
        }
    }

    private fun sendModlogEmbed(guild: Guild, case: ModerationCase) {
        val config = moderationConfigService.getByGuildId(guild.idLong) ?: return
        val channelId = config.modlogChannelId ?: return
        val channel = guild.getTextChannelById(channelId) ?: return

        val color = when (case.actionType) {
            ModerationCaseType.BAN -> Color.RED
            ModerationCaseType.UNBAN -> Color.GREEN
            ModerationCaseType.KICK -> Color.ORANGE
            ModerationCaseType.WARN -> Color.YELLOW
            ModerationCaseType.MUTE, ModerationCaseType.TIMEOUT -> Color.YELLOW
            ModerationCaseType.UNMUTE, ModerationCaseType.UNTIMEOUT -> Color.GREEN
        }

        val embed = EmbedBuilder()
            .setTitle("Case #${case.caseNumber} | ${case.actionType.name}")
            .setColor(color)
            .addField("User", "<@${case.targetId}> (${case.targetName})", true)
            .addField("Moderator", "<@${case.moderatorId}> (${case.moderatorName})", true)
            .apply {
                if (case.reason != null) addField("Reason", case.reason, false)
                if (case.duration != null) addField("Duration", DurationParser.format(case.duration!!), true)
            }
            .setTimestamp(case.createdAt)
            .build()

        channel.sendMessageEmbeds(embed).queue()
    }

    private suspend fun dmTarget(user: User, guild: Guild, action: String, reason: String?, duration: Long?) {
        try {
            val config = moderationConfigService.getByGuildId(guild.idLong) ?: return
            if (config.modlogChannelId == null) return // use modlog presence as proxy for DM config initially

            val dm = user.openPrivateChannel().await()
            val msg = buildString {
                append("You have been **$action** in **${guild.name}**.")
                if (reason != null) append("\n**Reason:** $reason")
                if (duration != null) append("\n**Duration:** ${DurationParser.format(duration)}")
            }
            dm.sendMessage(msg).await()
        } catch (e: Exception) {
            log.debug("Could not DM user {}: {}", user.id, e.message)
        }
    }

    private fun scheduleUnban(guildId: String, userId: String, duration: Long) {
        try {
            val jobKey = JobKey.jobKey("unban-$guildId-$userId", UnBanJob.GROUP)
            val jobDetail = JobBuilder.newJob(UnBanJob::class.java)
                .withIdentity(jobKey)
                .usingJobData(UnBanJob.ATTR_GUILD_ID, guildId)
                .usingJobData(UnBanJob.ATTR_USER_ID, userId)
                .build()

            val trigger = TriggerBuilder.newTrigger()
                .withIdentity("unban-trigger-$guildId-$userId", UnBanJob.GROUP)
                .startAt(Date.from(Instant.now().plusMillis(duration)))
                .build()

            schedulerFactoryBean.scheduler.scheduleJob(jobDetail, trigger)
        } catch (e: Exception) {
            log.error("Failed to schedule unban for user {} in guild {}", userId, guildId, e)
        }
    }
}
```

**Step 3: Verify compilation**

Run: `./gradlew :modules:sb-module-moderation:compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/service/
git commit -m "feat(moderation): implement IModerationService with case system, audit, modlog, DM, escalation"
```

---

### Task 6: UnBanJob (Quartz)

**Files:**
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/job/UnBanJob.kt`

**Step 1: Create UnBanJob**

```kotlin
package ru.sablebot.module.moderation.job

import org.quartz.JobExecutionContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.sablebot.common.worker.service.DiscordService
import ru.sablebot.common.worker.shared.support.AbstractJob

@Component
class UnBanJob : AbstractJob() {

    @Autowired
    private lateinit var discordService: DiscordService

    companion object {
        const val ATTR_USER_ID = "userId"
        const val ATTR_GUILD_ID = "guildId"
        const val GROUP = "UnBanJob-group"
    }

    override fun execute(context: JobExecutionContext) {
        if (!discordService.isConnected()) {
            context.rescheduleIn(java.time.Duration.ofMinutes(1), this)
            return
        }

        val data = context.jobDetail.jobDataMap
        val guildId = data.getString(ATTR_GUILD_ID) ?: return
        val userId = data.getString(ATTR_USER_ID) ?: return

        val guild = discordService.getShardManager()?.getGuildById(guildId) ?: return

        guild.unban(net.dv8tion.jda.api.entities.UserSnowflake.fromId(userId))
            .reason("Temporary ban expired")
            .queue(
                { /* success */ },
                { e -> log.error("Failed to unban user {} in guild {}", userId, guildId, e) }
            )
    }

    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)
}
```

**Step 2: Verify compilation**

Run: `./gradlew :modules:sb-module-moderation:compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/job/
git commit -m "feat(moderation): add UnBanJob for temporary ban expiry"
```

---

### Task 7: ModerationCommandPreconditions & Manual Moderation Commands (ban, unban, kick, warn, warnings, clear-warnings)

**Files:**
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/command/ModerationPreconditions.kt`
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/command/BanCommand.kt`
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/command/UnbanCommand.kt`
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/command/KickCommand.kt`
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/command/WarnCommand.kt`
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/command/WarningsCommand.kt`
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/command/ClearWarningsCommand.kt`

All commands follow the audio module DSL pattern: `@Component` implementing `SlashCommandDeclarationWrapper`, with inner executor class extending `SlashCommandExecutor`.

**Step 1: Create ModerationPreconditions**

```kotlin
package ru.sablebot.module.moderation.command

import net.dv8tion.jda.api.entities.Member
import ru.sablebot.common.model.exception.DiscordException

object ModerationPreconditions {

    fun requireCanModerate(moderator: Member, target: Member) {
        if (target.id == moderator.id) {
            throw DiscordException("You cannot moderate yourself.")
        }
        if (target.user.isBot) {
            throw DiscordException("You cannot moderate bots.")
        }
        if (!moderator.canInteract(target)) {
            throw DiscordException("You cannot moderate this user — they have a higher or equal role.")
        }
        val selfMember = moderator.guild.selfMember
        if (!selfMember.canInteract(target)) {
            throw DiscordException("I cannot moderate this user — they have a higher or equal role than me.")
        }
    }
}
```

**Step 2: Create BanCommand**

```kotlin
package ru.sablebot.module.moderation.command

import net.dv8tion.jda.api.DefaultMemberPermissions
import org.springframework.stereotype.Component
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.model.exception.DiscordException
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.command.model.dsl.slashCommand
import ru.sablebot.common.worker.message.model.commands.options.ApplicationCommandOptions
import ru.sablebot.module.moderation.model.DurationParser
import ru.sablebot.module.moderation.service.IModerationService
import java.util.UUID

@Component
class BanCommand(
    private val moderationService: IModerationService
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "ban", "Ban a user from the server",
        CommandCategory.MODERATION, UUID.fromString("b1000001-0000-0000-0000-000000000001")
    ) {
        defaultMemberPermissions = DefaultMemberPermissions.enabledFor(net.dv8tion.jda.api.Permission.BAN_MEMBERS)
        executor = BanExecutor()
    }

    inner class BanExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val target = user("user", "User to ban")
            val reason = optionalString("reason", "Reason for the ban")
            val duration = optionalString("duration", "Ban duration (e.g. 7d, 1w) — leave empty for permanent")
            val deleteDays = optionalString("delete_days", "Days of messages to delete (0-7)")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val guild = context.guild ?: throw DiscordException("This command can only be used in a server.")
                val moderator = context.member
                val targetData = args[options.target]
                val target = targetData.member ?: throw DiscordException("User is not in this server.")

                ModerationPreconditions.requireCanModerate(moderator, target)
                context.deferChannelMessage(false)

                val reason = args[options.reason]
                val durationStr = args[options.duration]
                val duration = durationStr?.let {
                    DurationParser.parse(it)?.toMillis() ?: throw DiscordException("Invalid duration format. Use: 1h, 30m, 7d, 1w")
                }
                val deleteDays = args[options.deleteDays]?.toIntOrNull()?.coerceIn(0, 7)

                val case = moderationService.ban(guild, target, moderator, reason, duration, deleteDays)

                val durationText = if (duration != null) " for ${DurationParser.format(duration)}" else ""
                context.reply(ephemeral = false, "Banned **${target.effectiveName}**$durationText. (Case #${case.caseNumber})")
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }
}
```

**Step 3: Create UnbanCommand**

```kotlin
package ru.sablebot.module.moderation.command

import net.dv8tion.jda.api.DefaultMemberPermissions
import org.springframework.stereotype.Component
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.model.exception.DiscordException
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.command.model.dsl.slashCommand
import ru.sablebot.common.worker.message.model.commands.options.ApplicationCommandOptions
import ru.sablebot.module.moderation.service.IModerationService
import java.util.UUID

@Component
class UnbanCommand(
    private val moderationService: IModerationService
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "unban", "Unban a user",
        CommandCategory.MODERATION, UUID.fromString("b1000001-0000-0000-0000-000000000002")
    ) {
        defaultMemberPermissions = DefaultMemberPermissions.enabledFor(net.dv8tion.jda.api.Permission.BAN_MEMBERS)
        executor = UnbanExecutor()
    }

    inner class UnbanExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val target = user("user", "User to unban")
            val reason = optionalString("reason", "Reason for the unban")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val guild = context.guild ?: throw DiscordException("This command can only be used in a server.")
                val moderator = context.member
                context.deferChannelMessage(false)

                val targetData = args[options.target]
                val reason = args[options.reason]

                val case = moderationService.unban(guild, targetData.user, moderator, reason)
                context.reply(ephemeral = false, "Unbanned **${targetData.user.effectiveName}**. (Case #${case.caseNumber})")
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }
}
```

**Step 4: Create KickCommand**

```kotlin
package ru.sablebot.module.moderation.command

import net.dv8tion.jda.api.DefaultMemberPermissions
import org.springframework.stereotype.Component
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.model.exception.DiscordException
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.command.model.dsl.slashCommand
import ru.sablebot.common.worker.message.model.commands.options.ApplicationCommandOptions
import ru.sablebot.module.moderation.service.IModerationService
import java.util.UUID

@Component
class KickCommand(
    private val moderationService: IModerationService
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "kick", "Kick a user from the server",
        CommandCategory.MODERATION, UUID.fromString("b1000001-0000-0000-0000-000000000003")
    ) {
        defaultMemberPermissions = DefaultMemberPermissions.enabledFor(net.dv8tion.jda.api.Permission.KICK_MEMBERS)
        executor = KickExecutor()
    }

    inner class KickExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val target = user("user", "User to kick")
            val reason = optionalString("reason", "Reason for the kick")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val guild = context.guild ?: throw DiscordException("This command can only be used in a server.")
                val moderator = context.member
                val targetData = args[options.target]
                val target = targetData.member ?: throw DiscordException("User is not in this server.")

                ModerationPreconditions.requireCanModerate(moderator, target)
                context.deferChannelMessage(false)

                val reason = args[options.reason]
                val case = moderationService.kick(guild, target, moderator, reason)

                context.reply(ephemeral = false, "Kicked **${target.effectiveName}**. (Case #${case.caseNumber})")
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }
}
```

**Step 5: Create WarnCommand**

```kotlin
package ru.sablebot.module.moderation.command

import net.dv8tion.jda.api.DefaultMemberPermissions
import org.springframework.stereotype.Component
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.model.exception.DiscordException
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.command.model.dsl.slashCommand
import ru.sablebot.common.worker.message.model.commands.options.ApplicationCommandOptions
import ru.sablebot.module.moderation.service.IModerationService
import java.util.UUID

@Component
class WarnCommand(
    private val moderationService: IModerationService
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "warn", "Issue a warning to a user",
        CommandCategory.MODERATION, UUID.fromString("b1000001-0000-0000-0000-000000000004")
    ) {
        defaultMemberPermissions = DefaultMemberPermissions.enabledFor(net.dv8tion.jda.api.Permission.MODERATE_MEMBERS)
        executor = WarnExecutor()
    }

    inner class WarnExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val target = user("user", "User to warn")
            val reason = string("reason", "Reason for the warning")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val guild = context.guild ?: throw DiscordException("This command can only be used in a server.")
                val moderator = context.member
                val targetData = args[options.target]
                val target = targetData.member ?: throw DiscordException("User is not in this server.")

                ModerationPreconditions.requireCanModerate(moderator, target)
                context.deferChannelMessage(false)

                val reason = args[options.reason]
                val case = moderationService.warn(guild, target, moderator, reason)

                val activeWarns = moderationService.getWarnings(guild.idLong, target.id).size
                context.reply(ephemeral = false, "Warned **${target.effectiveName}** — $activeWarns active warning(s). (Case #${case.caseNumber})")
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }
}
```

**Step 6: Create WarningsCommand**

```kotlin
package ru.sablebot.module.moderation.command

import net.dv8tion.jda.api.DefaultMemberPermissions
import net.dv8tion.jda.api.EmbedBuilder
import org.springframework.stereotype.Component
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.model.exception.DiscordException
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.command.model.dsl.slashCommand
import ru.sablebot.common.worker.message.model.commands.options.ApplicationCommandOptions
import ru.sablebot.module.moderation.service.IModerationService
import java.awt.Color
import java.util.UUID

@Component
class WarningsCommand(
    private val moderationService: IModerationService
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "warnings", "View active warnings for a user",
        CommandCategory.MODERATION, UUID.fromString("b1000001-0000-0000-0000-000000000005")
    ) {
        defaultMemberPermissions = DefaultMemberPermissions.enabledFor(net.dv8tion.jda.api.Permission.MODERATE_MEMBERS)
        executor = WarningsExecutor()
    }

    inner class WarningsExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val target = user("user", "User to check warnings for")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val guild = context.guild ?: throw DiscordException("This command can only be used in a server.")
                val targetData = args[options.target]
                val warnings = moderationService.getWarnings(guild.idLong, targetData.user.id)

                if (warnings.isEmpty()) {
                    context.reply(ephemeral = true, "**${targetData.user.effectiveName}** has no active warnings.")
                    return
                }

                val embed = EmbedBuilder()
                    .setTitle("Warnings for ${targetData.user.effectiveName}")
                    .setColor(Color.YELLOW)
                    .setDescription(warnings.joinToString("\n") { warn ->
                        "**Case #${warn.caseNumber}** — ${warn.reason ?: "No reason"}\n" +
                        "By <@${warn.moderatorId}> — <t:${warn.createdAt.epochSecond}:R>"
                    })
                    .setFooter("${warnings.size} active warning(s)")
                    .build()

                context.reply(ephemeral = true) { embeds += embed }
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }
}
```

**Step 7: Create ClearWarningsCommand**

```kotlin
package ru.sablebot.module.moderation.command

import net.dv8tion.jda.api.DefaultMemberPermissions
import org.springframework.stereotype.Component
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.model.exception.DiscordException
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.command.model.dsl.slashCommand
import ru.sablebot.common.worker.message.model.commands.options.ApplicationCommandOptions
import ru.sablebot.module.moderation.service.IModerationService
import java.util.UUID

@Component
class ClearWarningsCommand(
    private val moderationService: IModerationService
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "clear-warnings", "Clear all warnings for a user",
        CommandCategory.MODERATION, UUID.fromString("b1000001-0000-0000-0000-000000000006")
    ) {
        defaultMemberPermissions = DefaultMemberPermissions.enabledFor(net.dv8tion.jda.api.Permission.MODERATE_MEMBERS)
        executor = ClearWarningsExecutor()
    }

    inner class ClearWarningsExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val target = user("user", "User to clear warnings for")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val guild = context.guild ?: throw DiscordException("This command can only be used in a server.")
                context.deferChannelMessage(false)

                val targetData = args[options.target]
                val cleared = moderationService.clearWarnings(guild.idLong, targetData.user.id)

                context.reply(ephemeral = false, "Cleared **$cleared** warning(s) for **${targetData.user.effectiveName}**.")
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }
}
```

**Step 8: Verify compilation**

Run: `./gradlew :modules:sb-module-moderation:compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 9: Commit**

```bash
git add modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/command/
git commit -m "feat(moderation): add ban, unban, kick, warn, warnings, clear-warnings commands"
```

---

### Task 8: Remaining Manual Commands (timeout, untimeout, purge, slowmode, lock, unlock, case, modlog)

**Files:**
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/command/TimeoutCommand.kt`
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/command/UntimeoutCommand.kt`
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/command/PurgeCommand.kt`
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/command/SlowmodeCommand.kt`
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/command/LockCommand.kt`
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/command/UnlockCommand.kt`
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/command/CaseCommand.kt`
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/command/ModlogCommand.kt`

Follow the exact same pattern as Task 7 commands. Each command:
- `@Component` implementing `SlashCommandDeclarationWrapper`
- Inner executor extending `SlashCommandExecutor`
- Uses `context.deferChannelMessage(false)` before async operations
- Wraps in try/catch for `DiscordException`
- Sets `defaultMemberPermissions` appropriately
- Uses `CommandCategory.MODERATION`
- Delegates to `IModerationService`

Key details per command:
- **TimeoutCommand**: `MODERATE_MEMBERS` permission, parses duration string, calls `moderationService.timeout()`
- **UntimeoutCommand**: `MODERATE_MEMBERS` permission, calls `moderationService.removeTimeout()`
- **PurgeCommand**: `MESSAGE_MANAGE` permission, `count` integer option (1-100), optional `user` filter, calls `moderationService.purgeMessages()`
- **SlowmodeCommand**: `MANAGE_CHANNELS` permission, `seconds` integer option (0-21600), calls `channel.manager.setSlowmode(seconds).await()`
- **LockCommand**: `MANAGE_CHANNELS` permission, optional channel + reason, calls `moderationService.lockChannel()`
- **UnlockCommand**: `MANAGE_CHANNELS` permission, optional channel, calls `moderationService.unlockChannel()`
- **CaseCommand**: `MODERATE_MEMBERS` permission, `number` integer option, calls `moderationService.getCase()`, displays embed
- **ModlogCommand**: `MODERATE_MEMBERS` permission, `user` option, calls `moderationService.getModLog()`, displays paginated embed

**Commit:**

```bash
git add modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/command/
git commit -m "feat(moderation): add timeout, untimeout, purge, slowmode, lock, unlock, case, modlog commands"
```

---

### Task 9: ModConfig Command (subcommand group)

**Files:**
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/command/ModConfigCommand.kt`

This command uses subcommands: `modlog`, `dm`, `escalation add`, `escalation remove`, `escalation list`.

Follow the subcommand group pattern. The `slashCommand` builder supports `subcommand()` and `subcommandGroup()` methods.

Key implementation:
- `modlog` subcommand: takes `channel` option, saves `modlogChannelId` to `ModerationConfig`
- `dm` subcommand: takes `enable` boolean, updates `AutoModConfig.dmOnAction`
- `escalation` subcommand group with `add` / `remove` / `list` subcommands

Each subcommand has its own executor class. Uses `ModerationConfigService` and `AutoModConfigService` to persist settings.

**Commit:**

```bash
git add modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/command/ModConfigCommand.kt
git commit -m "feat(moderation): add /modconfig command with modlog, dm, escalation subcommands"
```

---

### Task 10: AutoMod Services

**Files:**
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/service/IAutoModConfigService.kt`
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/service/impl/AutoModConfigServiceImpl.kt`
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/service/IAutoModService.kt`
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/service/impl/AutoModServiceImpl.kt`
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/service/IRaidDetectionService.kt`
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/service/impl/RaidDetectionServiceImpl.kt`

**IAutoModConfigService**: Extends `DomainService<AutoModConfig>`, follows `ModerationConfigServiceImpl` pattern.

**AutoModServiceImpl**: Main auto-mod engine:
- `onMessage(message)`: runs all checks in sequence, executes first match
- `checkSpam()`: Caffeine cache keyed by `guildId:userId`, tracks message timestamps, evicts after window
- `checkWordFilter()`: regex matching against `wordFilterPatterns`
- `checkLinkFilter()`: URL extraction + domain matching
- `checkMentionSpam()`: count unique mentions in message
- `executeAction()`: delegates to `IModerationService` for warn/mute/kick, deletes message if needed

**RaidDetectionServiceImpl**:
- Caffeine cache keyed by `guildId`, stores list of join timestamps
- `onMemberJoin()`: adds timestamp, checks if count in window exceeds threshold
- If raid detected: executes configured action (kick/ban) on the joining member
- Also checks account age against `minAccountAgeDays`

**Commit:**

```bash
git add modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/service/
git commit -m "feat(moderation): implement auto-mod services (spam, word filter, link filter, mention spam, raid detection)"
```

---

### Task 11: AutoMod Event Listeners

**Files:**
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/listener/AutoModMessageListener.kt`
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/listener/RaidDetectionListener.kt`

**AutoModMessageListener**: JDA `ListenerAdapter`, listens to `MessageReceivedEvent`:
- Ignores bots
- Ignores members with `ADMINISTRATOR` or `MANAGE_MESSAGES` permission
- Calls `autoModService.onMessage(event.message)`

**RaidDetectionListener**: JDA `ListenerAdapter`, listens to `GuildMemberJoinEvent`:
- Calls `raidDetectionService.onMemberJoin(event.member)`

Both annotated with `@Component` for Spring auto-discovery.

**Commit:**

```bash
git add modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/listener/
git commit -m "feat(moderation): add AutoModMessageListener and RaidDetectionListener"
```

---

### Task 12: AutoMod Command (subcommand group)

**Files:**
- Create: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/command/AutoModCommand.kt`

Subcommand structure:
- `/automod antispam <enable> [max_messages] [window] [action] [mute_duration]`
- `/automod antiraid <enable> [join_threshold] [window] [min_account_age] [action]`
- `/automod wordfilter <enable> [action]` + `add <pattern>` + `remove <pattern>` + `list`
- `/automod linkfilter <enable> [mode] [action]` + `add <domain>` + `remove <domain>`
- `/automod mentionspam <enable> [threshold] [action]`
- `/automod status`

All subcommands require `ADMINISTRATOR` permission. Each reads/writes `AutoModConfig` via `IAutoModConfigService`.

**Commit:**

```bash
git add modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/command/AutoModCommand.kt
git commit -m "feat(moderation): add /automod command with antispam, antiraid, wordfilter, linkfilter, mentionspam subcommands"
```

---

### Task 13: Wire Module into sb-worker

**Files:**
- Modify: `sb-worker/build.gradle` (add dependency on `:modules:sb-module-moderation`)
- Verify Spring component scan picks up the new module's package

**Step 1: Add dependency**

In `sb-worker/build.gradle`, add:
```gradle
implementation project(":modules:sb-module-moderation")
```

**Step 2: Verify component scan**

Check that the `@SpringBootApplication` or `@ComponentScan` in the worker's main class covers `ru.sablebot.module.moderation`. If it uses a base package like `ru.sablebot`, it should work automatically.

**Step 3: Full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add sb-worker/build.gradle
git commit -m "feat(moderation): wire sb-module-moderation into sb-worker"
```

---

### Task 14: Final Review & Cleanup

**Step 1: Full compilation check**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL

**Step 2: Review all new files**

Verify:
- All commands registered with unique UUIDs
- All services properly annotated with `@Service`
- All repositories annotated with `@Repository`
- Liquibase changelog included in master changelog
- No circular dependencies

**Step 3: Commit any fixes**

```bash
git add -A
git commit -m "chore(moderation): cleanup and final review"
```
