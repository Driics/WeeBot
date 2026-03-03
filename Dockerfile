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

RUN addgroup --system app && adduser --system --ingroup app app
USER app

COPY --from=builder /app/sb-worker/build/libs/SableBot-Worker.jar app.jar

EXPOSE 8080 9090

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -f http://localhost:9090/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
