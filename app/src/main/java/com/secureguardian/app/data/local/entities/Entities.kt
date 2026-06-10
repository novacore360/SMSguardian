package com.secureguardian.app.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.secureguardian.app.domain.model.ThreatLevel

/**
 * Local cached SMS message entity
 */
@Entity(
    tableName = "cached_messages",
    indices = [Index(value = ["sender"]), Index(value = ["timestamp"]), Index(value = ["threatLevel"])]
)
data class CachedMessageEntity(
    @PrimaryKey val id: String,
    val sender: String,
    val senderDisplayName: String?,
    val isKnownContact: Boolean,
    val body: String,             // Stored raw as requested
    val timestamp: Long,
    val extractedLinksJson: String = "[]",
    val extractedDomainsJson: String = "[]",
    val threatLevel: ThreatLevel = ThreatLevel.SAFE,
    val isBlocked: Boolean = false,
    val isRead: Boolean = false,
    val blockReason: String? = null,
    val syncedAt: Long? = null
)

/**
 * Local flagged domain entity for offline checking
 */
@Entity(
    tableName = "flagged_domains",
    indices = [Index(value = ["domainHash"], unique = true), Index(value = ["domain"])]
)
data class FlaggedDomainEntity(
    @PrimaryKey val id: String,
    val domain: String,
    val domainHash: String,
    val threatLevel: ThreatLevel,
    val reportCount: Int = 1,
    val lastReported: Long,
    val isPersonal: Boolean,
    val reason: String? = null,
    val syncedAt: Long = System.currentTimeMillis()
)

/**
 * Local contact cache
 */
@Entity(
    tableName = "contacts",
    indices = [Index(value = ["phoneHash"]), Index(value = ["phoneNumber"])]
)
data class ContactEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phoneNumber: String,    // Stored raw as requested
    val phoneHash: String,
    val syncedAt: Long = System.currentTimeMillis()
)

/**
 * Quarantined/blocked messages
 */
@Entity(
    tableName = "blocked_messages",
    indices = [Index(value = ["originalMessageId"]), Index(value = ["blockedAt"])]
)
data class BlockedMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalMessageId: String,
    val sender: String,
    val body: String,
    val blockReason: String,
    val blockedAt: Long = System.currentTimeMillis(),
    val isReviewed: Boolean = false,
    val isOverridden: Boolean = false
)

/**
 * App statistics cache
 */
@Entity(tableName = "security_stats")
data class SecurityStatsEntity(
    @PrimaryKey val id: Int = 1, // Single row
    val totalBlocked: Int = 0,
    val blockedToday: Int = 0,
    val blockedThisWeek: Int = 0,
    val totalReported: Int = 0,
    val communityProtected: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)
