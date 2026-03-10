plugins {
    id("sablebot.spring-boot-app")
}

description = "SableBot Discord Bot Worker Application"

application {
    mainClass.set("ru.sablebot.worker.Launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("SableBot-Worker.jar")
}

dependencies {
    implementation(libs.spring.boot.starter.web)

    implementation(project(":sb-common-worker"))
    implementation(project(":modules:sb-module-moderation"))
    implementation(project(":modules:sb-module-audio"))
    implementation(project(":modules:sb-module-tickets"))

    implementation(libs.groovy)
}
