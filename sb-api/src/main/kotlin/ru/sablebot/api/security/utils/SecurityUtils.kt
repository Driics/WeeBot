package ru.sablebot.api.security.utils

import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.user.OAuth2User
import ru.sablebot.api.security.models.DiscordUserDetails

object SecurityUtils {

    val currentUser: DiscordUserDetails?
        get() = getDetails(SecurityContextHolder.getContext().authentication)

    fun getDetails(authentication: Authentication?): DiscordUserDetails? {
        val principal = authentication?.principal
        return when (principal) {
            is DiscordUserDetails -> principal         // если делаешь его principal
            is OAuth2User -> mapFromOAuth2User(principal) // если principal — стандартный OAuth2User
            else -> null
        }
    }

    private fun mapFromOAuth2User(oauth2User: OAuth2User): DiscordUserDetails {
        val attrs = oauth2User.attributes
        return DiscordUserDetails.create(attrs)
    }

    fun isAuthenticated(): Boolean =
        SecurityContextHolder.getContext().authentication?.isAuthenticated == true
}
