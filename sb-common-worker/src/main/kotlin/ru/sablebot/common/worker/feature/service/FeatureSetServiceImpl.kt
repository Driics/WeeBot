package ru.sablebot.common.worker.feature.service

import org.springframework.aop.support.AopUtils
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.stereotype.Service
import ru.sablebot.common.model.FeatureSet
import ru.sablebot.common.worker.feature.provider.FeatureProvider

@Service
class FeatureSetServiceImpl(
    providers: List<FeatureSetProvider>?
) : FeatureSetService {

    private val providers: List<FeatureSetProvider> = (providers ?: emptyList())
        .sortedBy { p ->
            val targetClass = AopUtils.getTargetClass(p)
            AnnotatedElementUtils.findMergedAnnotation(targetClass, FeatureProvider::class.java)?.priority
                ?: Int.MAX_VALUE
        }

    override fun isAvailable(guildId: Long, featureSet: FeatureSet): Boolean =
        getAnyAvailable(guildId, featureSet) { provider, id, feature ->
            provider.isAvailable(id, feature)
        }

    override fun isAvailableForUser(userId: Long, featureSet: FeatureSet): Boolean =
        getAnyAvailable(userId, featureSet) { provider, id, feature ->
            provider.isAvailableForUser(id, feature)
        }

    override fun getByGuild(guildId: Long): Set<FeatureSet> =
        calculateFeatures(guildId) { provider, id -> provider.getByGuild(id) }

    override fun getByUser(userId: Long): Set<FeatureSet> =
        calculateFeatures(userId) { provider, id -> provider.getByUser(id) }

    private inline fun getAnyAvailable(
        id: Long,
        featureSet: FeatureSet,
        supplier: (FeatureSetProvider, Long, FeatureSet) -> Boolean
    ): Boolean {
        if (providers.isEmpty()) return true
        return providers.any { supplier(it, id, featureSet) }
    }

    private inline fun calculateFeatures(
        id: Long,
        supplier: (FeatureSetProvider, Long) -> Set<FeatureSet>
    ): Set<FeatureSet> {
        if (providers.isEmpty()) return FeatureSet.entries.toSet()

        val allFeaturesCount = FeatureSet.entries.size
        val result = mutableSetOf<FeatureSet>()

        for (provider in providers) {
            result += supplier(provider, id)
            if (result.size == allFeaturesCount) break
        }
        return result
    }
}