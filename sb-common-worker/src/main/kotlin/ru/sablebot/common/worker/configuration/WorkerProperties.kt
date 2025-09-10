package ru.sablebot.common.worker.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import ru.sablebot.common.model.FeatureSet

@Component
@ConfigurationProperties("sablebot.worker")
open class WorkerProperties {

    var discord: Discord = Discord()
    var audio: Audio = Audio()
    var stats: Stats = Stats()
    var events: Events = Events()
    var aiml: Aiml = Aiml()
    var audit: Audit = Audit()
    var patreon: Patreon = Patreon()
    var dogApi: DogAPI = DogAPI()
    var support: Support = Support()
    var commands: Commands = Commands()

    data class Discord(
        var shardsTotal: Int = 0,
        var token: String = "",
        var playingStatus: String = "",
        var reactionsTtlMs: Long = 3600000L
    )

    data class Stats(
        var discordbotsOrgToken: String = "",
        var discordbotsGgToken: String = ""
    )

    data class Events(
        var asyncExecution: Boolean = true,
        var corePoolSize: Int = 5,
        var maxPoolSize: Int = 5
    )

    data class Aiml(
        var enabled: Boolean = true,
        var brainsPath: String = ""
    )

    data class Audit(
        var keepMonths: Int = 1,
        var historyEnabled: Boolean = true,
        var historyDays: Int = 7,
        var historyEncryption: Boolean = true
    )

    data class Commands(
        var invokeLogging: Boolean = true,
        var executionThresholdMs: Int = 1000,
        var disabled: List<String> = emptyList()
    )

    data class Audio(
        var resamplingQuality: String = "MEDIUM",
        var searchProvider: String = "youTube",
        var frameBufferDuration: Int = 2000,
        var itemLoaderThreadPoolSize: Int = 500,
        var panelRefreshInterval: Int = 5000,
        var lavalink: Lavalink = Lavalink(),
        var yandexProxy: YandexProxy = YandexProxy()
    ) {
        data class Lavalink(
            var enabled: Boolean = false,
            var discovery: LavalinkDiscovery = LavalinkDiscovery(),
            var nodes: List<LavalinkNode> = emptyList()
        ) {
            data class LavalinkDiscovery(
                var enabled: Boolean = false,
                var serviceName: String = "",
                var password: String = ""
            )

            data class LavalinkNode(
                var name: String = "",
                var url: String = "",
                var password: String = ""
            )
        }

        data class YandexProxy(
            var host: String = "",
            var port: Int = 0
        )
    }

    data class Patreon(
        var campaignId: String = "1552419",
        var webhookSecret: String = "",
        var accessToken: String = "",
        var refreshToken: String = "",
        var updateEnabled: Boolean = false,
        var updateInterval: Int = 600000
    )

    data class DogAPI(
        var userId: String = "",
        var key: String = ""
    )

    data class Support(
        var guildId: Long = 0L,
        var donatorRoleId: Long = 0L,
        var moderatorRoleId: Long = 0L,
        var emergencyChannelId: Long = 0L,
        var featuredRoles: Map<String, Set<FeatureSet>> = emptyMap()
    )
}