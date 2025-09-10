package ru.sablebot.common.worker.configuration

import org.springframework.cloud.client.discovery.EnableDiscoveryClient
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import ru.sablebot.common.configuration.CommonConfiguration

@Configuration
@EnableDiscoveryClient
@Import(CommonConfiguration::class, QuartzConfiguration::class)
open class WorkerConfiguration