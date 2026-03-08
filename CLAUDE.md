# SableBot - Developer Guide

## Project Overview

SableBot is a multi-module Discord bot built with Kotlin, Spring Boot 3.5.6, and JDA 5. It uses Kafka for inter-service messaging and PostgreSQL for persistence.

## Modules

- **sb-common** — Shared domain library: JPA entities, repositories, Kafka config, caching, i18n
- **sb-common-worker** — Worker runtime: command system, event handling, metrics, filters, interactions
- **sb-worker** — Main bot application (Spring Boot entry point)
- **sb-api** — REST API with Discord OAuth2 (separate Spring Boot app)
- **modules/sb-module-audio** — Music playback via Lavalink v4
- **modules/sb-module-moderation** — Moderation commands, automod, raid detection

## Build & Run

```bash
# Build everything
./gradlew build

# Run the bot locally
./gradlew :sb-worker:bootRun

# Run with Docker Compose (full stack with monitoring)
docker compose up -d

# Run with dev overrides (debug port 5005, console logging)
docker compose -f docker-compose.yml -f docker-compose.dev.yml up
```

## Architecture

### Command System (DSL — preferred)
Commands use a Kotlin DSL. Each command is a `@Component` with:
- Inner `Declarations` class implementing `SlashCommandDeclarationWrapper`
- Inner `Executor` class implementing `SlashCommandExecutor`
- Inner `Options` class extending `ApplicationCommandOptions`

### Event Flow
```
JDA Gateway → ContextEventManagerImpl (per-shard executor)
  → SlashCommandInteractionEvent → Filter chain → CommandHandlerFilter → InternalCommandsServiceImpl
  → Other events → DiscordEventListener loop
```

### Kafka (Request/Reply)
Topics prefixed `sablebot.`. `ReplyingKafkaTemplate` for request/reply. Dead-letter with exponential backoff (10 retries).

### Database
PostgreSQL + Hibernate JPA + Liquibase migrations. Changelogs in `sb-common/src/main/resources/db/`.

## Conventions

- **Logging:** Use `io.github.oshai.kotlinlogging.KotlinLogging.logger {}` — never `println` or `java.util.logging`
- **Services:** Interface + Impl pattern (`IFooService` / `FooServiceImpl` in modules, `FooService` / `FooServiceImpl` in common)
- **Metrics:** Use Micrometer `MeterRegistry`. Prefix all custom metrics with `sablebot.`
- **Config:** Use `@ConfigurationProperties` with prefix `sablebot.*`
- **Coroutines:** Use `CoroutineLauncher.launchMessageJob()` for async Discord work — never `GlobalScope.launch`
