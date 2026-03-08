package ru.sablebot.api.security.annotation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequireGuildPermission(val permission: Long) {
    companion object {
        const val MANAGE_SERVER = 0x00000020L
        const val BAN_MEMBERS = 0x00000004L
        const val KICK_MEMBERS = 0x00000002L
        const val ADMINISTRATOR = 0x00000008L
    }
}
