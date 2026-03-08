package ru.sablebot.module.audio.model

enum class SearchSource(val prefix: String) {
    YOUTUBE("ytsearch:"),
    YOUTUBE_MUSIC("ytmsearch:"),
    SOUNDCLOUD("scsearch:"),
    DIRECT("");

    companion object {
        fun fromName(name: String): SearchSource? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}
