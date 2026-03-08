package ru.sablebot.module.audio.model

enum class FilterPreset(val displayName: String) {
    BASSBOOST("Bass Boost"),
    NIGHTCORE("Nightcore"),
    VAPORWAVE("Vaporwave"),
    KARAOKE("Karaoke"),
    EIGHT_D("8D Audio"),
    TREMOLO("Tremolo"),
    VIBRATO("Vibrato"),
    NONE("None");

    companion object {
        fun fromName(name: String): FilterPreset? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}
