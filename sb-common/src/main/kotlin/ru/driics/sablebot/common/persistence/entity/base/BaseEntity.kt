package ru.driics.sablebot.common.persistence.entity.base

import jakarta.persistence.*
import java.io.Serializable

@MappedSuperclass
open class BaseEntity : Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    var id: Long = 0

    @Version
    @Column(name = "version")
    var version: Long = 0

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as BaseEntity
        return id == other.id
    }

    override fun toString(): String {
        return "${this::class.qualifiedName} [ID=$id]"
    }
}