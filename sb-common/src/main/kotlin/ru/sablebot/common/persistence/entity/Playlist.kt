package ru.sablebot.common.persistence.entity

import jakarta.persistence.*
import ru.sablebot.common.persistence.entity.base.GuildEntity
import java.util.*

@Entity
@Table(name = "playlist")
class Playlist(
    @OneToMany(
        mappedBy = "playlist",
        cascade = [CascadeType.ALL],
        fetch = FetchType.LAZY
    )
    @OrderColumn(name = "index")
    val items: MutableList<PlaylistItem> = mutableListOf(),
    @Column
    @Temporal(TemporalType.TIMESTAMP)
    val date: Date,
    @Column
    val uuid: String
) : GuildEntity() {
    @PrePersist
    @PreUpdate
    fun recalculate() {
        if (!items.isEmpty()) {
            items.forEachIndexed { index, item ->
                item.index = index
            }
        }
    }
}