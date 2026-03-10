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
import ru.sablebot.api.security.service.JwtTokenService

@Component
class JwtAuthenticationFilter(
    private val jwtTokenService: JwtTokenService
) : OncePerRequestFilter() {

    companion object {
        const val COOKIE_NAME = "sb_token"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = extractToken(request)

        if (token != null) {
            try {
                val claims = jwtTokenService.extractClaims(token)
                val details = DiscordUserDetails.create(
                    mapOf(
                        "id" to claims.subject,
                        "username" to claims.get("username", String::class.java),
                        "avatar" to claims.get("avatar", String::class.java),
                        "at" to claims.get("at", String::class.java)
                    )
                )

                val auth = UsernamePasswordAuthenticationToken(
                    details,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_USER"))
                )

                SecurityContextHolder.getContext().authentication = auth
            } catch (_: Exception) {
                // Invalid token — continue unauthenticated
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun extractToken(request: HttpServletRequest): String? {
        // Try cookie first
        request.cookies?.find { it.name == COOKIE_NAME }?.let {
            return it.value
        }

        // Fallback to Authorization header
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7)
        }

        return null
    }
}
