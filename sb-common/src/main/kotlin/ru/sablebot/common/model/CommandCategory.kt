package ru.sablebot.common.model

import net.dv8tion.jda.api.entities.emoji.Emoji

enum class CommandCategory(
    val title: String,
    val description: String,
    val emoji: Emoji? = null,
) {
    GENERAL("General", "Universal commands", Emoji.fromUnicode("\uD83C\uDF89")),
    MODERATION("Moderation", "Moderation commands", Emoji.fromUnicode("\uD83C\uDF89")),
}