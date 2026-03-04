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
COPY sb-api/build.gradle sb-api/build.gradle
RUN ./gradlew dependencies --no-daemon || true

# Copy source and build
COPY . .
RUN ./gradlew :sb-api:bootJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

RUN addgroup --system app && adduser --system --ingroup app app
USER app

COPY --from=builder /app/sb-api/build/libs/SableBot-API.jar app.jar

EXPOSE 8080 9090

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -f http://localhost:9090/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
