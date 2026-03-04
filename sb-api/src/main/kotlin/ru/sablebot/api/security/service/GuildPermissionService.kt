package ru.sablebot.api.security.service

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import ru.sablebot.api.service.DiscordApiService
import java.util.concurrent.TimeUnit

@Service
class GuildPermissionService(
    private val discordApiService: DiscordApiService
) {
    private val logger = KotlinLogging.logger {}

    // Cache: "userId:guildId" -> permissions bitmask
    private val permissionCache = Caffeine.newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .maximumSize(10000)
        .build<String, Long>()

    fun hasPermission(userId: String, guildId: String, requiredPermission: Long): Boolean {
        val cacheKey = "$userId:$guildId"
        val permissions = permissionCache.getIfPresent(cacheKey)

        if (permissions != null) {
            return checkPermission(permissions, requiredPermission)
        }

        // Permission not cached — we can't fetch it without the user's access token
        // This is set during the /me call or guild listing
        logger.debug { "No cached permissions for user $userId in guild $guildId" }
        return false
    }

    fun cachePermissions(userId: String, guildId: String, permissions: Long) {
        permissionCache.put("$userId:$guildId", permissions)
    }

    fun cacheGuildsPermissions(userId: String, guilds: List<Pair<String, Long>>) {
        guilds.forEach { (guildId, permissions) ->
            permissionCache.put("$userId:$guildId", permissions)
        }
    }

    private fun checkPermission(permissions: Long, required: Long): Boolean {
        // Administrator has all permissions
        if (permissions and 0x00000008L != 0L) return true
        return permissions and required != 0L
    }
}
