plugins {
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.jpa)
    id("sablebot.kotlin-library")
}

dependencies {
    api(libs.jackson.module.kotlin)
    api(libs.spring.boot.starter)
    api(libs.spring.boot.starter.data.jpa)
    api(libs.spring.boot.starter.validation)
    api(libs.spring.web)
    api(libs.spring.context.support)
    api(libs.spring.kafka)
    api(libs.reactor.kafka)

    api(libs.slf4j.api)
    api(libs.logstash.logback)

    api(libs.jda) {
        exclude(group = "club.minnced", module = "opus-java")
    }
    api(libs.discord.webhooks)
    api(libs.jda.ktx)

    api(libs.kotlin.logging)
    api(libs.kotlinx.serialization.json)

    implementation(libs.kotlin.reflect)
    api(libs.caffeine)

    compileOnly(libs.micrometer.core)

    implementation(libs.guava)
    implementation(libs.hibernate.validator)
    implementation(libs.postgresql)
    implementation(libs.liquibase.core)
}

sourceSets {
    test {
        kotlin {
            setSrcDirs(listOf("src/test/kotlin"))
        }
    }
}
