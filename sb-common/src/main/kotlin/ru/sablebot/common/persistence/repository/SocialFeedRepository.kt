package ru.sablebot.common.persistence.repository

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.sablebot.common.persistence.entity.FeedType
import ru.sablebot.common.persistence.entity.SocialFeed
import ru.sablebot.common.persistence.repository.base.GuildRepository
import java.time.Instant

@Repository
interface SocialFeedRepository : GuildRepository<SocialFeed> {

    @Query("SELECT f FROM SocialFeed f WHERE f.guildId = :guildId")
    fun findAllByGuildId(@Param("guildId") guildId: Long): List<SocialFeed>

    @Query("SELECT f FROM SocialFeed f WHERE f.enabled = true AND (f.lastCheckTime IS NULL OR f.lastCheckTime < :checkTimeBefore)")
    fun findFeedsDueForCheck(@Param("checkTimeBefore") checkTimeBefore: Instant): List<SocialFeed>

    @Query("SELECT COUNT(f) FROM SocialFeed f WHERE f.guildId = :guildId")
    fun countByGuildId(@Param("guildId") guildId: Long): Long

    @Query("SELECT f FROM SocialFeed f WHERE f.feedType = :feedType AND f.enabled = true")
    fun findByFeedTypeAndEnabled(@Param("feedType") feedType: FeedType): List<SocialFeed>
}
