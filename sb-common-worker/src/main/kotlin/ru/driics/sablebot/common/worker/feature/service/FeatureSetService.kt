package ru.driics.sablebot.common.worker.feature.service

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User
import ru.driics.sablebot.common.model.FeatureSet

interface FeatureSetService {

    fun isAvailable(guildId: Long, featureSet: FeatureSet): Boolean

    fun isAvailableForUser(userId: Long, featureSet: FeatureSet): Boolean

    fun getByGuild(guildId: Long): Set<FeatureSet>

    fun getByUser(userId: Long): Set<FeatureSet>

    fun getAvailable(guild: Guild?): Set<FeatureSet> =
        guild?.let { getByGuild(it.idLong) } ?: emptySet()

    fun getAvailableByUser(user: User?): Set<FeatureSet> =
        user?.let { getByUser(it.idLong) } ?: emptySet()

    fun isAvailable(guildId: Long): Boolean =
        isAvailable(guildId, FeatureSet.BONUS)

    fun isAvailable(guild: Guild?): Boolean =
        isAvailable(guild, FeatureSet.BONUS)

    fun isAvailable(guild: Guild?, featureSet: FeatureSet): Boolean =
        guild?.let { isAvailable(it.idLong, featureSet) } ?: false

    fun isAvailableForUser(userId: Long): Boolean =
        isAvailableForUser(userId, FeatureSet.BONUS)

    fun isAvailableForUser(user: User?): Boolean =
        isAvailableForUser(user, FeatureSet.BONUS)

    fun isAvailableForUser(user: User?, featureSet: FeatureSet): Boolean =
        user?.let { isAvailableForUser(it.idLong, featureSet) } ?: false
}
