package ru.driics.sablebot.common.persistence.entity.base

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class NamedReference(
    @Column(length = 21)
    var id: String = "",
    @Column
    var name: String = "",
) {
    @Transient
    val asChannelMention = "<#$id>"
    @Transient
    val asUserMention = "<@$id>"
}