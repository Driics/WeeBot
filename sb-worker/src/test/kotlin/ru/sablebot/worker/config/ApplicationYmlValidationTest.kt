package ru.sablebot.worker.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileInputStream

class ApplicationYmlValidationTest {

    private val yamlPath = "src/main/resources/application.yml"

    @Test
    fun `application yml should exist`() {
        // Given
        val file = File(yamlPath)

        // Then
        assertTrue(file.exists(), "application.yml should exist at $yamlPath")
        assertTrue(file.isFile, "application.yml should be a file")
    }

    @Test
    fun `application yml should be valid YAML`() {
        // Given
        val yaml = Yaml()
        val file = File(yamlPath)

        // When & Then
        assertDoesNotThrow {
            FileInputStream(file).use { inputStream ->
                yaml.load<Map<String, Any>>(inputStream)
            }
        }
    }

    @Test
    fun `application yml should contain spring configuration`() {
        // Given
        val yaml = Yaml()
        val file = File(yamlPath)

        // When
        val config = FileInputStream(file).use { inputStream ->
            yaml.load<Map<String, Any>>(inputStream)
        }

        // Then
        assertTrue(config.containsKey("spring"), "Configuration should contain 'spring' key")
    }

    @Test
    fun `spring cloud consul configuration should be present`() {
        // Given
        val yaml = Yaml()
        val file = File(yamlPath)

        // When
        val config = FileInputStream(file).use { inputStream ->
            yaml.load<Map<String, Any>>(inputStream)
        }

        // Then
        val spring = config["spring"] as? Map<*, *>
        assertNotNull(spring, "Spring configuration should exist")
        
        val cloud = spring?.get("cloud") as? Map<*, *>
        assertNotNull(cloud, "Spring cloud configuration should exist")
        
        val consul = cloud?.get("consul") as? Map<*, *>
        assertNotNull(consul, "Consul configuration should exist")
    }

    @Test
    fun `consul should be disabled`() {
        // Given
        val yaml = Yaml()
        val file = File(yamlPath)

        // When
        val config = FileInputStream(file).use { inputStream ->
            yaml.load<Map<String, Any>>(inputStream)
        }

        // Then
        val spring = config["spring"] as Map<*, *>
        val cloud = spring["cloud"] as Map<*, *>
        val consul = cloud["consul"] as Map<*, *>
        
        assertEquals(false, consul["enabled"], "Consul should be disabled")
    }

    @Test
    fun `consul discovery health check configuration should be present`() {
        // Given
        val yaml = Yaml()
        val file = File(yamlPath)

        // When
        val config = FileInputStream(file).use { inputStream ->
            yaml.load<Map<String, Any>>(inputStream)
        }

        // Then
        val spring = config["spring"] as Map<*, *>
        val cloud = spring["cloud"] as Map<*, *>
        val consul = cloud["consul"] as Map<*, *>
        val discovery = consul["discovery"] as? Map<*, *>
        
        assertNotNull(discovery, "Discovery configuration should exist")
        assertEquals("/health", discovery?.get("healthCheckPath"), "Health check path should be /health")
        assertEquals("15s", discovery?.get("healthCheckInterval"), "Health check interval should be 15s")
    }

    @Test
    fun `spring application configuration should be present`() {
        // Given
        val yaml = Yaml()
        val file = File(yamlPath)

        // When
        val config = FileInputStream(file).use { inputStream ->
            yaml.load<Map<String, Any>>(inputStream)
        }

        // Then
        val spring = config["spring"] as Map<*, *>
        val application = spring["application"] as? Map<*, *>
        
        assertNotNull(application, "Application configuration should exist")
        assertTrue(application?.containsKey("name") == true, "Application name should be configured")
        assertEquals("SablebotBot-Worker", application?.get("name"))
    }

    @Test
    fun `datasource url should be configured with environment variable`() {
        // Given
        val yaml = Yaml()
        val file = File(yamlPath)

        // When
        val config = FileInputStream(file).use { inputStream ->
            yaml.load<Map<String, Any>>(inputStream)
        }

        // Then
        val spring = config["spring"] as Map<*, *>
        val datasource = spring["datasource"] as? Map<*, *>
        
        assertNotNull(datasource, "Datasource configuration should exist")
        assertEquals("\${DB_URL}", datasource?.get("url"), "Datasource URL should use DB_URL env variable")
    }

    @Test
    fun `quartz configuration should be present`() {
        // Given
        val yaml = Yaml()
        val file = File(yamlPath)

        // When
        val config = FileInputStream(file).use { inputStream ->
            yaml.load<Map<String, Any>>(inputStream)
        }

        // Then
        val spring = config["spring"] as Map<*, *>
        val quartz = spring["quartz"] as? Map<*, *>
        
        assertNotNull(quartz, "Quartz configuration should exist")
        assertEquals("jdbc", quartz?.get("job-store-type"), "Quartz should use JDBC job store")
        assertEquals(true, quartz?.get("overwriteExistingJobs"), "Quartz should overwrite existing jobs")
    }

    @Test
    fun `quartz jdbc configuration should not initialize schema`() {
        // Given
        val yaml = Yaml()
        val file = File(yamlPath)

        // When
        val config = FileInputStream(file).use { inputStream ->
            yaml.load<Map<String, Any>>(inputStream)
        }

        // Then
        val spring = config["spring"] as Map<*, *>
        val quartz = spring["quartz"] as Map<*, *>
        val jdbc = quartz["jdbc"] as? Map<*, *>
        
        assertNotNull(jdbc, "JDBC configuration should exist")
        assertEquals("never", jdbc?.get("initialize-schema"), "Schema initialization should be never")
    }

    @Test
    fun `quartz properties should be configured`() {
        // Given
        val yaml = Yaml()
        val file = File(yamlPath)

        // When
        val config = FileInputStream(file).use { inputStream ->
            yaml.load<Map<String, Any>>(inputStream)
        }

        // Then
        val spring = config["spring"] as Map<*, *>
        val quartz = spring["quartz"] as Map<*, *>
        val properties = quartz["properties"] as? Map<*, *>
        
        assertNotNull(properties, "Quartz properties should exist")
        assertEquals("sablebot-spring-boot-quartz", properties?.get("org.quartz.scheduler.instanceName"))
        assertEquals("AUTO", properties?.get("org.quartz.scheduler.instanceId"))
        assertEquals("15", properties?.get("org.quartz.threadPool.threadCount"))
        assertEquals("qrtz_", properties?.get("org.quartz.jobStore.tablePrefix"))
    }

    @Test
    fun `liquibase configuration should be present`() {
        // Given
        val yaml = Yaml()
        val file = File(yamlPath)

        // When
        val config = FileInputStream(file).use { inputStream ->
            yaml.load<Map<String, Any>>(inputStream)
        }

        // Then
        val spring = config["spring"] as Map<*, *>
        val liquibase = spring["liquibase"] as? Map<*, *>
        
        assertNotNull(liquibase, "Liquibase configuration should exist")
        assertEquals("classpath:db/master.xml", liquibase?.get("change-log"))
    }

    @Test
    fun `kafka configuration should be present`() {
        // Given
        val yaml = Yaml()
        val file = File(yamlPath)

        // When
        val config = FileInputStream(file).use { inputStream ->
            yaml.load<Map<String, Any>>(inputStream)
        }

        // Then
        val spring = config["spring"] as Map<*, *>
        val kafka = spring["kafka"] as? Map<*, *>
        
        assertNotNull(kafka, "Kafka configuration should exist")
        assertTrue(kafka?.containsKey("bootstrap-servers") == true)
        assertTrue(kafka?.containsKey("consumer") == true)
        assertTrue(kafka?.containsKey("producer") == true)
    }

    @Test
    fun `kafka consumer configuration should be correct`() {
        // Given
        val yaml = Yaml()
        val file = File(yamlPath)

        // When
        val config = FileInputStream(file).use { inputStream ->
            yaml.load<Map<String, Any>>(inputStream)
        }

        // Then
        val spring = config["spring"] as Map<*, *>
        val kafka = spring["kafka"] as Map<*, *>
        val consumer = kafka["consumer"] as? Map<*, *>
        
        assertNotNull(consumer)
        assertEquals("latest", consumer?.get("auto-offset-reset"))
        assertEquals("org.apache.kafka.common.serialization.StringDeserializer", consumer?.get("key-deserializer"))
        assertEquals("org.springframework.kafka.support.serializer.JsonDeserializer", consumer?.get("value-deserializer"))
        
        val consumerProps = consumer?.get("properties") as? Map<*, *>
        assertNotNull(consumerProps)
        assertEquals(10485760, consumerProps?.get("max.partition.fetch.bytes"))
        assertEquals(500, consumerProps?.get("max.poll.records"))
    }

    @Test
    fun `kafka producer configuration should be correct`() {
        // Given
        val yaml = Yaml()
        val file = File(yamlPath)

        // When
        val config = FileInputStream(file).use { inputStream ->
            yaml.load<Map<String, Any>>(inputStream)
        }

        // Then
        val spring = config["spring"] as Map<*, *>
        val kafka = spring["kafka"] as Map<*, *>
        val producer = kafka["producer"] as? Map<*, *>
        
        assertNotNull(producer)
        assertEquals("org.apache.kafka.common.serialization.StringSerializer", producer?.get("key-serializer"))
        assertEquals("org.springframework.kafka.support.serializer.JsonSerializer", producer?.get("value-serializer"))
        assertEquals("all", producer?.get("acks"))
        assertEquals(3, producer?.get("retries"))
        
        val producerProps = producer?.get("properties") as? Map<*, *>
        assertNotNull(producerProps)
        assertEquals(10485760, producerProps?.get("max.request.size"))
        assertEquals(16384, producerProps?.get("batch.size"))
    }

    @Test
    fun `management endpoints should be configured`() {
        // Given
        val yaml = Yaml()
        val file = File(yamlPath)

        // When
        val config = FileInputStream(file).use { inputStream ->
            yaml.load<Map<String, Any>>(inputStream)
        }

        // Then
        val management = config["management"] as? Map<*, *>
        assertNotNull(management, "Management configuration should exist")
        
        val endpoints = management?.get("endpoints") as? Map<*, *>
        val web = endpoints?.get("web") as? Map<*, *>
        val exposure = web?.get("exposure") as? Map<*, *>
        
        assertEquals("health,metrics,prometheus", exposure?.get("include"))
    }

    @Test
    fun `prometheus metrics should be enabled`() {
        // Given
        val yaml = Yaml()
        val file = File(yamlPath)

        // When
        val config = FileInputStream(file).use { inputStream ->
            yaml.load<Map<String, Any>>(inputStream)
        }

        // Then
        val management = config["management"] as Map<*, *>
        val prometheus = management["prometheus"] as? Map<*, *>
        val metrics = prometheus?.get("metrics") as? Map<*, *>
        val export = metrics?.get("export") as? Map<*, *>
        
        assertEquals(true, export?.get("enabled"))
    }

    @Test
    fun `sablebot configuration should be present`() {
        // Given
        val yaml = Yaml()
        val file = File(yamlPath)

        // When
        val config = FileInputStream(file).use { inputStream ->
            yaml.load<Map<String, Any>>(inputStream)
        }

        // Then
        assertTrue(config.containsKey("sablebot"), "Sablebot configuration should exist")
        
        val sablebot = config["sablebot"] as Map<*, *>
        assertNotNull(sablebot["common"])
        assertNotNull(sablebot["worker"])
    }

    @Test
    fun `discord configuration should use environment variables`() {
        // Given
        val yaml = Yaml()
        val file = File(yamlPath)

        // When
        val config = FileInputStream(file).use { inputStream ->
            yaml.load<Map<String, Any>>(inputStream)
        }

        // Then
        val sablebot = config["sablebot"] as Map<*, *>
        val worker = sablebot["worker"] as Map<*, *>
        val discord = worker["discord"] as? Map<*, *>
        
        assertNotNull(discord)
        assertEquals("\${DISCORD_TOKEN}", discord?.get("token"))
        assertEquals("Still testing", discord?.get("playingStatus"))
        assertEquals(1, discord?.get("shardsTotal"))
    }

    @Test
    fun `support guild configuration should be present`() {
        // Given
        val yaml = Yaml()
        val file = File(yamlPath)

        // When
        val config = FileInputStream(file).use { inputStream ->
            yaml.load<Map<String, Any>>(inputStream)
        }

        // Then
        val sablebot = config["sablebot"] as Map<*, *>
        val worker = sablebot["worker"] as Map<*, *>
        val support = worker["support"] as? Map<*, *>
        
        assertNotNull(support)
        assertEquals(763252709833965579L, support?.get("guild-id"))
        assertEquals(924575732989034506L, support?.get("emergency-channel-id"))
    }

    @Test
    fun `server configuration should set UTF-8 encoding`() {
        // Given
        val yaml = Yaml()
        val file = File(yamlPath)

        // When
        val config = FileInputStream(file).use { inputStream ->
            yaml.load<Map<String, Any>>(inputStream)
        }

        // Then
        val server = config["server"] as? Map<*, *>
        assertNotNull(server)
        
        val servlet = server?.get("servlet") as? Map<*, *>
        val encoding = servlet?.get("encoding") as? Map<*, *>
        
        assertNotNull(encoding)
        assertEquals("UTF-8", encoding?.get("charset"))
        assertEquals(true, encoding?.get("enabled"))
        assertEquals(true, encoding?.get("force"))
    }

    @Test
    fun `commands configuration should enable invoke logging`() {
        // Given
        val yaml = Yaml()
        val file = File(yamlPath)

        // When
        val config = FileInputStream(file).use { inputStream ->
            yaml.load<Map<String, Any>>(inputStream)
        }

        // Then
        val sablebot = config["sablebot"] as Map<*, *>
        val worker = sablebot["worker"] as Map<*, *>
        val commands = worker["commands"] as? Map<*, *>
        
        assertNotNull(commands)
        assertEquals(true, commands?.get("invokeLogging"))
    }
}