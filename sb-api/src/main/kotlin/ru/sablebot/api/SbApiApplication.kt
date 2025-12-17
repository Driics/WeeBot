package ru.sablebot.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
open class SbApiApplication

fun main(args: Array<String>) {
    runApplication<SbApiApplication>(*args)
}
