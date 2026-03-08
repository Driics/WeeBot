package ru.sablebot.api.security.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import ru.sablebot.api.security.models.DiscordUserDetails
import ru.sablebot.api.service.ApiKeyService

@Component
class ApiKeyAuthenticationFilter(
    private val apiKeyService: ApiKeyService
) : OncePerRequestFilter() {

    companion object {
        const val API_KEY_HEADER = "X-API-Key"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // Only process if not already authenticated
        if (SecurityContextHolder.getContext().authentication == null) {
            val apiKey = request.getHeader(API_KEY_HEADER)
            if (apiKey != null) {
                val key = apiKeyService.validateKey(apiKey)
                if (key != null) {
                    val details = DiscordUserDetails.create(
                        mapOf(
                            "id" to key.createdBy,
                            "username" to "API Key: ${key.name}"
                        )
                    )

                    val auth = UsernamePasswordAuthenticationToken(
                        details,
                        null,
                        listOf(SimpleGrantedAuthority("ROLE_API_KEY"))
                    )

                    SecurityContextHolder.getContext().authentication = auth
                }
            }
        }

        filterChain.doFilter(request, response)
    }
}
