package ru.sablebot.api.security.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import ru.sablebot.api.security.config.JwtProperties
import java.util.*
import javax.crypto.SecretKey

@Service
class JwtTokenService(private val jwtProperties: JwtProperties) {

    private val logger = KotlinLogging.logger {}

    private val key: SecretKey = Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())

    fun generateToken(userId: String, username: String, avatar: String?, accessToken: String? = null): String {
        val now = Date()
        val expiry = Date(now.time + jwtProperties.expirationMs)

        return Jwts.builder()
            .subject(userId)
            .claim("username", username)
            .claim("avatar", avatar)
            .apply { if (accessToken != null) claim("at", accessToken) }
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    fun validateToken(token: String): Boolean {
        return try {
            extractClaims(token)
            true
        } catch (e: JwtException) {
            logger.debug { "Invalid JWT token: ${e.message}" }
            false
        }
    }

    fun extractClaims(token: String): Claims {
        return Jwts.parser().verifyWith(key).build()
            .parseSignedClaims(token)
            .payload
    }

    fun extractUserId(token: String): String = extractClaims(token).subject

    fun extractUsername(token: String): String? = extractClaims(token).get("username", String::class.java)

    fun extractAvatar(token: String): String? = extractClaims(token).get("avatar", String::class.java)

    fun extractAccessToken(token: String): String? = extractClaims(token).get("at", String::class.java)
}
