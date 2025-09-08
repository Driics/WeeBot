package ru.driics.sablebot.common.persistence.entity

import jakarta.persistence.*
import org.jetbrains.annotations.NotNull
import ru.driics.sablebot.common.persistence.entity.base.BaseEntity
import java.util.Date

@Entity
@Table(name = "gulag")
open class Gulag(
    @Column
    var snowflake: Long = 0,
    @Column(columnDefinition = "TEXT")
    var reason: String = "",
    @ManyToOne(fetch = FetchType.LAZY, optional = false,
        cascade = [CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH])
    @JoinColumn(name = "moderator_id", nullable = false)
    var moderator: LocalUser? = null,
    @Column
    @Temporal(TemporalType.TIMESTAMP)
    var date: Date = Date(),
) : BaseEntity()