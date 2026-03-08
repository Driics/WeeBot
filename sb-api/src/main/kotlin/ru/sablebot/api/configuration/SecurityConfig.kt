package ru.sablebot.api.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import ru.sablebot.api.security.config.CorsProperties
import ru.sablebot.api.security.filter.ApiKeyAuthenticationFilter
import ru.sablebot.api.security.filter.JwtAuthenticationFilter

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
open class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val apiKeyAuthenticationFilter: ApiKeyAuthenticationFilter,
    private val corsProperties: CorsProperties
) {

    @Bean
    open fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/api/auth/discord",
                        "/api/auth/discord/callback",
                        "/api/public/**",
                        "/actuator/health",
                        "/ws/**"
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(apiKeyAuthenticationFilter, JwtAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    open fun corsConfigurationSource(): CorsConfigurationSource {
        val origins = corsProperties.allowedOrigins.split(",").map { it.trim() }
        val config = CorsConfiguration().apply {
            allowedOrigins = origins
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
            maxAge = 3600L
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }
}
