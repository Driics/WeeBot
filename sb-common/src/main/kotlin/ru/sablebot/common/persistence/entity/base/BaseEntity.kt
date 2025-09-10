package ru.sablebot.common.persistence.entity.base

import jakarta.persistence.*
import org.hibernate.Hibernate
import java.io.Serializable

@MappedSuperclass
abstract class BaseEntity : Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    open var id: Long? = null

    @Version
    @Column(name = "version")
    open var version: Long = 0

    override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as BaseEntity
        return id != null && id == other.id
    }

    override fun toString(): String {
        return "${this::class.qualifiedName} [ID=$id]"
    }
}