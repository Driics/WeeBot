# DX & Observability Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add structured logging, distributed tracing, Grafana dashboards, Docker infrastructure, GitHub Actions CI/CD, and developer experience improvements to SableBot.

**Architecture:** Grafana stack (Prometheus + Loki + Tempo + Grafana) in Docker Compose alongside the bot. Logstash-logback-encoder for structured JSON logs. Micrometer Tracing with Brave bridge for distributed tracing. MDC correlation IDs propagated through JDA events and Kafka headers. GitHub Actions for CI/CD with GHCR for container images.

**Tech Stack:** Spring Boot 3.5.6, Micrometer, Brave, Logstash-logback-encoder, Docker, Prometheus, Loki, Promtail, Tempo, Grafana, GitHub Actions

---

## Task 1: Add Structured Logging Dependencies

**Files:**
- Modify: `sb-common/build.gradle`

**Step 1: Add logstash-logback-encoder dependency**

Add to `sb-common/build.gradle` in the `dependencies` block, after the existing `api 'org.slf4j:slf4j-api:2.0.9'` line:

```groovy
    api "net.logstash.logback:logstash-logback-encoder:8.0"
```

**Step 2: Verify build compiles**

Run: `./gradlew :sb-common:compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add sb-common/build.gradle
git commit -m "feat(logging): add logstash-logback-encoder dependency"
```

---

## Task 2: Create Logback Configuration for sb-worker

**Files:**
- Create: `sb-worker/src/main/resources/logback-spring.xml`

**Step 1: Create the logback-spring.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <!-- Console appender for local dev (human-readable) -->
    <springProfile name="console">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} %highlight(%-5level) [%thread] %cyan(%logger{36}) - %msg%n%throwable</pattern>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>

    <!-- JSON appender for production (Loki ingestion) -->
    <springProfile name="!console">
        <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <includeMdcKeyName>traceId</includeMdcKeyName>
                <includeMdcKeyName>spanId</includeMdcKeyName>
                <includeMdcKeyName>guildId</includeMdcKeyName>
                <includeMdcKeyName>userId</includeMdcKeyName>
                <includeMdcKeyName>commandName</includeMdcKeyName>
                <includeMdcKeyName>eventType</includeMdcKeyName>
                <fieldNames>
                    <timestamp>@timestamp</timestamp>
                    <version>[ignore]</version>
                </fieldNames>
                <customFields>{"app":"sablebot-worker"}</customFields>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="JSON"/>
        </root>
    </springProfile>

    <!-- Suppress noisy loggers -->
    <logger name="net.dv8tion.jda" level="INFO"/>
    <logger name="org.apache.kafka" level="WARN"/>
    <logger name="org.hibernate" level="WARN"/>
    <logger name="org.springframework.cloud.consul" level="WARN"/>
</configuration>
```

**Step 2: Verify the app starts with default (JSON) profile**

Run: `./gradlew :sb-worker:bootRun` (briefly, then Ctrl+C)
Expected: Logs output as JSON lines to stdout

**Step 3: Commit**

```bash
git add sb-worker/src/main/resources/logback-spring.xml
git commit -m "feat(logging): add structured JSON logging config for sb-worker"
```

---

## Task 3: Create Logback Configuration for sb-api

**Files:**
- Create: `sb-api/src/main/resources/logback-spring.xml`

**Step 1: Create the logback-spring.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <springProfile name="console">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} %highlight(%-5level) [%thread] %cyan(%logger{36}) - %msg%n%throwable</pattern>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>

    <springProfile name="!console">
        <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <includeMdcKeyName>traceId</includeMdcKeyName>
                <includeMdcKeyName>spanId</includeMdcKeyName>
                <includeMdcKeyName>requestId</includeMdcKeyName>
                <includeMdcKeyName>userId</includeMdcKeyName>
                <fieldNames>
                    <timestamp>@timestamp</timestamp>
                    <version>[ignore]</version>
                </fieldNames>
                <customFields>{"app":"sablebot-api"}</customFields>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="JSON"/>
        </root>
    </springProfile>

    <logger name="org.hibernate" level="WARN"/>
    <logger name="org.springframework.security" level="INFO"/>
</configuration>
```

**Step 2: Commit**

```bash
git add sb-api/src/main/resources/logback-spring.xml
git commit -m "feat(logging): add structured JSON logging config for sb-api"
```

---

## Task 4: Add MDC Correlation to ContextEventManagerImpl

**Files:**
- Modify: `sb-common-worker/src/main/kotlin/ru/sablebot/common/worker/event/service/ContextEventManagerImpl.kt`

**Step 1: Add MDC imports and correlation ID injection**

At the top of the file, add import:
```kotlin
import org.slf4j.MDC
import java.util.UUID
```

In the `handleEvent(event: GenericEvent)` method (around line 63), add MDC setup at the start and cleanup in `finally`. The method currently looks like:

```kotlin
private fun handleEvent(event: GenericEvent) {
    val sample = Timer.start(meterRegistry)
    try {
        loopListeners(event)
        ...
```

Modify to:

```kotlin
private fun handleEvent(event: GenericEvent) {
    val sample = Timer.start(meterRegistry)
    MDC.put("traceId", UUID.randomUUID().toString().replace("-", ""))
    MDC.put("eventType", event.javaClass.simpleName)
    try {
        loopListeners(event)
        ...
    } finally {
        MDC.clear()
        contextService.resetContext()
    }
}
```

Note: Ensure `MDC.clear()` is called before `contextService.resetContext()` in the existing `finally` block.

**Step 2: Verify build compiles**

Run: `./gradlew :sb-common-worker:compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add sb-common-worker/src/main/kotlin/ru/sablebot/common/worker/event/service/ContextEventManagerImpl.kt
git commit -m "feat(logging): add traceId and eventType to MDC in event handling"
```

---

## Task 5: Add MDC Correlation to InternalCommandsServiceImpl

**Files:**
- Modify: `sb-common-worker/src/main/kotlin/ru/sablebot/common/worker/command/service/InternalCommandsServiceImpl.kt`

**Step 1: Add MDC context for command execution**

Add import at top:
```kotlin
import org.slf4j.MDC
```

In `executeDslCommand()` method (around line 87), before `coroutineLauncher.launchMessageJob(event)`, add:

```kotlin
MDC.put("commandName", fullCommandName)
MDC.put("guildId", event.guild?.id ?: "DM")
MDC.put("userId", event.user.id)
```

In `executeLegacyCommand()` method (around line 163), before `coroutineLauncher.launchMessageJob(event)`, add:

```kotlin
MDC.put("commandName", command.key)
MDC.put("guildId", event.guild?.id ?: "DM")
MDC.put("userId", event.user.id)
```

Note: MDC will be cleared by `ContextEventManagerImpl.handleEvent()` in its `finally` block, so no cleanup needed here.

**Step 2: Verify build compiles**

Run: `./gradlew :sb-common-worker:compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add sb-common-worker/src/main/kotlin/ru/sablebot/common/worker/command/service/InternalCommandsServiceImpl.kt
git commit -m "feat(logging): add command/guild/user context to MDC"
```

---

## Task 6: Add Kafka Header Correlation

**Files:**
- Modify: `sb-common/src/main/kotlin/ru/sablebot/common/configuration/KafkaConfiguration.kt`

**Step 1: Add trace ID propagation to Kafka producer**

Add imports:
```kotlin
import org.slf4j.MDC
import org.apache.kafka.clients.producer.ProducerInterceptor
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
```

Add a producer interceptor class inside or alongside `KafkaConfiguration`:

```kotlin
class TraceIdProducerInterceptor : ProducerInterceptor<String, Any> {
    override fun onSend(record: ProducerRecord<String, Any>): ProducerRecord<String, Any> {
        MDC.get("traceId")?.let { traceId ->
            record.headers().add("x-trace-id", traceId.toByteArray(Charsets.UTF_8))
        }
        return record
    }
    override fun onAcknowledgement(metadata: RecordMetadata?, exception: Exception?) {}
    override fun close() {}
    override fun configure(configs: MutableMap<String, *>?) {}
}
```

In `producerFactory()` (around line 48), add the interceptor to producer props:

```kotlin
props[ProducerConfig.INTERCEPTOR_CLASSES_CONFIG] = listOf(TraceIdProducerInterceptor::class.java.name)
```

**Step 2: Add trace ID extraction in consumer**

In `kafkaListenerContainerFactory()` (around line 64), add a `RecordInterceptor` that reads the header and sets MDC:

```kotlin
factory.setRecordInterceptor { record, _ ->
    record.headers().lastHeader("x-trace-id")?.let { header ->
        MDC.put("traceId", String(header.value(), Charsets.UTF_8))
    }
    record
}
```

**Step 3: Verify build compiles**

Run: `./gradlew :sb-common:compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add sb-common/src/main/kotlin/ru/sablebot/common/configuration/KafkaConfiguration.kt
git commit -m "feat(logging): propagate traceId through Kafka headers"
```

---

## Task 7: Add Distributed Tracing Dependencies

**Files:**
- Modify: `sb-common-worker/build.gradle`

**Step 1: Add Micrometer Tracing and Brave dependencies**

Add to `sb-common-worker/build.gradle` dependencies block:

```groovy
    api "io.micrometer:micrometer-tracing-bridge-brave"
    api "io.zipkin.reporter2:zipkin-reporter-brave"
```

These versions are managed by the Spring Boot BOM (3.5.6) so no explicit versions needed.

**Step 2: Verify build compiles**

Run: `./gradlew :sb-common-worker:compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add sb-common-worker/build.gradle
git commit -m "feat(tracing): add Micrometer Tracing with Brave bridge"
```

---

## Task 8: Configure Tracing in application.yml

**Files:**
- Modify: `sb-worker/src/main/resources/application.yml`

**Step 1: Add tracing configuration**

Add the following under the existing `management:` section in `application.yml`, after the `prometheus:` block (around line 70):

```yaml
  tracing:
    sampling:
      probability: 1.0
  zipkin:
    tracing:
      endpoint: ${TEMPO_ENDPOINT:http://localhost:9411/api/v2/spans}
```

Also add under `logging:` at the top level:

```yaml
logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

**Step 2: Verify the config is valid YAML**

Run: `./gradlew :sb-worker:compileKotlin` (the app won't start without Tempo, but compilation should pass)
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add sb-worker/src/main/resources/application.yml
git commit -m "feat(tracing): configure Zipkin/Tempo tracing endpoint"
```

---

## Task 9: Create Dockerfile

**Files:**
- Create: `Dockerfile` (project root)

**Step 1: Create multi-stage Dockerfile**

```dockerfile
# Stage 1: Build
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Cache Gradle wrapper
COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
RUN chmod +x gradlew && ./gradlew --version

# Cache dependencies
COPY build.gradle settings.gradle ./
COPY sb-common/build.gradle sb-common/build.gradle
COPY sb-common-worker/build.gradle sb-common-worker/build.gradle
COPY sb-worker/build.gradle sb-worker/build.gradle
COPY sb-api/build.gradle sb-api/build.gradle
COPY modules/build.gradle modules/build.gradle
COPY modules/sb-module-audio/build.gradle modules/sb-module-audio/build.gradle
COPY modules/sb-module-moderation/build.gradle modules/sb-module-moderation/build.gradle
RUN ./gradlew dependencies --no-daemon || true

# Copy source and build
COPY . .
RUN ./gradlew :sb-worker:bootJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/sb-worker/build/libs/SableBot-Worker.jar app.jar

EXPOSE 8080 9090

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -f http://localhost:9090/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Step 2: Add .dockerignore**

Create `.dockerignore` in project root:

```
.git
.gradle
.idea
**/build/
*.sarif.json
docs/
```

**Step 3: Verify Dockerfile syntax**

Run: `docker build --check .` (or just inspect visually — the build test happens in Task 12)

**Step 4: Commit**

```bash
git add Dockerfile .dockerignore
git commit -m "feat(docker): add multi-stage Dockerfile for sb-worker"
```

---

## Task 10: Create Infrastructure Configs

**Files:**
- Create: `infra/prometheus/prometheus.yml`
- Create: `infra/loki/loki.yml`
- Create: `infra/promtail/promtail.yml`
- Create: `infra/tempo/tempo.yml`

**Step 1: Create Prometheus config**

Create `infra/prometheus/prometheus.yml`:

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: "sablebot-worker"
    metrics_path: "/actuator/prometheus"
    static_configs:
      - targets: ["sablebot-worker:9090"]
        labels:
          app: "sablebot-worker"
```

**Step 2: Create Loki config**

Create `infra/loki/loki.yml`:

```yaml
auth_enabled: false

server:
  http_listen_port: 3100

common:
  path_prefix: /loki
  storage:
    filesystem:
      chunks_directory: /loki/chunks
      rules_directory: /loki/rules
  replication_factor: 1
  ring:
    kvstore:
      store: inmemory

schema_config:
  configs:
    - from: "2024-01-01"
      store: tsdb
      object_store: filesystem
      schema: v13
      index:
        prefix: index_
        period: 24h

limits_config:
  reject_old_samples: true
  reject_old_samples_max_age: 168h

analytics:
  reporting_enabled: false
```

**Step 3: Create Promtail config**

Create `infra/promtail/promtail.yml`:

```yaml
server:
  http_listen_port: 9080

positions:
  filename: /tmp/positions.yaml

clients:
  - url: http://loki:3100/loki/api/v1/push

scrape_configs:
  - job_name: docker
    docker_sd_configs:
      - host: unix:///var/run/docker.sock
        refresh_interval: 5s
        filters:
          - name: label
            values: ["logging=promtail"]
    relabel_configs:
      - source_labels: ["__meta_docker_container_name"]
        regex: "/(.*)"
        target_label: "container"
      - source_labels: ["__meta_docker_container_label_com_docker_compose_service"]
        target_label: "service"
    pipeline_stages:
      - json:
          expressions:
            level: level
            app: app
            traceId: traceId
      - labels:
          level:
          app:
      - timestamp:
          source: "@timestamp"
          format: "2006-01-02T15:04:05.000Z07:00"
```

**Step 4: Create Tempo config**

Create `infra/tempo/tempo.yml`:

```yaml
server:
  http_listen_port: 3200

distributor:
  receivers:
    zipkin:
      endpoint: "0.0.0.0:9411"

storage:
  trace:
    backend: local
    local:
      path: /var/tempo/traces
    wal:
      path: /var/tempo/wal

metrics_generator:
  registry:
    external_labels:
      source: tempo
  storage:
    path: /var/tempo/generator/wal
    remote_write:
      - url: http://prometheus:9090/api/v1/write
        send_exemplars: true
```

**Step 5: Commit**

```bash
git add infra/
git commit -m "feat(infra): add Prometheus, Loki, Promtail, Tempo configs"
```

---

## Task 11: Create Grafana Provisioning

**Files:**
- Create: `infra/grafana/provisioning/datasources/datasources.yml`
- Create: `infra/grafana/provisioning/dashboards/dashboards.yml`

**Step 1: Create datasources provisioning**

Create `infra/grafana/provisioning/datasources/datasources.yml`:

```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: true

  - name: Loki
    type: loki
    access: proxy
    url: http://loki:3100
    editable: true
    jsonData:
      derivedFields:
        - datasourceUid: tempo
          matcherRegex: '"traceId":"(\w+)"'
          name: TraceID
          url: "$${__value.raw}"

  - name: Tempo
    type: tempo
    access: proxy
    url: http://tempo:3200
    uid: tempo
    editable: true
    jsonData:
      tracesToLogs:
        datasourceUid: loki
        filterByTraceID: true
        mapTagNamesEnabled: true
```

**Step 2: Create dashboard provisioning config**

Create `infra/grafana/provisioning/dashboards/dashboards.yml`:

```yaml
apiVersion: 1

providers:
  - name: "SableBot"
    orgId: 1
    folder: "SableBot"
    type: file
    disableDeletion: false
    editable: true
    options:
      path: /var/lib/grafana/dashboards
      foldersFromFilesStructure: false
```

**Step 3: Commit**

```bash
git add infra/grafana/
git commit -m "feat(grafana): add datasource and dashboard provisioning"
```

---

## Task 12: Create Docker Compose Files

**Files:**
- Create: `docker-compose.yml`
- Create: `docker-compose.dev.yml`
- Create: `.env.example`

**Step 1: Create docker-compose.yml**

```yaml
services:
  sablebot-worker:
    build: .
    container_name: sablebot-worker
    restart: unless-stopped
    ports:
      - "9090:9090"
    environment:
      - SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-}
      - DB_URL=jdbc:postgresql://postgres:5432/${POSTGRES_DB:-sablebot}
      - DB_USERNAME=${POSTGRES_USER:-sablebot}
      - DB_PASSWORD=${POSTGRES_PASSWORD:-sablebot}
      - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
      - DISCORD_TOKEN=${DISCORD_TOKEN}
      - TEMPO_ENDPOINT=http://tempo:9411/api/v2/spans
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_healthy
    labels:
      logging: "promtail"

  postgres:
    image: postgres:16-alpine
    container_name: sablebot-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${POSTGRES_DB:-sablebot}
      POSTGRES_USER: ${POSTGRES_USER:-sablebot}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-sablebot}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-sablebot}"]
      interval: 5s
      timeout: 5s
      retries: 5

  kafka:
    image: apache/kafka:3.8.1
    container_name: sablebot-kafka
    restart: unless-stopped
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_LOG_DIRS: /var/lib/kafka/data
      CLUSTER_ID: sablebot-kafka-cluster-001
    volumes:
      - kafka_data:/var/lib/kafka/data
    ports:
      - "9092:9092"
    healthcheck:
      test: ["CMD-SHELL", "/opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 > /dev/null 2>&1"]
      interval: 10s
      timeout: 10s
      retries: 5

  prometheus:
    image: prom/prometheus:v2.53.0
    container_name: sablebot-prometheus
    restart: unless-stopped
    volumes:
      - ./infra/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus_data:/prometheus
    ports:
      - "9091:9090"
    command:
      - "--config.file=/etc/prometheus/prometheus.yml"
      - "--storage.tsdb.retention.time=30d"
      - "--web.enable-remote-write-receiver"

  loki:
    image: grafana/loki:3.3.2
    container_name: sablebot-loki
    restart: unless-stopped
    volumes:
      - ./infra/loki/loki.yml:/etc/loki/local-config.yaml:ro
      - loki_data:/loki
    ports:
      - "3100:3100"
    command: -config.file=/etc/loki/local-config.yaml

  promtail:
    image: grafana/promtail:3.3.2
    container_name: sablebot-promtail
    restart: unless-stopped
    volumes:
      - ./infra/promtail/promtail.yml:/etc/promtail/config.yml:ro
      - /var/run/docker.sock:/var/run/docker.sock:ro
    command: -config.file=/etc/promtail/config.yml
    depends_on:
      - loki

  tempo:
    image: grafana/tempo:2.6.1
    container_name: sablebot-tempo
    restart: unless-stopped
    volumes:
      - ./infra/tempo/tempo.yml:/etc/tempo/tempo.yml:ro
      - tempo_data:/var/tempo
    ports:
      - "3200:3200"
      - "9411:9411"
    command: -config.file=/etc/tempo/tempo.yml

  grafana:
    image: grafana/grafana:11.4.0
    container_name: sablebot-grafana
    restart: unless-stopped
    environment:
      GF_SECURITY_ADMIN_USER: ${GRAFANA_ADMIN_USER:-admin}
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-admin}
      GF_USERS_ALLOW_SIGN_UP: "false"
    volumes:
      - ./infra/grafana/provisioning:/etc/grafana/provisioning:ro
      - ./infra/grafana/dashboards:/var/lib/grafana/dashboards:ro
      - grafana_data:/var/lib/grafana
    ports:
      - "3000:3000"
    depends_on:
      - prometheus
      - loki
      - tempo

volumes:
  postgres_data:
  kafka_data:
  prometheus_data:
  loki_data:
  tempo_data:
  grafana_data:
```

**Step 2: Create docker-compose.dev.yml**

```yaml
# Usage: docker compose -f docker-compose.yml -f docker-compose.dev.yml up
services:
  sablebot-worker:
    build:
      context: .
      target: builder
    environment:
      - SPRING_PROFILES_ACTIVE=console
      - JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
    ports:
      - "9090:9090"
      - "5005:5005"
    command: ["./gradlew", ":sb-worker:bootRun", "--no-daemon"]
    volumes:
      - .:/app
      - gradle_cache:/root/.gradle
    labels:
      logging: "promtail"

volumes:
  gradle_cache:
```

**Step 3: Create .env.example**

```bash
# Discord Bot Token (required)
DISCORD_TOKEN=your-discord-bot-token-here

# PostgreSQL
POSTGRES_DB=sablebot
POSTGRES_USER=sablebot
POSTGRES_PASSWORD=change-me-in-production

# Grafana
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=change-me-in-production

# Spring profiles (leave empty for JSON logging, set to "console" for dev)
SPRING_PROFILES_ACTIVE=
```

**Step 4: Commit**

```bash
git add docker-compose.yml docker-compose.dev.yml .env.example
git commit -m "feat(docker): add Docker Compose for full dev/prod stack"
```

---

## Task 13: Create Grafana Bot Overview Dashboard

**Files:**
- Create: `infra/grafana/dashboards/bot-overview.json`

**Step 1: Create the dashboard JSON**

Create `infra/grafana/dashboards/bot-overview.json` with the following Grafana dashboard. This is a provisioned JSON dashboard with panels for:

1. **Row: Discord Stats** — stat panels for guild count, user count, channel count, average ping (from `sablebot_discord_guilds`, `sablebot_discord_users`, `sablebot_discord_channels`, `sablebot_discord_average_ping`)
2. **Row: Commands** — timeseries for command rate (`rate(sablebot_commands_executed_total[5m])`), error rate (`rate(sablebot_commands_errors_total[5m])`), and duration heatmap (`sablebot_commands_duration_seconds_bucket`)
3. **Row: Events** — timeseries for event throughput (`rate(sablebot_events_processed_total[5m])`), rejection rate (`rate(sablebot_events_rejected_total[5m])`), event errors
4. **Row: Interactions** — timeseries for interaction rate by type (`rate(sablebot_interactions_total[5m])` grouped by `type` tag)

Use Prometheus datasource, auto-refresh 30s, time range last 1h.

The dashboard should use `uid: "sablebot-overview"` and `title: "SableBot Overview"`.

Write the full JSON dashboard file. Use Grafana's standard panel schema with `gridPos`, `targets` with PromQL expressions, appropriate `type` (stat, timeseries, heatmap), and `fieldConfig` for units (short, s, percent).

**Step 2: Commit**

```bash
git add infra/grafana/dashboards/bot-overview.json
git commit -m "feat(grafana): add Bot Overview dashboard"
```

---

## Task 14: Create Grafana Infrastructure Dashboard

**Files:**
- Create: `infra/grafana/dashboards/infrastructure.json`

**Step 1: Create the dashboard JSON**

Create `infra/grafana/dashboards/infrastructure.json` with panels for:

1. **Row: JVM** — stat/timeseries for heap usage (`jvm_memory_used_bytes{area="heap"}`), GC pause time (`jvm_gc_pause_seconds_sum`), thread count (`jvm_threads_live_threads`)
2. **Row: Thread Pools** — gauges for event executor active/queue (`sablebot_event_executor_*`), coroutine pool (`sablebot_coroutine_executor_*`)
3. **Row: Cache** — timeseries for cache gets rate (`rate(sablebot_cache_gets_total[5m])`), eviction rate (`rate(sablebot_cache_evictions_total[5m])`)
4. **Row: Coroutines** — timeseries for job rate (`rate(sablebot_coroutine_jobs_total[5m])`), job duration (`sablebot_coroutine_job_duration_seconds`)

Use `uid: "sablebot-infra"`, `title: "SableBot Infrastructure"`.

**Step 2: Commit**

```bash
git add infra/grafana/dashboards/infrastructure.json
git commit -m "feat(grafana): add Infrastructure dashboard"
```

---

## Task 15: Create Grafana Kafka Dashboard

**Files:**
- Create: `infra/grafana/dashboards/kafka.json`

**Step 1: Create the dashboard JSON**

Create `infra/grafana/dashboards/kafka.json` with panels for:

1. **Row: Consumer** — timeseries for consumer lag (`kafka_consumer_fetch_manager_records_lag`), records consumed rate (`rate(kafka_consumer_fetch_manager_records_consumed_total[5m])`)
2. **Row: Producer** — timeseries for records sent rate, request latency
3. **Row: Dead Letter** — stat panel for DLT message count (`rate(sablebot_kafka_cache_evict_total[5m])` as proxy), timeseries for cache eviction rate
4. **Row: Spring Kafka** — listener container metrics from Spring Kafka auto-exposed Micrometer bindings

Use `uid: "sablebot-kafka"`, `title: "SableBot Kafka"`.

**Step 2: Commit**

```bash
git add infra/grafana/dashboards/kafka.json
git commit -m "feat(grafana): add Kafka dashboard"
```

---

## Task 16: Create Grafana Audio Dashboard

**Files:**
- Create: `infra/grafana/dashboards/audio.json`

**Step 1: Create the dashboard JSON**

Create `infra/grafana/dashboards/audio.json` with panels for:

1. **Row: Players** — stat for active players (`sablebot_audio_players_active`), counter for tracks played (`sablebot_audio_tracks_played_total`), errors (`sablebot_audio_player_errors_total`)
2. **Row: Activity** — timeseries for track play rate, error rate over time

Note: These metrics will be added in Task 20. For now, create the dashboard with the expected metric names — panels will show "No data" until the metrics are wired.

Use `uid: "sablebot-audio"`, `title: "SableBot Audio"`.

**Step 2: Commit**

```bash
git add infra/grafana/dashboards/audio.json
git commit -m "feat(grafana): add Audio dashboard"
```

---

## Task 17: Create GitHub Actions CI Workflow

**Files:**
- Create: `.github/workflows/ci.yml`

**Step 1: Create CI workflow**

```yaml
name: CI

on:
  push:
    branches: ["*"]
  pull_request:
    branches: [master]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: "21"
          distribution: "temurin"

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Build
        run: ./gradlew build --no-daemon

      - name: Run tests
        run: ./gradlew test --no-daemon

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-results
          path: "**/build/reports/tests/"

  qodana:
    runs-on: ubuntu-latest
    needs: build
    if: github.event_name == 'pull_request'
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: Qodana Scan
        uses: JetBrains/qodana-action@v2025.1
        with:
          args: --apply-fixes
```

**Step 2: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "feat(ci): add GitHub Actions build and test workflow"
```

---

## Task 18: Create GitHub Actions Docker Workflow

**Files:**
- Create: `.github/workflows/docker.yml`

**Step 1: Create Docker build and push workflow**

```yaml
name: Docker Build & Push

on:
  push:
    branches: [master]

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write

    steps:
      - uses: actions/checkout@v4

      - name: Log in to GHCR
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Extract metadata
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}
          tags: |
            type=sha,prefix=
            type=raw,value=latest

      - name: Build and push
        uses: docker/build-push-action@v6
        with:
          context: .
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
```

**Step 2: Commit**

```bash
git add .github/workflows/docker.yml
git commit -m "feat(ci): add Docker build and push to GHCR workflow"
```

---

## Task 19: Create GitHub Actions Deploy Workflow

**Files:**
- Create: `.github/workflows/deploy.yml`

**Step 1: Create deploy workflow**

```yaml
name: Deploy

on:
  workflow_dispatch:
  workflow_run:
    workflows: ["Docker Build & Push"]
    types: [completed]
    branches: [master]

jobs:
  deploy:
    runs-on: ubuntu-latest
    if: ${{ github.event_name == 'workflow_dispatch' || github.event.workflow_run.conclusion == 'success' }}

    steps:
      - name: Deploy to VPS
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.VPS_HOST }}
          username: ${{ secrets.VPS_USER }}
          key: ${{ secrets.VPS_SSH_KEY }}
          script: |
            cd ${{ secrets.VPS_APP_DIR }}
            docker compose pull sablebot-worker
            docker compose up -d sablebot-worker
            sleep 10
            docker compose exec sablebot-worker curl -sf http://localhost:9090/actuator/health || {
              echo "Health check failed, rolling back..."
              docker compose up -d --no-deps sablebot-worker
              exit 1
            }
            echo "Deploy successful!"
```

**Step 2: Commit**

```bash
git add .github/workflows/deploy.yml
git commit -m "feat(ci): add VPS deploy workflow with health check"
```

---

## Task 20: Add Audio Module Metrics

**Files:**
- Modify: `modules/sb-module-audio/src/main/kotlin/ru/sablebot/module/audio/service/PlayerServiceImpl.kt` (or wherever player state is tracked)

**Step 1: Find the PlayerServiceImpl**

Read `modules/sb-module-audio/src/main/kotlin/ru/sablebot/module/audio/service/PlayerServiceImpl.kt` to understand where players are created/destroyed and tracks are played.

**Step 2: Add MeterRegistry injection and metrics**

Add `MeterRegistry` to the constructor and register:

```kotlin
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
```

In the class body:

```kotlin
private val tracksPlayedCounter = meterRegistry.counter("sablebot.audio.tracks.played")
private val playerErrorsCounter = meterRegistry.counter("sablebot.audio.player.errors")

init {
    Gauge.builder("sablebot.audio.players.active") { /* return active player count */ }
        .description("Number of active audio players")
        .register(meterRegistry)
}
```

Increment `tracksPlayedCounter` when a track starts playing. Increment `playerErrorsCounter` on track load failures or exceptions.

**Step 3: Verify build**

Run: `./gradlew :modules:sb-module-audio:compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add modules/sb-module-audio/
git commit -m "feat(audio): add Micrometer metrics for players, tracks, errors"
```

---

## Task 21: Add Moderation Module Metrics

**Files:**
- Modify: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/service/ModerationServiceImpl.kt`
- Modify: `modules/sb-module-moderation/src/main/kotlin/ru/sablebot/module/moderation/service/AutoModServiceImpl.kt`

**Step 1: Read both service files to understand method signatures**

**Step 2: Add moderation action counter**

In `ModerationServiceImpl`, inject `MeterRegistry` and add:

```kotlin
private fun recordAction(type: String) {
    meterRegistry.counter("sablebot.moderation.actions", "type", type).increment()
}
```

Call `recordAction("ban")` in ban method, `recordAction("kick")` in kick method, etc.

**Step 3: Add automod trigger counter**

In `AutoModServiceImpl`, inject `MeterRegistry` and add:

```kotlin
private fun recordTrigger(type: String) {
    meterRegistry.counter("sablebot.moderation.automod.triggers", "type", type).increment()
}
```

Call `recordTrigger("spam")`, `recordTrigger("word")`, `recordTrigger("link")`, `recordTrigger("mention")` in respective check methods.

**Step 4: Verify build**

Run: `./gradlew :modules:sb-module-moderation:compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add modules/sb-module-moderation/
git commit -m "feat(moderation): add Micrometer metrics for actions and automod triggers"
```

---

## Task 22: Create CLAUDE.md

**Files:**
- Create: `CLAUDE.md` (project root)

**Step 1: Create CLAUDE.md**

```markdown
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
```

**Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: add CLAUDE.md developer guide"
```

---

## Task 23: Update .gitignore for New Files

**Files:**
- Modify: `.gitignore`

**Step 1: Add Docker and infra ignores**

Add to `.gitignore`:

```
# Docker volumes (never commit)
postgres_data/
kafka_data/
prometheus_data/
loki_data/
tempo_data/
grafana_data/

# Environment files (secrets)
.env
!.env.example
```

**Step 2: Commit**

```bash
git add .gitignore
git commit -m "chore: update .gitignore for Docker and env files"
```

---

## Execution Order

Tasks can be grouped for parallel execution:

**Group 1 (Foundation — sequential):** Tasks 1, 2, 3
**Group 2 (MDC Correlation — parallel after Group 1):** Tasks 4, 5, 6
**Group 3 (Tracing — sequential):** Tasks 7, 8
**Group 4 (Docker Infra — parallel):** Tasks 9, 10, 11, 12
**Group 5 (Dashboards — parallel after Group 4):** Tasks 13, 14, 15, 16
**Group 6 (CI/CD — parallel):** Tasks 17, 18, 19
**Group 7 (DX — parallel):** Tasks 20, 21, 22, 23
