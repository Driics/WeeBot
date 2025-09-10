package ru.sablebot.common.persistence.entity.base

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Transient
import ru.sablebot.common.model.FeatureSet

@MappedSuperclass
open class FeaturedUserEntity : UserEntity() {

    @Column
    var features: String? = null

    @get:Transient
    var featureSets: Set<FeatureSet>
        get() = features
            ?.split(',')
            ?.mapNotNull { it.trim().takeIf { name -> name.isNotEmpty() } }
            ?.mapNotNull { runCatching { FeatureSet.valueOf(it) }.getOrNull() }
            ?.toSet()
            ?: emptySet()
        set(value) {
            features = value.joinToString(",") { it.name }
        }

    @Transient
    fun appendFeatureSets(newFeatures: Set<FeatureSet>) {
        featureSets = featureSets + newFeatures
    }
}
