plugins {
    alias(libs.plugins.kotlin.spring)
    id("sablebot.kotlin-library")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":sb-common-worker"))
    implementation(libs.kotlinx.coroutines.reactor)
    implementation(libs.kotlin.stdlib)
}
