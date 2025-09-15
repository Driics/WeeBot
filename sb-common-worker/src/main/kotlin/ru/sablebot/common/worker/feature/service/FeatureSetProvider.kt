package ru.sablebot.common.worker.feature.service

import ru.sablebot.common.model.FeatureSet

interface FeatureSetProvider {

    fun isAvailable(guildId: Long, featureSet: FeatureSet): Boolean

    fun isAvailableForUser(userId: Long, featureSet: FeatureSet): Boolean

    fun getByGuild(guildId: Long): Set<FeatureSet>

    fun getByUser(userId: Long): Set<FeatureSet>
}
