package ru.sablebot.module.audio.service

import ru.sablebot.module.audio.model.FilterPreset

interface IFilterService {
    suspend fun apply(guildId: Long, preset: FilterPreset)
    suspend fun clear(guildId: Long)
    fun current(guildId: Long): FilterPreset?
}
