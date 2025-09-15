package ru.sablebot.common.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.jmx.export.MBeanExporter
import org.springframework.jmx.export.annotation.AnnotationJmxAttributeSource
import org.springframework.jmx.export.assembler.MetadataMBeanInfoAssembler
import org.springframework.jmx.export.naming.MetadataNamingStrategy


@Configuration
open class MBeanConfiguration {
    @Bean
    open fun annotationJmxAttributeSource(): AnnotationJmxAttributeSource = AnnotationJmxAttributeSource()

    @Bean
    open fun infoAssembler(): MetadataMBeanInfoAssembler = MetadataMBeanInfoAssembler().apply {
        setAttributeSource(annotationJmxAttributeSource())
    }

    @Bean
    open fun namingStrategy(): MetadataNamingStrategy = MetadataNamingStrategy(annotationJmxAttributeSource())
    /*
    SbMetadataNamingStrategy().apply {
    setAttributeSource(annotationJmxAttributeSource())
}
     */

    @Bean
    @Lazy(false)
    open fun mBeanExporter(): MBeanExporter = MBeanExporter().apply {
        setAutodetect(true)
        setNamingStrategy(namingStrategy())
        setAssembler(infoAssembler())
        setEnsureUniqueRuntimeObjectNames(false)
    }
}