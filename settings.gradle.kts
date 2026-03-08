pluginManagement {
    includeBuild("build-logic")
}

rootProject.name = "discordBot"

include(":sb-api")
include(":sb-common-worker")
include(":sb-worker")
include(":sb-common")
include(":modules")
include(":modules:sb-module-audio")
include(":modules:sb-module-moderation")
