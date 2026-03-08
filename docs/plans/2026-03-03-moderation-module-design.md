# Moderation Module Design

**Date:** 2026-03-03
**Status:** Approved
**Module:** `sb-module-moderation`

## Overview

Full-featured moderation module for SableBot, covering manual moderation commands, a numbered case system, warning escalation, and a complete auto-moderation suite (anti-spam, anti-raid, word filter, link filter, mention spam protection).

## Data Model

### New Entity: `ModerationCase`

Every mod action creates a numbered case.

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT PK | Auto-generated |
| `guild_id` | BIGINT | Guild reference |
| `case_number` | INT | Per-guild sequential number |
| `action_type` | ENUM(STRING) | BAN, KICK, WARN, MUTE, TIMEOUT, UNBAN, UNMUTE |
| `moderator_id` | VARCHAR(21) | Who performed the action |
| `moderator_name` | VARCHAR | Name snapshot |
| `target_id` | VARCHAR(21) | Target user |
| `target_name` | VARCHAR | Name snapshot |
| `reason` | TEXT | Reason (nullable) |
| `duration` | BIGINT | Duration in millis (nullable) |
| `created_at` | TIMESTAMP | Action timestamp |
| `active` | BOOLEAN | Whether action is currently active |

Indexes: `(guild_id, case_number)`, `(guild_id, target_id)`.

### New Entity: `WarnEscalationRule`

Child of `ModerationConfig`. Configurable thresholds for auto-escalation.

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT PK | Auto-generated |
| `config_id` | BIGINT FK | Parent ModerationConfig |
| `threshold` | INT | Number of active warnings |
| `action_type` | ENUM | MUTE, KICK, BAN |
| `duration` | BIGINT | Duration in millis (nullable) |

### New Entity: `AutoModConfig`

Per-guild auto-moderation settings.

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT PK | Auto-generated |
| `guild_id` | BIGINT | Guild reference |
| `anti_spam_enabled` | BOOLEAN | Toggle |
| `anti_spam_max_messages` | INT | Max messages in window (default 5) |
| `anti_spam_window_seconds` | INT | Time window (default 5) |
| `anti_spam_action` | ENUM | WARN, MUTE, KICK |
| `anti_spam_mute_duration` | BIGINT | Millis |
| `anti_raid_enabled` | BOOLEAN | Toggle |
| `anti_raid_join_threshold` | INT | Joins per window (default 10) |
| `anti_raid_window_seconds` | INT | Time window (default 10) |
| `anti_raid_min_account_age_days` | INT | Minimum account age (default 7) |
| `anti_raid_action` | ENUM | KICK, BAN |
| `word_filter_enabled` | BOOLEAN | Toggle |
| `word_filter_patterns` | JSONB | List of regex/word patterns |
| `word_filter_action` | ENUM | WARN, MUTE, DELETE |
| `link_filter_enabled` | BOOLEAN | Toggle |
| `link_filter_mode` | ENUM | WHITELIST, BLACKLIST |
| `link_filter_domains` | JSONB | List of domain patterns |
| `link_filter_action` | ENUM | WARN, MUTE, DELETE |
| `mention_spam_enabled` | BOOLEAN | Toggle |
| `mention_spam_threshold` | INT | Max unique mentions (default 5) |
| `mention_spam_action` | ENUM | WARN, MUTE |
| `dm_on_action` | BOOLEAN | DM targets on mod actions |

### Alterations to Existing `ModerationConfig`

- Add `modlog_channel_id` (BIGINT, nullable) for modlog embed channel.

## Commands

### Manual Moderation

| Command | Options | Permission | Description |
|---|---|---|---|
| `/ban <user> [reason] [duration] [delete_days]` | user, reason?, duration? ("7d"), delete_days? (0-7) | BAN_MEMBERS | Ban, optionally temporary |
| `/unban <user> [reason]` | user, reason? | BAN_MEMBERS | Unban |
| `/kick <user> [reason]` | user, reason? | KICK_MEMBERS | Kick |
| `/warn <user> <reason>` | user, reason | MODERATE_MEMBERS | Warn, checks escalation |
| `/warnings <user>` | user | MODERATE_MEMBERS | List active warnings |
| `/clear-warnings <user>` | user | MODERATE_MEMBERS | Clear warnings |
| `/timeout <user> <duration> [reason]` | user, duration, reason? | MODERATE_MEMBERS | Discord native timeout |
| `/untimeout <user> [reason]` | user, reason? | MODERATE_MEMBERS | Remove timeout |
| `/purge <count> [user] [before]` | count (1-100), user?, before? | MESSAGE_MANAGE | Bulk delete |
| `/slowmode <seconds>` | seconds (0-21600) | MANAGE_CHANNELS | Set slowmode |
| `/lock [channel] [reason]` | channel?, reason? | MANAGE_CHANNELS | Lock channel |
| `/unlock [channel]` | channel? | MANAGE_CHANNELS | Unlock channel |
| `/case <number>` | number | MODERATE_MEMBERS | View case |
| `/modlog <user>` | user | MODERATE_MEMBERS | User's mod history |

### Auto-Moderation Configuration

| Command | Description |
|---|---|
| `/automod antispam <enable> [max_messages] [window] [action] [mute_duration]` | Anti-spam settings |
| `/automod antiraid <enable> [join_threshold] [window] [min_account_age] [action]` | Anti-raid settings |
| `/automod wordfilter <enable> [action]` | Toggle word filter |
| `/automod wordfilter add <pattern>` | Add pattern |
| `/automod wordfilter remove <pattern>` | Remove pattern |
| `/automod wordfilter list` | List patterns |
| `/automod linkfilter <enable> [mode] [action]` | Link filter settings |
| `/automod linkfilter add <domain>` | Add domain |
| `/automod linkfilter remove <domain>` | Remove domain |
| `/automod mentionspam <enable> [threshold] [action]` | Mention spam settings |
| `/automod status` | Show all auto-mod settings |

### Moderation Config

| Command | Description |
|---|---|
| `/modconfig modlog <channel>` | Set modlog channel |
| `/modconfig dm <enable>` | Toggle DM notifications |
| `/modconfig escalation add <threshold> <action> [duration]` | Add escalation rule |
| `/modconfig escalation remove <threshold>` | Remove rule |
| `/modconfig escalation list` | List rules |

## Services

### `IModerationService`

Core orchestrator. Each action:
1. Performs Discord action (JDA call)
2. Creates `ModerationCase` in DB
3. Logs via `AuditService`
4. Sends modlog embed to configured channel
5. Optionally DMs target
6. For warns: checks escalation rules

Methods:
- `ban(request): ModerationCase`
- `unban(guild, targetId, moderator, reason): ModerationCase`
- `kick(request): ModerationCase`
- `warn(guild, target, moderator, reason): ModerationCase`
- `getWarnings(guildId, targetId): List<ModerationCase>`
- `clearWarnings(guildId, targetId, moderator): Int`
- `timeout(guild, target, moderator, duration, reason): ModerationCase`
- `removeTimeout(guild, target, moderator, reason): ModerationCase`
- `purgeMessages(channel, count, filterUser?): Int`
- `lockChannel(channel, moderator, reason): Unit`
- `unlockChannel(channel, moderator): Unit`
- `getCase(guildId, caseNumber): ModerationCase?`
- `getModLog(guildId, targetId): List<ModerationCase>`

### `IAutoModService`

Auto-moderation engine. Entry point: `onMessage(message)`.
- `checkSpam(message): AutoModResult?`
- `checkWordFilter(message): AutoModResult?`
- `checkLinkFilter(message): AutoModResult?`
- `checkMentionSpam(message): AutoModResult?`
- `executeAction(result): Unit`

Uses Caffeine cache for rate tracking.

### `IAutoModConfigService`

Extends `DomainService<AutoModConfig>`, follows existing config pattern.

### `IRaidDetectionService`

- `onMemberJoin(member): Unit`
- `isRaidDetected(guildId): Boolean`

Uses Caffeine sliding window for join tracking.

## Quartz Jobs

- **`UnBanJob`** — temp-ban expiry, mirrors `UnMuteJob` pattern
- Existing `UnMuteJob` reused for mute expiry

## Event Listeners

- **`AutoModMessageListener`** — `MessageReceivedEvent` → `IAutoModService.onMessage()`
- **`RaidDetectionListener`** — `GuildMemberJoinEvent` → `IRaidDetectionService.onMemberJoin()`

## Module Structure

```
modules/sb-module-moderation/
├── build.gradle
├── src/main/kotlin/ru/sablebot/module/moderation/
│   ├── config/
│   │   └── ModerationModuleConfig.kt
│   ├── command/
│   │   ├── BanCommand.kt
│   │   ├── UnbanCommand.kt
│   │   ├── KickCommand.kt
│   │   ├── WarnCommand.kt
│   │   ├── WarningsCommand.kt
│   │   ├── ClearWarningsCommand.kt
│   │   ├── TimeoutCommand.kt
│   │   ├── UntimeoutCommand.kt
│   │   ├── PurgeCommand.kt
│   │   ├── SlowmodeCommand.kt
│   │   ├── LockCommand.kt
│   │   ├── UnlockCommand.kt
│   │   ├── CaseCommand.kt
│   │   ├── ModlogCommand.kt
│   │   ├── AutoModCommand.kt
│   │   └── ModConfigCommand.kt
│   ├── service/
│   │   ├── IModerationService.kt
│   │   ├── IAutoModService.kt
│   │   ├── IAutoModConfigService.kt
│   │   ├── IRaidDetectionService.kt
│   │   └── impl/
│   │       ├── ModerationServiceImpl.kt
│   │       ├── AutoModServiceImpl.kt
│   │       ├── AutoModConfigServiceImpl.kt
│   │       └── RaidDetectionServiceImpl.kt
│   ├── listener/
│   │   ├── AutoModMessageListener.kt
│   │   └── RaidDetectionListener.kt
│   ├── model/
│   │   ├── AutoModResult.kt
│   │   └── DurationParser.kt
│   └── job/
│       └── UnBanJob.kt
└── src/main/resources/
    └── db/changelog/
        └── moderation-changelog.xml
```

## Key Design Decisions

- **Separate Gradle module** following audio module pattern
- **Reuse existing `MuteService`** — `IModerationService` delegates to it, wrapping with case creation
- **Reuse existing `ModerationActionRequest`** — compatible with `MuteService.mute()`
- **Per-guild case numbers** via `SELECT MAX(case_number) + 1` with row locking
- **Caffeine cache** for auto-mod rate limiting (no DB writes for transient counters)
- **Shared duration parser** — "30m", "1h", "7d", "1w" → millis
- **English only** for initial release, i18n added later
- **DM notifications are per-guild toggleable** (off by default)

## Liquibase Migrations

1. Create `moderation_case` table
2. Create `warn_escalation_rule` table
3. Create `automod_config` table
4. Alter `mod_config`: add `modlog_channel_id` column
