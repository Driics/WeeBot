package ru.sablebot.module.audio.model

import net.dv8tion.jda.api.entities.emoji.Emoji

enum class RepeatMode(val emoji: Emoji) {
    CURRENT(Emoji.fromFormatted(":repeat_one:")),
    ALL(Emoji.fromFormatted(":repeat:")),
    NONE(Emoji.fromFormatted(":arrow_right:")),
}