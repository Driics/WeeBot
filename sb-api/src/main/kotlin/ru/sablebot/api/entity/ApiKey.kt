package ru.sablebot.api.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(
    name = "api_key",
    indexes = [
        Index(name = "idx_api_key_guild_id", columnList = "guild_id"),
        Index(name = "idx_api_key_hashed_key", columnList = "hashed_key")
    ]
)
class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "guild_id", nullable = false)
    var guildId: Long = 0

    @Column(name = "name", nullable = false, length = 100)
    var name: String = ""

    @Column(name = "hashed_key", nullable = false, unique = true)
    var hashedKey: String = ""

    @Column(name = "key_prefix", nullable = false, length = 10)
    var keyPrefix: String = ""

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scopes", columnDefinition = "JSONB")
    var scopes: List<String> = emptyList()

    @Column(name = "created_by", nullable = false, length = 21)
    var createdBy: String = ""

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "last_used_at")
    var lastUsedAt: Instant? = null

    @Column(name = "revoked", nullable = false)
    var revoked: Boolean = false
}
