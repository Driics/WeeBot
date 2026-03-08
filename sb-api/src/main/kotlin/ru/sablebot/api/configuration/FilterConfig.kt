package ru.sablebot.api.configuration

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.sablebot.api.common.ApiRequestLoggingFilter
import ru.sablebot.api.common.InfoMdcFilter

@Configuration
open class FilterConfig {

    @Bean
    open fun infoMdcFilter(): FilterRegistrationBean<InfoMdcFilter> {
        return FilterRegistrationBean<InfoMdcFilter>().apply {
            filter = InfoMdcFilter()
            order = 1
            addUrlPatterns("/*")
        }
    }

    @Bean
    open fun apiRequestLoggingFilter(): FilterRegistrationBean<ApiRequestLoggingFilter> {
        return FilterRegistrationBean<ApiRequestLoggingFilter>().apply {
            filter = ApiRequestLoggingFilter().apply {
                setIncludeQueryString(true)
                setIncludeClientInfo(true)
            }
            order = 2
            addUrlPatterns("/api/*")
        }
    }
}
