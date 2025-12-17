package ru.sablebot.module.audio.service.impl

import org.springframework.stereotype.Service
import ru.sablebot.common.worker.configuration.WorkerProperties
import ru.sablebot.common.worker.event.service.ContextService
import ru.sablebot.common.worker.feature.service.FeatureSetService
import ru.sablebot.common.worker.shared.service.DiscordService

@Service
class PlayerServiceImpl(
    private val messageManager: AudioMessageManager,
    private val discordService: DiscordService,
    private val storedPlaylistService: StoredPlaylistService,
    private val musicConfigService: MusicConfigService,
    private val contextService: ContextService,
    private val lavaAudioService: LavalinkV4AudioService,
    private val validationService: ValidationService,
    private val youTubeService: YouTubeService,
    private val featureSetService: FeatureSetService,
    private val workerProperties: WorkerProperties,
    private val searchProviders: List<AudioSearchProvider>,
    private val trackLoader: LavalinkTrackLoader
) {
    // TODO
}