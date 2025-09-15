package ru.sablebot.common.persistence.entity.base

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.Transient

@Embeddable
data class NamedReference(
    @Column(length = 21)
    var id: String = "",
    @Column
    var name: String = "",
) {
    @get:Transient
    val asChannelMention: String
        get() = "<#$id>"

    @get:Transient
    val asUserMention: String
        get() = "<@$id>"
}