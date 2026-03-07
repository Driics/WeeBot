plugins {
    id("sablebot.kotlin-library")
}

dependencies {
    api(project(":sb-common"))
    api(libs.spring.boot.starter.quartz)
    implementation(libs.spring.boot.starter.actuator)
    api(libs.spring.cloud.consul)

    api(libs.micrometer.core)
    api(libs.micrometer.prometheus)

    // Distributed tracing (Brave/Zipkin → Tempo)
    api(libs.micrometer.tracing.brave)
    api(libs.zipkin.reporter.brave)
    api(libs.zipkin.sender.urlconnection)
}
