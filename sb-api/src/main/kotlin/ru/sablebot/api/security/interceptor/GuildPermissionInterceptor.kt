package ru.sablebot.api.security.interceptor

import io.github.oshai.kotlinlogging.KotlinLogging
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.servlet.HandlerMapping
import ru.sablebot.api.security.annotation.RequireGuildPermission
import ru.sablebot.api.security.service.GuildPermissionService
import ru.sablebot.api.security.utils.SecurityUtils

@Aspect
@Component
class GuildPermissionInterceptor(
    private val guildPermissionService: GuildPermissionService
) {
    private val logger = KotlinLogging.logger {}

    @Around("@annotation(ru.sablebot.api.security.annotation.RequireGuildPermission)")
    fun checkPermission(joinPoint: ProceedingJoinPoint): Any? {
        val method = (joinPoint.signature as MethodSignature).method
        val annotation = method.getAnnotation(RequireGuildPermission::class.java)
        val requiredPermission = annotation.permission

        val userId = SecurityUtils.currentUser?.id
            ?: throw AccessDeniedException("Not authenticated")

        val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)
            ?.request ?: throw IllegalStateException("No request context")

        @Suppress("UNCHECKED_CAST")
        val pathVariables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as? Map<String, String>
        val guildId = pathVariables?.get("guildId")
            ?: throw IllegalArgumentException("Missing guildId path variable")

        if (!guildPermissionService.hasPermission(userId, guildId, requiredPermission)) {
            logger.warn { "User $userId denied access to guild $guildId (permission: $requiredPermission)" }
            throw AccessDeniedException("Insufficient permissions for this guild")
        }

        return joinPoint.proceed()
    }
}
