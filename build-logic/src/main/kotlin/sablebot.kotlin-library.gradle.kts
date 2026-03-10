plugins {
    java
    `java-library`
    id("org.jetbrains.kotlin.jvm")
    kotlin("plugin.spring")
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

val libs = the<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

dependencies {
    api(platform(libs.findLibrary("spring-boot-bom").get()))
    api(platform(libs.findLibrary("spring-cloud-bom").get()))

    implementation(libs.findLibrary("kotlinx-coroutines-core").get())
    implementation(libs.findLibrary("kotlin-stdlib").get())

    testImplementation(libs.findBundle("testing").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}

tasks.withType<Test> {
    useJUnitPlatform()
}
