plugins {
    java
    `java-library`
    id("org.jetbrains.kotlin.jvm")
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(platform(libs.spring.boot.bom))
    api(platform(libs.spring.cloud.bom))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.stdlib)

    testImplementation(libs.bundles.testing)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
