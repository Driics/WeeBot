# DX & Observability Improvement Design

**Date:** 2026-03-03
**Status:** Approved
**Approach:** Grafana-First Observability + Dockerized Dev (Approach 1)
**Context:** Solo project, Docker on VPS, GitHub for CI/CD

## Overview

Enhance SableBot's developer experience and operational observability by:
1. Adding structured logging with correlation IDs
2. Implementing distributed tracing across JDA/Kafka/DB
3. Building Grafana dashboards with alerting
4. Dockerizing the full dev/prod stack
5. Setting up GitHub Actions CI/CD
6. Improving DX with CLAUDE.md, test infrastructure, and new metrics

## Current State

- **Metrics:** 20+ Micrometer metrics (commands, events, coroutines, cache, interactions, Kafka). Prometheus endpoint on `:9090` with SLO histograms.
- **Logging:** Default Logback, `kotlin-logging` facade. API has MDC (`requestId`/`userId`), worker has none.
- **Tracing:** None.
- **Infra:** No Docker, no CI/CD. Qodana config exists but unwired.
- **Tests:** Minimal — one smoke test, one unit test class. JUnit 5 + Spring Boot Test available.

---

## Section 1: Structured Logging & Correlation

### Dependencies
- `net.logstash.logback:logstash-logback-encoder` in `sb-common`

### Implementation
1. **`logback-spring.xml`** in both `sb-worker` and `sb-api`:
   - `console` profile: human-readable pattern layout for local dev
   - `json` profile (default): JSON encoder via logstash-logback-encoder with timestamp, level, logger, thread, MDC fields, exception stack
2. **Correlation ID propagation:**
   - `ContextEventManagerImpl`: generate `traceId` (UUID) at JDA event entry point, set in MDC
   - Add `guildId`, `userId`, `commandName` to MDC for every command execution in `InternalCommandsServiceImpl`
   - `KafkaConfiguration`: propagate `x-trace-id` header in producer/consumer
3. **Loki ingestion:** Promtail (in Docker Compose) ships container stdout logs (JSON) to Loki with labels `{app, level}`

---

## Section 2: Distributed Tracing

### Dependencies
- `io.micrometer:micrometer-tracing-bridge-brave` in `sb-common-worker`
- `io.zipkin.reporter2:zipkin-reporter-brave` in `sb-common-worker`

### Implementation
1. **Auto-instrumented spans** (zero code changes):
   - Spring `@Transactional` (DB queries)
   - Spring Kafka producer/consumer (`ObservationConvention`)
   - Spring MVC HTTP endpoints
2. **Custom spans for Discord flow:**
   - `ContextEventManagerImpl.handle()` → root span per event
   - `InternalCommandsServiceImpl.handleSlashCommand()` → child span with `command` tag
   - `CoroutineLauncher.launchMessageJob()` → child span for async work
3. **Tempo backend:** Receives spans via Zipkin protocol (port 9411)
4. **Trace-to-log correlation:** `traceId`/`spanId` from Brave auto-injected into MDC → appears in JSON logs → Grafana links traces to logs

### Configuration (`application.yml`)
```yaml
management:
  tracing:
    sampling:
      probability: 1.0  # 100% in dev, reduce in prod
  zipkin:
    tracing:
      endpoint: http://tempo:9411/api/v2/spans
```

---

## Section 3: Grafana Dashboards & Alerting

### Dashboards (provisioned as JSON)

1. **Bot Overview:**
   - Guild/user/channel counts (gauges)
   - Shard status + average ping
   - Command rate + error rate (timeseries)
   - Command duration heatmap (p50/p95/p99)
   - Event throughput + rejection rate

2. **Kafka:**
   - Consumer lag per topic
   - Dead-letter message count
   - Request/reply latency
   - Cache eviction rate

3. **Audio Module:**
   - Active players, tracks played
   - Player errors
   - Lavalink node health

4. **Infrastructure:**
   - JVM heap/GC/threads
   - Thread pool utilization
   - Coroutine pool active/queue size
   - Cache hit rates

### Alerting (Grafana → Discord webhook)
- Command error rate > 5% over 5min
- Average shard ping > 500ms for 2min
- Command p99 latency > 5s SLO for 5min
- Dead-letter queue messages > 0
- JVM heap usage > 85%

---

## Section 4: Docker Compose & Local Dev

### Dockerfile (project root, multi-stage)
- **Stage 1:** Gradle build with dependency cache layer
- **Stage 2:** Eclipse Temurin JRE 21 slim (~200MB)
- Health check: `curl -f http://localhost:9090/health`

### docker-compose.yml (production)
```
services:
  sablebot-worker    # built from Dockerfile
  postgres           # PostgreSQL 16, named volume
  kafka              # KRaft mode (no ZooKeeper)
  prometheus         # scrapes sablebot-worker:9090/prometheus
  loki               # log aggregation
  promtail           # ships container logs → Loki
  tempo              # trace backend (Zipkin receiver)
  grafana            # port 3000, provisioned dashboards + datasources
```

### docker-compose.dev.yml (override for local dev)
- Debug port 5005 exposed
- `SPRING_PROFILES_ACTIVE=console` for readable logs
- Source code mount for faster iteration
- PostgreSQL data in named volume

### Grafana provisioning
- `infra/grafana/provisioning/datasources/` — Prometheus, Loki, Tempo
- `infra/grafana/provisioning/dashboards/` — all 4 dashboard JSON files
- Auto-loaded on container start

### .env.example
All required env vars documented with example values.

---

## Section 5: GitHub Actions CI/CD

### Workflow 1: Build & Test (`ci.yml`)
- **Trigger:** Push to any branch, PRs to master
- **Steps:** Checkout → JDK 21 → Gradle cache → `./gradlew build test` → Upload test results → Qodana scan

### Workflow 2: Docker Build & Push (`docker.yml`)
- **Trigger:** Push to master
- **Steps:** Build Docker image → Tag with commit SHA + `latest` → Push to GHCR

### Workflow 3: Deploy (`deploy.yml`)
- **Trigger:** Manual or on GHCR image push
- **Steps:** SSH to VPS → `docker compose pull && docker compose up -d` → Health check → Rollback on failure

---

## Section 6: DX Improvements

### CLAUDE.md
- Project architecture overview (modules, communication patterns)
- Build commands
- Coding conventions (interface-impl pattern, DSL commands, kotlin-logging)
- Module creation guide

### Test Infrastructure
- Add `org.testcontainers:postgresql` to `sb-common` test dependencies
- Add `io.mockk:mockk` for Kotlin-idiomatic mocking
- Create `TestBase` class with embedded PostgreSQL + test Spring config
- Foundation only — not writing comprehensive tests in this effort

### New Metrics
- **Audio module:** `sablebot.audio.players.active` (gauge), `sablebot.audio.tracks.played` (counter), `sablebot.audio.player.errors` (counter)
- **Moderation module:** `sablebot.moderation.actions` (counter, tags: `type=ban|kick|warn|timeout|purge`), `sablebot.moderation.automod.triggers` (counter, tags: `type=spam|word|link|mention|raid`)
- **Kafka:** Consumer lag already auto-exposed, just needs dashboard panels

---

## File Structure (new files)

```
E:\Dev\
├── Dockerfile
├── docker-compose.yml
├── docker-compose.dev.yml
├── .env.example
├── CLAUDE.md
├── .github/
│   └── workflows/
│       ├── ci.yml
│       ├── docker.yml
│       └── deploy.yml
├── infra/
│   ├── prometheus/
│   │   └── prometheus.yml
│   ├── promtail/
│   │   └── promtail.yml
│   ├── tempo/
│   │   └── tempo.yml
│   ├── loki/
│   │   └── loki.yml
│   └── grafana/
│       └── provisioning/
│           ├── datasources/
│           │   └── datasources.yml
│           └── dashboards/
│               ├── dashboards.yml
│               ├── bot-overview.json
│               ├── kafka.json
│               ├── audio.json
│               └── infrastructure.json
├── sb-common/
│   └── src/main/resources/
│       └── logback-spring.xml
├── sb-worker/
│   └── src/main/resources/
│       └── logback-spring.xml
└── sb-api/
    └── src/main/resources/
        └── logback-spring.xml
```
