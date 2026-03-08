# SableBot Web Dashboard — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a full-featured web dashboard with server config, analytics, moderation console, and API key management for SableBot.

**Architecture:** sb-api (Spring Boot backend with shared PostgreSQL via sb-common, Kafka event bridge, WebSocket/STOMP) + sb-dashboard (Next.js 14+ frontend with Tailwind/shadcn/ui). Discord OAuth2 + JWT + API keys for auth.

**Tech Stack:** Kotlin/Spring Boot 3.5.6, Spring Security OAuth2, Spring WebSocket STOMP, jjwt, Next.js 14+, TypeScript, Tailwind CSS, shadcn/ui, Recharts, STOMP.js

---

## Phase 1: Backend Foundation (sb-api)

### Task 1: Re-enable sb-api module and update dependencies

**Files:**
- Modify: `settings.gradle:2` — uncomment sb-api include
- Modify: `sb-api/build.gradle` — update dependencies for dashboard needs
- Modify: `sb-api/src/main/resources/application.properties` → rename to `application.yml`

**Steps:**
1. Uncomment `include ':sb-api'` in `settings.gradle`
2. Update `sb-api/build.gradle`:
   - Remove unused deps (twitch4j, vk-sdk, rome, opencv)
   - Add: `spring-boot-starter-websocket`, `spring-kafka`, `jjwt-api:0.12.6`, `jjwt-impl:0.12.6`, `jjwt-jackson:0.12.6`
   - Add: `spring-boot-starter-actuator`, `micrometer-registry-prometheus`
   - Keep: spring-boot-starter-web, spring-boot-starter-security, spring-boot-starter-oauth2-client, mapstruct, sb-common
3. Create `application.yml` with: server port 8080, PostgreSQL datasource (same as sb-worker), Kafka bootstrap servers, Discord OAuth2 client config (client-id/secret as env vars), JWT secret as env var, CORS allowed origins
4. Verify build: `./gradlew :sb-api:compileKotlin`
5. Commit

### Task 2: JWT token service

**Files:**
- Create: `sb-api/src/main/kotlin/ru/sablebot/api/security/service/JwtTokenService.kt`
- Create: `sb-api/src/main/kotlin/ru/sablebot/api/security/filter/JwtAuthenticationFilter.kt`
- Create: `sb-api/src/main/kotlin/ru/sablebot/api/security/config/JwtProperties.kt`

**Steps:**
1. Create `JwtProperties` — `@ConfigurationProperties("sablebot.jwt")` with `secret`, `expirationMs` (default 24h)
2. Create `JwtTokenService` — generateToken(userId, guildIds), validateToken(token), extractUserId(token), using jjwt
3. Create `JwtAuthenticationFilter` — extends OncePerRequestFilter, reads JWT from httpOnly cookie `sb_token`, sets SecurityContext
4. Verify build
5. Commit

### Task 3: Discord OAuth2 auth controller

**Files:**
- Create: `sb-api/src/main/kotlin/ru/sablebot/api/controller/AuthController.kt`
- Create: `sb-api/src/main/kotlin/ru/sablebot/api/service/DiscordApiService.kt`
- Create: `sb-api/src/main/kotlin/ru/sablebot/api/dto/auth/AuthDtos.kt`
- Modify: `sb-api/src/main/kotlin/ru/sablebot/api/configuration/SecurityConfig.kt`

**Steps:**
1. Create `DiscordApiService` — wraps Discord REST API calls: exchangeCode(code) → tokens, getCurrentUser(accessToken) → user info, getUserGuilds(accessToken) → guild list with permissions
2. Create DTOs: `UserInfoResponse` (id, username, avatar, guilds), `GuildInfoResponse` (id, name, icon, permissions, botPresent)
3. Create `AuthController`:
   - `GET /api/auth/discord` — redirects to Discord OAuth2 authorize URL
   - `GET /api/auth/discord/callback` — exchanges code, fetches user+guilds, issues JWT in httpOnly cookie, redirects to dashboard
   - `POST /api/auth/logout` — clears cookie
   - `GET /api/auth/me` — returns current user info from JWT
4. Update `SecurityConfig` — add JwtAuthenticationFilter, permit auth endpoints, configure CORS for dashboard origin
5. Commit

### Task 4: Guild authorization middleware

**Files:**
- Create: `sb-api/src/main/kotlin/ru/sablebot/api/security/service/GuildPermissionService.kt`
- Create: `sb-api/src/main/kotlin/ru/sablebot/api/security/annotation/RequireGuildPermission.kt`
- Create: `sb-api/src/main/kotlin/ru/sablebot/api/security/interceptor/GuildPermissionInterceptor.kt`

**Steps:**
1. Create `GuildPermissionService` — checks if user has required Discord permission in guild, caches results for 5 min (Caffeine)
2. Create `@RequireGuildPermission(Permission.MANAGE_SERVER)` annotation
3. Create `GuildPermissionInterceptor` — AOP interceptor that validates guild access on annotated controller methods, extracts guildId from path variable
4. Commit

### Task 5: Guild config endpoints

**Files:**
- Create: `sb-api/src/main/kotlin/ru/sablebot/api/controller/GuildConfigController.kt`
- Create: `sb-api/src/main/kotlin/ru/sablebot/api/service/GuildConfigService.kt`
- Create: `sb-api/src/main/kotlin/ru/sablebot/api/dto/config/ConfigDtos.kt`

**Steps:**
1. Create config DTOs: `GuildOverviewResponse`, `GuildConfigResponse` (mod config, music config, audit config sections), `UpdateGuildConfigRequest`
2. Create `GuildConfigService` — reads/writes using sb-common's ConfigService, ModerationConfigService, MusicConfigService
3. Create `GuildConfigController`:
   - `GET /api/guilds/{id}` — guild overview
   - `GET /api/guilds/{id}/config` — full config
   - `PATCH /api/guilds/{id}/config` — update config sections
   - `GET /api/guilds/{id}/roles` — roles via Discord API
   - `GET /api/guilds/{id}/channels` — channels via Discord API
4. All endpoints annotated with `@RequireGuildPermission(MANAGE_SERVER)`
5. Commit

### Task 6: Moderation endpoints

**Files:**
- Create: `sb-api/src/main/kotlin/ru/sablebot/api/controller/ModerationController.kt`
- Create: `sb-api/src/main/kotlin/ru/sablebot/api/service/ModerationApiService.kt`
- Create: `sb-api/src/main/kotlin/ru/sablebot/api/dto/moderation/ModerationDtos.kt`

**Steps:**
1. Create DTOs: `CaseResponse`, `CaseListResponse` (paginated), `CaseFilterParams`, `BanRequest`, `KickRequest`, `WarnRequest`
2. Create `ModerationApiService` — wraps sb-common moderation repos for reads, publishes Kafka commands for actions (ban/kick/warn)
3. Create `ModerationController`:
   - `GET /api/guilds/{id}/cases` — paginated, filterable case list
   - `GET /api/guilds/{id}/cases/{caseId}` — case detail
   - `POST /api/guilds/{id}/moderation/ban` — execute ban (requires BAN_MEMBERS)
   - `POST /api/guilds/{id}/moderation/kick` — execute kick (requires KICK_MEMBERS)
   - `POST /api/guilds/{id}/moderation/warn` — issue warning (requires MANAGE_SERVER)
4. Commit

### Task 7: Stats endpoints

**Files:**
- Create: `sb-api/src/main/kotlin/ru/sablebot/api/controller/StatsController.kt`
- Create: `sb-api/src/main/kotlin/ru/sablebot/api/service/StatsService.kt`
- Create: `sb-api/src/main/kotlin/ru/sablebot/api/dto/stats/StatsDtos.kt`

**Steps:**
1. Create DTOs: `StatsOverviewResponse` (member count, commands 24h, mod actions 7d), `CommandUsageResponse`, `MemberGrowthResponse`, `AudioStatsResponse`
2. Create `StatsService` — queries database for aggregated stats, uses Prometheus metrics where applicable
3. Create `StatsController`:
   - `GET /api/guilds/{id}/stats/overview`
   - `GET /api/guilds/{id}/stats/commands?period=7d`
   - `GET /api/guilds/{id}/stats/members?period=30d`
   - `GET /api/guilds/{id}/stats/audio?period=7d`
4. Commit

### Task 8: API key management

**Files:**
- Create: `sb-api/src/main/kotlin/ru/sablebot/api/controller/ApiKeyController.kt`
- Create: `sb-api/src/main/kotlin/ru/sablebot/api/service/ApiKeyService.kt`
- Create: `sb-api/src/main/kotlin/ru/sablebot/api/entity/ApiKey.kt`
- Create: `sb-api/src/main/kotlin/ru/sablebot/api/repository/ApiKeyRepository.kt`
- Create: `sb-api/src/main/kotlin/ru/sablebot/api/security/filter/ApiKeyAuthenticationFilter.kt`
- Create: Liquibase changelog for api_keys table

**Steps:**
1. Create `ApiKey` entity — id, guildId, hashedKey, scopes (JSON), createdBy, createdAt, lastUsedAt, revoked
2. Create Liquibase changelog: `sb-api/src/main/resources/db/changelog-api-keys.xml`
3. Create `ApiKeyRepository` — JPA repository
4. Create `ApiKeyService` — generate (returns raw key once), revoke, validate, list
5. Create `ApiKeyAuthenticationFilter` — checks `X-API-Key` header, validates against hashed keys
6. Create `ApiKeyController`:
   - `GET /api/guilds/{id}/api-keys` — list keys (masked)
   - `POST /api/guilds/{id}/api-keys` — create (returns raw key once)
   - `DELETE /api/guilds/{id}/api-keys/{keyId}` — revoke
7. Commit

### Task 9: WebSocket STOMP configuration

**Files:**
- Create: `sb-api/src/main/kotlin/ru/sablebot/api/websocket/WebSocketConfig.kt`
- Create: `sb-api/src/main/kotlin/ru/sablebot/api/websocket/WebSocketAuthInterceptor.kt`
- Create: `sb-api/src/main/kotlin/ru/sablebot/api/websocket/GuildEventBroadcaster.kt`

**Steps:**
1. Create `WebSocketConfig` — `@EnableWebSocketMessageBroker`, configure STOMP endpoint `/ws` with SockJS fallback, topic prefix `/topic`
2. Create `WebSocketAuthInterceptor` — validates JWT from connection query params, sets user principal
3. Create `GuildEventBroadcaster` — service that sends messages to `/topic/guild.{id}.stats`, `/topic/guild.{id}.moderation`, `/topic/guild.{id}.audio`
4. Commit

### Task 10: Kafka event consumers for real-time

**Files:**
- Create: `sb-api/src/main/kotlin/ru/sablebot/api/kafka/ModerationEventConsumer.kt`
- Create: `sb-api/src/main/kotlin/ru/sablebot/api/kafka/StatsEventConsumer.kt`
- Create: `sb-api/src/main/kotlin/ru/sablebot/api/kafka/AudioEventConsumer.kt`
- Modify: sb-worker Kafka publishers (add event publishing for mod/stats/audio events)

**Steps:**
1. Create consumers that listen on `sablebot.events.moderation`, `sablebot.events.stats`, `sablebot.events.audio`
2. Each consumer deserializes the event and calls `GuildEventBroadcaster` to push to WebSocket subscribers
3. Add event publishing in sb-worker where moderation cases are created, commands are executed, and audio events occur
4. Commit

### Task 11: Docker Compose integration

**Files:**
- Modify: `docker-compose.yml` — add sablebot-api service
- Create: `sb-api/Dockerfile`

**Steps:**
1. Create Dockerfile for sb-api (multi-stage: gradle build → JRE runtime)
2. Add `sablebot-api` service to docker-compose.yml (port 8080, depends on postgres + kafka)
3. Add env vars for Discord OAuth2 credentials, JWT secret, DB connection
4. Commit

---

## Phase 2: Frontend (sb-dashboard)

### Task 12: Initialize Next.js project

**Steps:**
1. `npx create-next-app@latest sb-dashboard --typescript --tailwind --eslint --app --src-dir`
2. Install: `shadcn/ui`, `recharts`, `@stomp/stompjs`, `sockjs-client`, `lucide-react`
3. Initialize shadcn/ui: `npx shadcn@latest init`
4. Add shadcn components: button, card, input, table, tabs, select, badge, dialog, dropdown-menu, avatar, separator, sheet, toast
5. Create base layout with dark/light theme support
6. Commit

### Task 13: Auth flow and API client

**Files:**
- Create: `sb-dashboard/src/lib/api.ts` — REST client with cookie-based auth
- Create: `sb-dashboard/src/lib/auth.ts` — auth utilities (login redirect, logout, getCurrentUser)
- Create: `sb-dashboard/src/types/index.ts` — TypeScript types matching backend DTOs
- Create: `sb-dashboard/src/app/page.tsx` — landing page with Discord login button
- Create: `sb-dashboard/src/middleware.ts` — protect /dashboard routes

**Steps:**
1. Create TypeScript types for all API responses
2. Create API client (fetch wrapper with base URL, cookie credentials, error handling)
3. Create auth utilities
4. Create landing page with "Sign in with Discord" button
5. Create middleware to redirect unauthenticated users
6. Commit

### Task 14: Dashboard layout (navbar, sidebar, guild selector)

**Files:**
- Create: `sb-dashboard/src/components/layout/Navbar.tsx`
- Create: `sb-dashboard/src/components/layout/Sidebar.tsx`
- Create: `sb-dashboard/src/components/layout/GuildSelector.tsx`
- Create: `sb-dashboard/src/app/dashboard/layout.tsx`
- Create: `sb-dashboard/src/app/dashboard/page.tsx` — guild selection page
- Create: `sb-dashboard/src/app/dashboard/[guildId]/layout.tsx`

**Steps:**
1. Create Navbar: logo, guild selector dropdown, user avatar + logout
2. Create Sidebar: navigation links (Overview, Config, Stats, Moderation, API Keys)
3. Create GuildSelector: fetches user guilds from `/api/auth/me`, shows guilds where bot is present
4. Create dashboard layout wrapping all guild pages
5. Create guild selection page (card grid of guilds)
6. Commit

### Task 15: Server Config page

**Files:**
- Create: `sb-dashboard/src/app/dashboard/[guildId]/config/page.tsx`
- Create: `sb-dashboard/src/components/config/GeneralConfig.tsx`
- Create: `sb-dashboard/src/components/config/ModerationConfig.tsx`
- Create: `sb-dashboard/src/components/config/MusicConfig.tsx`
- Create: `sb-dashboard/src/components/config/AuditConfig.tsx`

**Steps:**
1. Create config page with tabs (General, Moderation, Music, Audit)
2. Each tab renders a form with current values fetched from API
3. Forms use shadcn components (switches, selects, inputs)
4. Save button calls PATCH /api/guilds/{id}/config
5. Toast notifications for success/error
6. Commit

### Task 16: Stats & Analytics page

**Files:**
- Create: `sb-dashboard/src/app/dashboard/[guildId]/stats/page.tsx`
- Create: `sb-dashboard/src/components/stats/OverviewCards.tsx`
- Create: `sb-dashboard/src/components/stats/CommandUsageChart.tsx`
- Create: `sb-dashboard/src/components/stats/MemberGrowthChart.tsx`
- Create: `sb-dashboard/src/components/stats/AudioStatsChart.tsx`
- Create: `sb-dashboard/src/lib/ws.ts` — WebSocket client

**Steps:**
1. Create WebSocket client (STOMP.js, connects to /ws, subscribes to guild topics)
2. Create OverviewCards: 4 stat cards with icons (members, active, commands, mod actions)
3. Create charts using Recharts (line/bar charts with period selectors)
4. Stats page composes all components, fetches initial data via REST, subscribes to WebSocket for real-time
5. Commit

### Task 17: Moderation Console page

**Files:**
- Create: `sb-dashboard/src/app/dashboard/[guildId]/moderation/page.tsx`
- Create: `sb-dashboard/src/components/moderation/CaseTable.tsx`
- Create: `sb-dashboard/src/components/moderation/CaseDetail.tsx`
- Create: `sb-dashboard/src/components/moderation/QuickActions.tsx`
- Create: `sb-dashboard/src/components/moderation/CaseFilters.tsx`

**Steps:**
1. Create CaseFilters: type select, moderator filter, target search, date range
2. Create CaseTable: paginated data table with columns (case#, type, target, mod, reason, date)
3. Create CaseDetail: slide-over panel showing full case info
4. Create QuickActions: dialog forms for ban/kick/warn with target user input and reason
5. Compose moderation page with filters + table + detail panel + action buttons
6. Commit

### Task 18: API Keys page

**Files:**
- Create: `sb-dashboard/src/app/dashboard/[guildId]/api-keys/page.tsx`
- Create: `sb-dashboard/src/components/api-keys/ApiKeyTable.tsx`
- Create: `sb-dashboard/src/components/api-keys/CreateKeyDialog.tsx`

**Steps:**
1. Create ApiKeyTable: table of keys (masked, scopes, created date, last used)
2. Create CreateKeyDialog: scope selection, shows raw key once after creation with copy button
3. Delete button with confirmation dialog
4. Commit

### Task 19: Docker & deployment

**Files:**
- Create: `sb-dashboard/Dockerfile`
- Modify: `docker-compose.yml` — add sablebot-dashboard + nginx services

**Steps:**
1. Create multi-stage Dockerfile (node build → node production)
2. Add dashboard and nginx services to docker-compose.yml
3. Create nginx config routing `/api` → sb-api, `/` → sb-dashboard
4. Test full stack: `docker compose up -d`
5. Commit
