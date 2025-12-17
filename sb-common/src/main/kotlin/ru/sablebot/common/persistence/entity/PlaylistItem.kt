package ru.sablebot.common.persistence.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import ru.sablebot.common.persistence.entity.base.BaseEntity

@Entity
@Table(name = "playlist_item")
class PlaylistItem(
    @ManyToOne(
        cascade = [
            CascadeType.PERSIST,
            CascadeType.REFRESH,
            CascadeType.DETACH
        ]
    )
    @JoinColumn(name = "requested_by_id")
    var requestedBy: LocalMember?
) : BaseEntity() {
    @ManyToOne(cascade = [CascadeType.REFRESH, CascadeType.DETACH, CascadeType.MERGE, CascadeType.PERSIST])
    @JoinColumn(name = "playlist_id")
    var playlist: Playlist? = null

    @Column
    var title: String = ""

    @Column
    var author: String = ""

    @Column(length = 1000)
    var identifier: String = ""

    @Column(length = 1000)
    var uri: String = ""

    @Column
    var length: Long = 0

    @Column(name = "is_stream")
    var stream: Boolean = false

    @Column
    var index: Int = 0

    @Column
    var type: String = ""

    @Column(name = "artwork_url")
    var artworkUri: String = ""

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column
    var data: ByteArray? = null

    constructor() : this(null)
}