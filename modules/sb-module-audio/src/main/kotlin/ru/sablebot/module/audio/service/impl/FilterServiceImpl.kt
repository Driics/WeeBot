package ru.sablebot.module.audio.service.impl

import dev.arbjerg.lavalink.client.player.FilterBuilder
import dev.arbjerg.lavalink.protocol.v4.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import ru.sablebot.module.audio.model.FilterPreset
import ru.sablebot.module.audio.service.IFilterService
import ru.sablebot.module.audio.service.ILavalinkV4AudioService
import ru.sablebot.module.audio.service.PlayerServiceV4

@Service
class FilterServiceImpl(
    @Lazy private val playerService: PlayerServiceV4,
    private val audioService: ILavalinkV4AudioService
) : IFilterService {

    private val log = KotlinLogging.logger {}

    override suspend fun apply(guildId: Long, preset: FilterPreset) {
        val instance = playerService.get(guildId) ?: return

        val filters = buildFilters(preset)
        val link = audioService.lavalink.getOrCreateLink(guildId)

        link.createOrUpdatePlayer()
            .setFilters(filters)
            .block()

        instance.activeFilter = if (preset == FilterPreset.NONE) null else preset
        log.debug { "Applied filter preset $preset to guild $guildId" }
    }

    override suspend fun clear(guildId: Long) {
        apply(guildId, FilterPreset.NONE)
    }

    override fun current(guildId: Long): FilterPreset? {
        return playerService.get(guildId)?.activeFilter
    }

    private fun buildFilters(preset: FilterPreset): Filters {
        val builder = FilterBuilder()

        when (preset) {
            FilterPreset.BASSBOOST -> {
                builder.setEqualizer(
                    listOf(
                        Band(0, 0.25f),
                        Band(1, 0.20f),
                        Band(2, 0.15f),
                        Band(3, 0.10f),
                        Band(4, 0.05f)
                    )
                )
            }

            FilterPreset.NIGHTCORE -> {
                builder.setTimescale(Timescale(speed = 1.3, pitch = 1.3, rate = 1.0))
            }

            FilterPreset.VAPORWAVE -> {
                builder.setTimescale(Timescale(speed = 0.8, pitch = 0.9, rate = 1.0))
            }

            FilterPreset.KARAOKE -> {
                builder.setKaraoke(
                    Karaoke(
                        level = 1.0f,
                        monoLevel = 1.0f,
                        filterBand = 220.0f,
                        filterWidth = 100.0f
                    )
                )
            }

            FilterPreset.EIGHT_D -> {
                builder.setRotation(Rotation(rotationHz = 0.2))
            }

            FilterPreset.TREMOLO -> {
                builder.setTremolo(Tremolo(frequency = 2.0f, depth = 0.5f))
            }

            FilterPreset.VIBRATO -> {
                builder.setVibrato(Vibrato(frequency = 2.0f, depth = 0.5f))
            }

            FilterPreset.NONE -> {
                // Reset all filters by setting them to null
                builder.setEqualizer(emptyList())
                builder.setKaraoke(null)
                builder.setTimescale(null)
                builder.setTremolo(null)
                builder.setVibrato(null)
                builder.setRotation(null)
                builder.setDistortion(null)
                builder.setChannelMix(null)
                builder.setLowPass(null)
            }
        }

        return builder.build()
    }
}
