package ru.sablebot.api

import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

/**
 * Basic test to verify the application class exists and is in the correct package.
 * Full integration tests require external dependencies (PostgreSQL, Kafka, etc.)
 * and should be run separately with proper test infrastructure.
 */
class SbApiApplicationTests {

    @Test
    fun applicationClassExists() {
        // Verify the application class is accessible
        val appClass = SbApiApplication::class.java
        assertNotNull(appClass, "SbApiApplication class should exist")
    }

    @Test
    fun mainFunctionExists() {
        // Verify main function exists as a top-level function in the same file
        // In Kotlin, top-level functions are compiled to static methods in a class named [FileName]Kt
        val appKtClass = Class.forName("ru.sablebot.api.SbApiApplicationKt")
        val mainMethod = appKtClass.declaredMethods
            .find { it.name == "main" }
        assertNotNull(mainMethod, "main function should exist")
    }

}
