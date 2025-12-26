package ru.sablebot.common.configuration

/**
 * Centralized Kafka topic definitions
 */
object KafkaTopics {
    private const val PREFIX = "sablebot"

    // Request topics
    const val GUILD_INFO_REQUEST = "$PREFIX.guild.info.request"
    const val RANKING_UPDATE_REQUEST = "$PREFIX.ranking.update.request"
    const val COMMAND_LIST_REQUEST = "$PREFIX.command.list.request"
    const val STATUS_REQUEST = "$PREFIX.status.request"
    const val WEBHOOK_GET_REQUEST = "$PREFIX.webhook.get.request"
    const val WEBHOOK_UPDATE_REQUEST = "$PREFIX.webhook.update.request"
    const val WEBHOOK_DELETE_REQUEST = "$PREFIX.webhook.delete.request"
    const val PATREON_WEBHOOK_REQUEST = "$PREFIX.patreon.webhook.request"
    const val CHECK_OWNER_REQUEST = "$PREFIX.check.owner.request"
    const val CACHE_EVICT_REQUEST = "$PREFIX.cache.evict.request"

    // Reply topics
    const val STATUS_REPLY = "$PREFIX.status.reply"
    const val CHECK_OWNER_REPLY = "$PREFIX.check.owner.reply"

    val all: List<String> by lazy {
        listOf(
            GUILD_INFO_REQUEST, RANKING_UPDATE_REQUEST, COMMAND_LIST_REQUEST,
            STATUS_REQUEST, STATUS_REPLY, WEBHOOK_GET_REQUEST,
            WEBHOOK_UPDATE_REQUEST, WEBHOOK_DELETE_REQUEST,
            PATREON_WEBHOOK_REQUEST, CHECK_OWNER_REQUEST,
            CHECK_OWNER_REPLY, CACHE_EVICT_REQUEST
        )
    }

    val replies: Array<String> = arrayOf(STATUS_REPLY, CHECK_OWNER_REPLY)
}