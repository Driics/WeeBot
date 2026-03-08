package ru.sablebot.api.dto.auth

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class DiscordTokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("token_type") val tokenType: String,
    @JsonProperty("expires_in") val expiresIn: Long,
    @JsonProperty("refresh_token") val refreshToken: String?,
    val scope: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DiscordUserResponse(
    val id: String,
    val username: String,
    val avatar: String?,
    val discriminator: String?,
    val email: String?,
    val verified: Boolean?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DiscordGuildResponse(
    val id: String,
    val name: String,
    val icon: String?,
    val owner: Boolean,
    val permissions: Long
)

data class UserInfoResponse(
    val id: String,
    val username: String,
    val avatar: String?,
    val guilds: List<GuildInfoResponse>
)

data class GuildInfoResponse(
    val id: String,
    val name: String,
    val icon: String?,
    val permissions: Long,
    val botPresent: Boolean
)
