package ru.sablebot.module.audio.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.ResourceBundleMessageSource

@Configuration
class AudioModuleConfig {

    @Bean("audioMessageSource")
    fun audioMessageSource(): ResourceBundleMessageSource {
        val source = ResourceBundleMessageSource()
        source.setBasename("audio")
        source.setDefaultEncoding("UTF-8")
        source.setUseCodeAsDefaultMessage(true)
        return source
    }
}
