# Music Module Full Rewrite - Design Document

**Date:** 2026-03-01
**Branch:** feature/mr-30
**Status:** Approved

## Overview

Full rewrite of the `sb-module-audio` music module for SableBot. Replaces existing partial implementation with production-ready module featuring 21 slash commands, Discord button-based player UI, audio filters, saved playlists, i18n support, and comprehensive metrics.

## Technology Stack

- **Audio backend:** Lavalink v4 via `dev.arbjerg:lavalink-client:3.3.0`
- **Command pattern:** Modern DSL (`SlashCommandDeclarationWrapper` + `SlashCommandExecutor`)
- **Player UI:** Discord button components (replacing reaction-based controls)
- **i18n:** Spring `MessageSource` with `ResourceBundleMessageSource`
- **Persistence:** JPA/Hibernate for playlists
- **Filters:** Lavalink v4 native filter API

## Architecture: Layered Services

All code lives in `modules/sb-module-audio` under `ru.sablebot.module.audio`.

```
ru/sablebot/module/audio/
├── model/                    # Domain models
├── service/                  # Interfaces
├── service/impl/             # Core implementations
├── service/helper/           # Support services
├── command/                  # Slash commands (one per command)
├── interaction/              # Button/component handlers
├── i18n/                     # Translations
└── config/                   # Spring configuration
```

## 1. Data Models

### TrackRequest (enhanced)

Keep existing structure, clean up:
- Remove `endMemberId` (track end context via `EndReason`)
- Keep all Lavalink v4 metadata: `encodedTrack`, `title`, `author`, `uri`, `lengthMs`, `artworkUrl`, `isStream`, `isSeekable`, `sourceName`, `isrc`, `identifier`
- Keep state fields: `endReason`, `resetMessage`, `resetOnResume`, `timeCode`

### PlaybackInstance (enhanced)

Add to existing:
- `volume: Int` - per-guild volume level
- `activeFilter: FilterPreset?` - current audio filter
- `twentyFourSeven: Boolean` - prevents auto-disconnect
- `moveTo(from: Int, to: Int): Boolean` - reorder queue
- `skipTo(index: Int): TrackRequest?` - jump to specific track
- `clear(): Int` - clear queue keeping current track

Fix: `shuffleUpcoming()` in-place shuffle correctness.

### FilterPreset (new)

```kotlin
enum class FilterPreset(val displayName: String) {
    BASSBOOST("Bass Boost"),
    NIGHTCORE("Nightcore"),
    VAPORWAVE("Vaporwave"),
    KARAOKE("Karaoke"),
    EIGHT_D("8D Audio"),
    TREMOLO("Tremolo"),
    VIBRATO("Vibrato"),
    NONE("None");
}
```

Each preset maps to Lavalink v4 filter parameters (equalizer bands, timescale, rotation, etc.).

### SearchSource (new)

```kotlin
enum class SearchSource(val prefix: String) {
    YOUTUBE("ytsearch:"),
    YOUTUBE_MUSIC("ytmsearch:"),
    SOUNDCLOUD("scsearch:"),
    DIRECT("")
}
```

### Playlist (new JPA entity, in sb-common)

```kotlin
@Entity
@Table(name = "playlist")
class Playlist : BaseEntity() {
    var name: String
    var ownerId: Long
    var guildId: Long?
    @JdbcTypeCode(SqlTypes.JSON)
    var tracks: List<PlaylistTrack>
}

data class PlaylistTrack(
    val encodedTrack: String,
    val title: String?,
    val author: String?,
    val uri: String?,
    val lengthMs: Long
)
```

### EndReason (migrated)

Migrate from lavaplayer's `AudioTrackEndReason` to Lavalink v4's `Message.EmittedEvent.TrackEndEvent.AudioTrackEndReason`. The PlayerServiceImpl already uses the v4 enum internally.

### RepeatMode (keep as-is)

`CURRENT`, `ALL`, `NONE` with emoji representations.

## 2. Service Layer

### PlayerServiceV4 (extended interface)

New methods added to existing interface:
```kotlin
suspend fun seek(guild: Guild, positionMs: Long): Boolean
suspend fun setVolume(guild: Guild, volume: Int): Boolean
suspend fun applyFilter(guild: Guild, preset: FilterPreset): Boolean
fun moveTo(guild: Guild, from: Int, to: Int): Boolean
fun skipTo(guild: Guild, index: Int): TrackRequest?
fun clearQueue(guild: Guild): Int
fun set247(guild: Guild, enabled: Boolean)
```

### PlayerServiceImpl (rewrite)

Key changes:
- **Fix `connectToChannel()` TODO** - implement via `lavaAudioService.connect(voiceChannel)` with proper member voice state checks
- **Structured concurrency** - scope tied to Spring lifecycle (`@PreDestroy` cancellation)
- **`loadAndPlay()`** - handle direct URLs, search queries with `SearchSource` prefix, playlist URLs
- **Auto-disconnect** - configurable timeout, respects 24/7 mode
- **Auto-disconnect on empty channel** - leave when last user departs (unless 24/7)

### IFilterService / FilterServiceImpl (new)

```kotlin
interface IFilterService {
    suspend fun apply(guildId: Long, preset: FilterPreset)
    suspend fun clear(guildId: Long)
    fun current(guildId: Long): FilterPreset?
}
```

Calls Lavalink v4 player update endpoint with filter payloads.

### IPlaylistService / PlaylistServiceImpl (new)

```kotlin
interface IPlaylistService {
    fun create(ownerId: Long, guildId: Long?, name: String, tracks: List<PlaylistTrack>): Playlist
    fun delete(playlistId: Long, requesterId: Long): Boolean
    fun getByOwner(ownerId: Long): List<Playlist>
    fun getByGuild(guildId: Long): List<Playlist>
    fun get(playlistId: Long): Playlist?
    fun addTracks(playlistId: Long, tracks: List<PlaylistTrack>)
    fun removeTracks(playlistId: Long, indices: List<Int>)
    suspend fun loadAndPlay(playlistId: Long, guild: Guild, member: Member, channel: TextChannel)
}
```

### AudioMessageManager (rewrite)

- Replace reaction-based controls with Discord button components
- Button row: `[Pause/Resume] [Skip] [Stop] [Repeat] [Shuffle]`
- Embed: track title, author, progress bar, duration, requester, volume, repeat mode, queue size
- Queue preview in embed (next 2-3 tracks)
- Auto-refresh on `panelRefreshInterval` (default 5000ms)
- Paginated queue display with forward/back buttons

### ValidationService (keep)

Existing queue validation (streams, duration, duplicates, queue size per user). No changes needed.

### PlayerListenerAdapter (keep, clean up)

Handles Lavalink v4 events. Minor cleanup only.

## 3. Slash Commands

All commands use `SlashCommandDeclarationWrapper` DSL. Category: `CommandCategory.MUSIC` (new enum value to add).

| Command | Options | Description |
|---------|---------|-------------|
| `/play <query>` | `query: String` (required) | Play a track/URL or search |
| `/pause` | — | Pause playback |
| `/resume` | — | Resume playback |
| `/skip` | `to: Int?` (optional) | Skip current or skip to track N |
| `/stop` | — | Stop playback and clear queue |
| `/queue` | `page: Int?` (optional) | Show current queue |
| `/nowplaying` | — | Show current track info |
| `/volume <level>` | `level: Int` (1-150) | Set volume |
| `/repeat <mode>` | `mode: String` choices: none/track/queue | Set repeat mode |
| `/shuffle` | — | Shuffle upcoming tracks |
| `/seek <position>` | `position: String` (e.g. "1:30", "90") | Seek to position |
| `/remove <position>` | `position: Int` | Remove track at position |
| `/clear` | — | Clear queue, keep current track |
| `/move <from> <to>` | `from: Int`, `to: Int` | Move track in queue |
| `/join` | `channel: VoiceChannel?` | Join voice channel |
| `/disconnect` | — | Disconnect from voice |
| `/filter <preset>` | `preset: String` choices from FilterPreset | Apply audio filter |
| `/lyrics` | `query: String?` (optional) | Show lyrics for current/specified track |
| `/playlist` | subcommands: save/load/delete/list/show | Manage saved playlists |
| `/history` | — | Show recently played tracks |
| `/247` | — | Toggle 24/7 mode |

### Common Preconditions

Shared utility functions for command preconditions:
- `requireVoiceChannel(member)` - must be in a voice channel
- `requireSameChannel(member, guild)` - must be in same channel as bot
- `requireMusicRole(member, musicConfig)` - must have required role (if configured)
- `requireActivePlayer(guild)` - must have active playback

### Command Example Pattern

```kotlin
@Component
class PlayCommand(
    private val playerService: PlayerServiceV4,
    private val audioService: ILavalinkV4AudioService
) : SlashCommandDeclarationWrapper {

    override fun command() = slashCommand(
        "play", "Play a track or search for music",
        CommandCategory.MUSIC, UUID.fromString("...")
    ) {
        executor = PlayExecutor()
    }

    inner class PlayExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val query = string("query", "Track URL or search query")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val query = args[options.query]
            context.deferChannelMessage(false)
            playerService.loadAndPlay(context.channel as TextChannel, context.member, query)
        }
    }
}
```

## 4. Button Interactions

### Player Panel Layout

```
┌─────────────────────────────────────────┐
│ Now Playing                              │
│                                          │
│ **Track Title**                          │
│ by Author Name                           │
│                                          │
│ ▓▓▓▓▓▓▓▓▓░░░░░░░░░░ 2:34 / 5:12       │
│                                          │
│ Requested by: @User                      │
│ Volume: 80% | Repeat: Off | Queue: 5    │
│                                          │
│ **Up Next:**                             │
│ 1. Next Track - Artist                   │
│ 2. Another Track - Artist                │
├─────────────────────────────────────────┤
│ [Pause] [Skip] [Stop] [Repeat] [Shuffle] │
└─────────────────────────────────────────┘
```

### PlayerButtonHandler

- Spring component listening for `ButtonInteractionEvent`
- Custom IDs: `audio:pause`, `audio:skip`, `audio:stop`, `audio:repeat`, `audio:shuffle`
- Same precondition checks as commands
- Updates panel message after each action

### QueuePaginationHandler

- `/queue` shows 10 tracks per page
- Buttons: `[Previous] [Page X/N] [Next]`
- Custom IDs: `audio:queue:prev:{guildId}`, `audio:queue:next:{guildId}`

## 5. Internationalization (i18n)

### Approach

- Spring `ResourceBundleMessageSource` with basename `audio`
- Locale from `context.discordGuildLocale` or `context.discordUserLocale`
- Property files: `audio_en.properties`, `audio_ru.properties`

### Message Key Examples

```properties
# audio_en.properties
audio.play.now_playing=Now Playing
audio.play.added_to_queue=Added to queue
audio.play.playlist_added={0} tracks added to queue
audio.queue.empty=The queue is empty
audio.queue.title=Queue ({0} tracks)
audio.error.not_in_voice=You must be in a voice channel
audio.error.not_same_channel=You must be in the same voice channel as the bot
audio.error.no_results=No results found for: {0}
audio.error.load_failed=Failed to load track
audio.filter.applied=Applied filter: {0}
audio.filter.cleared=Filters cleared
audio.volume.set=Volume set to {0}%
audio.repeat.set=Repeat mode: {0}
audio.247.enabled=24/7 mode enabled
audio.247.disabled=24/7 mode disabled
```

## 6. Database Changes

### New Table: playlist

```sql
-- Liquibase changelog
CREATE TABLE playlist (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    owner_id BIGINT NOT NULL,
    guild_id BIGINT,
    tracks JSON NOT NULL DEFAULT '[]',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_playlist_owner ON playlist(owner_id);
CREATE INDEX idx_playlist_guild ON playlist(guild_id);
```

### CommandCategory Update

Add `MUSIC` to `CommandCategory` enum in `sb-common`.

## 7. Error Handling

- **Voice disconnect:** Auto-stop after configurable inactivity timeout (unless 24/7)
- **Empty voice channel:** Auto-disconnect when last user leaves (unless 24/7)
- **Track load failure:** Send error embed, try next track in queue
- **Lavalink node failure:** Graceful degradation via existing node management
- **Queue overflow:** Validated by `ValidationService`
- **Concurrent modifications:** `PlaybackInstance` synchronized blocks
- **Permission errors:** Catch `InsufficientPermissionException`, send user-friendly message

## 8. Metrics

Extending existing Micrometer metrics:

| Metric | Type | Tags |
|--------|------|------|
| `sablebot.audio.commands.used` | Counter | command |
| `sablebot.audio.filters.applied` | Counter | preset |
| `sablebot.audio.playlists.loaded` | Counter | — |
| `sablebot.audio.twentyfour_seven.active` | Gauge | — |

Existing metrics kept: `sablebot.audio.active.sessions`, `sablebot.audio.tracks.started/ended/exceptions/stuck`, `sablebot.lavalink.*`

## 9. Files to Delete

- `modules/sb-module-audio/.../utils/MessageController.kt` (reaction controls, replaced by buttons)
- `modules/sb-module-audio/.../utils/AudioUtils.kt` (mostly empty)

## 10. Files to Keep (with modifications)

- `DefaultAudioServiceImpl.kt` - keep, minor cleanup
- `PlayerListenerAdapter.kt` - keep, clean up
- `ValidationService.kt` - keep as-is
- `ILavalinkV4AudioService.kt` - keep as-is
- `IAudioSearchProvider.kt` - keep as-is

## 11. Configuration

No new configuration properties needed. Existing `WorkerProperties.Audio` covers:
- `panelRefreshInterval` (5000ms)
- `lavalink.enabled`, `lavalink.nodes`, `lavalink.discovery`
- `searchProvider`
- `resamplingQuality`
