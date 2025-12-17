package ru.sablebot.api.common

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import org.apache.commons.lang3.RandomStringUtils
import org.slf4j.MDC
import ru.sablebot.api.security.utils.SecurityUtils

class InfoMdcFilter : Filter {
    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        chain: FilterChain
    ) {
        try {
            MDC.put("requestId", RandomStringUtils.randomAlphabetic(8))
            val details = SecurityUtils.currentUser
            if (details != null) {
                MDC.put("userId", details.id)
            }
            chain.doFilter(request, response)
        } finally {
            MDC.remove("requestId")
            MDC.remove("userId")
        }
    }
}