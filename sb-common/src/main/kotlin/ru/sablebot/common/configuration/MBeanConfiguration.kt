package ru.sablebot.common.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.jmx.export.MBeanExporter
import org.springframework.jmx.export.annotation.AnnotationJmxAttributeSource
import org.springframework.jmx.export.assembler.MetadataMBeanInfoAssembler
import org.springframework.jmx.export.naming.MetadataNamingStrategy
import ru.sablebot.common.support.jmx.SbMetadataNamingStrategy


@Configuration
class MBeanConfiguration {
    @Bean
    fun annotationJmxAttributeSource(): AnnotationJmxAttributeSource = AnnotationJmxAttributeSource()

    @Bean
    fun infoAssembler(): MetadataMBeanInfoAssembler = MetadataMBeanInfoAssembler().apply {
        setAttributeSource(annotationJmxAttributeSource())
    }

    @Bean
    fun namingStrategy(): MetadataNamingStrategy = SbMetadataNamingStrategy().apply {
        setAttributeSource(annotationJmxAttributeSource())
    }


    @Bean
    @Lazy(false)
    fun mBeanExporter(): MBeanExporter = MBeanExporter().apply {
        setAutodetect(true)
        setNamingStrategy(namingStrategy())
        setAssembler(infoAssembler())
        setEnsureUniqueRuntimeObjectNames(false)
    }
}