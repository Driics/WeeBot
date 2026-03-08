plugins {
    id("sablebot.spring-boot-app")
}

description = "SableBot Discord Bot Web Application"

application {
    mainClass.set("ru.sablebot.api.SbApiApplicationKt")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("SableBot-API.jar")
}

dependencies {
    implementation(project(":sb-common"))

    // MapStruct
    annotationProcessor(libs.mapstruct.processor)

    // Spring Boot starters
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.websocket)
    implementation(libs.spring.boot.starter.aop)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.oauth2.client)

    // Kafka
    implementation(libs.spring.kafka)

    // JWT
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    // Metrics
    implementation(libs.micrometer.prometheus)

    // Structured logging
    implementation(libs.logstash.logback)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.security.test)
}

tasks.named<ProcessResources>("processResources") {
    filesMatching("**/application.yml") {
        filter { line ->
            line.replace("@build.version@", project.version.toString())
                .replace("@build.timestamp@", System.currentTimeMillis().toString())
        }
    }
}
