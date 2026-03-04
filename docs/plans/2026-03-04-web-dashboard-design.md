# SableBot Web Dashboard — Design Document

**Date:** 2026-03-04
**Status:** Approved

## Overview

A full-featured web dashboard for SableBot providing server configuration, analytics, and moderation management. Consists of two components: a Spring Boot REST + WebSocket backend (sb-api) and a Next.js frontend (sb-dashboard).

## Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────┐
│  Next.js App     │────▶│  sb-api (Spring)  │────▶│ PostgreSQL  │
│  (sb-dashboard/) │◀───▶│  REST + WebSocket │◀───▶│  (shared)   │
└─────────────────┘     └──────┬───────────┘     └─────────────┘
                               │  ▲
                        Kafka  │  │  Kafka
                        consume│  │  publish
                               ▼  │
                        ┌──────────────────┐
                        │   sb-worker      │
                        │   (Discord bot)  │
                        └──────────────────┘
```

- **sb-api** connects directly to the shared PostgreSQL database via `sb-common` entities/repos
- **sb-worker** publishes events to Kafka; sb-api consumes them and pushes to WebSocket clients
- **sb-dashboard** is a standalone Next.js app communicating via REST + WebSocket

## Authentication

- **Discord OAuth2** for user login — redirect flow, exchange code for Discord tokens
- **JWT session tokens** issued by sb-api, stored in httpOnly cookies
- **API keys** per guild for programmatic access, with configurable scopes (read-only, config-write, moderation-write)
- Guild-level authorization: checks Discord `MANAGE_SERVER` permission (cached 5 min)

## Dashboard Pages

### Server Config (`/dashboard/[guildId]/config`)
- General: language, bot nickname
- Moderation: auto-mod toggles (spam, links, profanity, raid), thresholds, word blacklist, warn escalation rules
- Music: default volume, DJ role, max queue size, 24/7 toggle
- Audit: log channel, events to log

### Stats & Analytics (`/dashboard/[guildId]/stats`)
- Overview cards: members, active today, commands (24h), mod actions (7d)
- Charts: command usage, member growth, most used commands, audio stats
- Real-time updates via WebSocket

### Moderation Console (`/dashboard/[guildId]/moderation`)
- Case browser: searchable/filterable paginated table
- Case detail: target, moderator, reason, duration, evidence
- Quick actions: ban, kick, warn from dashboard
- Mod activity breakdown

### API Key Management (`/dashboard/[guildId]/api-keys`)
- Generate/revoke keys with scoped permissions
- Usage statistics per key

## API Design

### Auth
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/auth/discord` | Redirect to Discord OAuth2 |
| GET | `/api/auth/discord/callback` | Exchange code, issue JWT |
| POST | `/api/auth/logout` | Invalidate session |
| GET | `/api/auth/me` | Current user info + guilds |

### Guild Config
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/guilds/{id}` | Guild overview |
| GET | `/api/guilds/{id}/config` | Full config |
| PATCH | `/api/guilds/{id}/config` | Update config sections |
| GET | `/api/guilds/{id}/roles` | Available roles |
| GET | `/api/guilds/{id}/channels` | Available channels |

### Moderation
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/guilds/{id}/cases` | Paginated case list |
| GET | `/api/guilds/{id}/cases/{caseId}` | Case detail |
| POST | `/api/guilds/{id}/moderation/ban` | Execute ban |
| POST | `/api/guilds/{id}/moderation/kick` | Execute kick |
| POST | `/api/guilds/{id}/moderation/warn` | Issue warning |

### Stats
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/guilds/{id}/stats/overview` | Summary cards |
| GET | `/api/guilds/{id}/stats/commands` | Command usage over time |
| GET | `/api/guilds/{id}/stats/members` | Member growth |
| GET | `/api/guilds/{id}/stats/audio` | Audio playback stats |

### API Keys
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/guilds/{id}/api-keys` | List keys |
| POST | `/api/guilds/{id}/api-keys` | Create key |
| DELETE | `/api/guilds/{id}/api-keys/{keyId}` | Revoke key |

### WebSocket (STOMP)
| Endpoint | Description |
|----------|-------------|
| `/ws` | WebSocket handshake |
| `/topic/guild.{id}.stats` | Real-time stat updates |
| `/topic/guild.{id}.moderation` | New mod case events |
| `/topic/guild.{id}.audio` | Player state changes |

## Tech Stack

### sb-api (Backend)
- Spring Boot 3.5.6, Kotlin
- Depends on `sb-common` for entities/repos
- Spring Security with Discord OAuth2
- Spring WebSocket (STOMP + SockJS)
- JWT (jjwt) for session tokens
- API key auth filter
- Spring Kafka consumer for real-time events

### sb-dashboard (Frontend)
- Next.js 14+ with App Router, TypeScript
- Tailwind CSS + shadcn/ui
- Recharts for analytics
- STOMP.js for WebSocket client

## Project Structure

```
sb-dashboard/
  src/
    app/
      page.tsx                    # Landing / login
      dashboard/[guildId]/
        page.tsx                  # Guild overview
        config/page.tsx           # Server config
        stats/page.tsx            # Analytics
        moderation/page.tsx       # Mod console
        api-keys/page.tsx         # API key mgmt
    components/
      ui/                        # shadcn components
      layout/                    # Navbar, Sidebar
      charts/                    # Recharts wrappers
      moderation/                # Case table, detail
    lib/
      api.ts                     # REST client
      ws.ts                      # WebSocket client
      auth.ts                    # Auth utilities
    types/                       # TypeScript types

sb-api/
  src/main/kotlin/ru/sablebot/api/
    controller/                  # REST controllers
    security/                    # OAuth2, JWT, API key
    websocket/                   # STOMP config, handlers
    service/                     # Dashboard services
    dto/                         # Request/response DTOs
```

## Kafka Event Bridge

New topics published by sb-worker:
- `sablebot.events.moderation` — case created/updated
- `sablebot.events.stats` — command executed, member joined/left
- `sablebot.events.audio` — track started, player paused, queue updated

sb-api consumes these and broadcasts to WebSocket subscribers per guild.

## Security

- Consistent error responses: `{ "error": "code", "message": "...", "status": N }`
- Rate limiting: auth (10 req/min), mod actions (30 req/min)
- CORS: dashboard origin only
- CSRF: SameSite cookies
- API keys: stored hashed (bcrypt), shown once on creation
- Discord permissions re-validated per request (5 min cache)
- WebSocket: JWT authentication in handshake

## Docker Compose Additions

- `sablebot-api` service (port 8080)
- `sablebot-dashboard` service (port 3000)
- Nginx reverse proxy: `/api` → sb-api, `/` → sb-dashboard

## Deployment

Both services added to existing `docker-compose.yml`. sb-api shares the same PostgreSQL and Kafka instances. sb-dashboard built as a Docker image with `next build` + `next start`.
