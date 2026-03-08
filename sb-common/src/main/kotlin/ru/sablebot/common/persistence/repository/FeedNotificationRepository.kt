package ru.sablebot.common.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.sablebot.common.persistence.entity.FeedNotification
import java.time.Instant

@Repository
interface FeedNotificationRepository : JpaRepository<FeedNotification, Long> {

    @Query("SELECT CASE WHEN COUNT(n) > 0 THEN true ELSE false END FROM FeedNotification n WHERE n.feedId = :feedId AND n.externalItemId = :externalItemId")
    fun existsByFeedIdAndExternalItemId(
        @Param("feedId") feedId: Long,
        @Param("externalItemId") externalItemId: String
    ): Boolean

    @Query("SELECT n FROM FeedNotification n WHERE n.feedId = :feedId ORDER BY n.sentAt DESC")
    fun findAllByFeedIdOrderBySentAtDesc(@Param("feedId") feedId: Long): List<FeedNotification>

    @Query("SELECT n FROM FeedNotification n WHERE n.guildId = :guildId ORDER BY n.sentAt DESC")
    fun findAllByGuildIdOrderBySentAtDesc(@Param("guildId") guildId: Long): List<FeedNotification>

    @Modifying
    @Query("DELETE FROM FeedNotification n WHERE n.feedId = :feedId")
    fun deleteByFeedId(@Param("feedId") feedId: Long)

    @Modifying
    @Query("DELETE FROM FeedNotification n WHERE n.sentAt < :before")
    fun deleteOlderThan(@Param("before") before: Instant)
}
