package ru.sablebot.common.persistence.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import ru.sablebot.common.persistence.entity.base.BaseEntity
import java.time.Instant

@Entity
@Table(
    name = "gulag",
    indexes = [
        Index(name = "ix_gulag_snowflake", columnList = "snowflake"),
        Index(name = "ix_gulag_moderator_id", columnList = "moderator_id")
    ]
)
open class Gulag(
    @Column(nullable = false)
    open var snowflake: Long = 0,
    @Lob
    @Column(nullable = false)
    open var reason: String = "",
    @ManyToOne(fetch = FetchType.LAZY, optional = false,
        cascade = [CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH])
    @JoinColumn(name = "moderator_id", nullable = false)
    open var moderator: LocalUser,

    @Column(nullable = false)
    @field:CreationTimestamp
    open var date: Instant = Instant.now(),
) : BaseEntity()