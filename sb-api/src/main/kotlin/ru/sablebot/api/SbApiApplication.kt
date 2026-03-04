package ru.sablebot.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication(scanBasePackages = ["ru.sablebot.api", "ru.sablebot.common"])
@EnableJpaRepositories(basePackages = ["ru.sablebot.api.repository", "ru.sablebot.common.persistence.repository"])
@EntityScan(basePackages = ["ru.sablebot.api.entity", "ru.sablebot.common.persistence.entity"])
@ConfigurationPropertiesScan
open class SbApiApplication

fun main(args: Array<String>) {
    runApplication<SbApiApplication>(*args)
}
