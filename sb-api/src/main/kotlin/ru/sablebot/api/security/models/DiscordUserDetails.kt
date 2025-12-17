package ru.sablebot.api.security.models

import kotlinx.serialization.Serializable

@Serializable
class DiscordUserDetails : AbstractDetails() {

    var userName: String? = null
        private set

    var verified: Boolean? = null
        private set

    var mfaEnabled: Boolean? = null
        private set

    var avatar: String? = null
        private set

    var discriminator: String? = null
        private set

    var email: String? = null
        private set

    companion object {

        fun create(attrs: Map<String, Any?>): DiscordUserDetails =
            DiscordUserDetails().apply {
                id = attrs["id"] as? String
                userName = attrs["username"] as? String
                verified = attrs["verified"] as? Boolean
                mfaEnabled = attrs["mfa_enabled"] as? Boolean
                avatar = attrs["avatar"] as? String
                discriminator = attrs["discriminator"] as? String
                email = attrs["email"] as? String
            }
    }
}