package com.secureguardian.app.data.local.dao

import androidx.room.*
import com.secureguardian.app.data.local.entities.*
import com.secureguardian.app.domain.model.ThreatLevel
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM cached_messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<CachedMessageEntity>>

    @Query("SELECT * FROM cached_messages WHERE isBlocked = 0 ORDER BY timestamp DESC")
    fun getInboxMessages(): Flow<List<CachedMessageEntity>>

    @Query("SELECT * FROM cached_messages WHERE threatLevel = 'RED_FLAG' OR isBlocked = 1 ORDER BY timestamp DESC")
    fun getFlaggedMessages(): Flow<List<CachedMessageEntity>>

    @Query("SELECT * FROM cached_messages WHERE threatLevel = 'SAFE' AND isBlocked = 0 ORDER BY timestamp DESC")
    fun getSafeMessages(): Flow<List<CachedMessageEntity>>

    @Query("SELECT * FROM cached_messages WHERE id = :id")
    suspend fun getMessageById(id: String): CachedMessageEntity?

    @Query("SELECT * FROM cached_messages WHERE sender = :sender ORDER BY timestamp DESC")
    fun getMessagesBySender(sender: String): Flow<List<CachedMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: CachedMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<CachedMessageEntity>)

    @Update
    suspend fun updateMessage(message: CachedMessageEntity)

    @Query("UPDATE cached_messages SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: String)

    @Query("UPDATE cached_messages SET threatLevel = :level, isBlocked = :isBlocked WHERE id = :id")
    suspend fun updateThreatLevel(id: String, level: ThreatLevel, isBlocked: Boolean)

    @Delete
    suspend fun deleteMessage(message: CachedMessageEntity)

    @Query("DELETE FROM cached_messages WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("SELECT COUNT(*) FROM cached_messages WHERE isRead = 0 AND isBlocked = 0")
    fun getUnreadCount(): Flow<Int>
}

@Dao
interface FlaggedDomainDao {

    @Query("SELECT * FROM flagged_domains ORDER BY reportCount DESC")
    fun getAllFlaggedDomains(): Flow<List<FlaggedDomainEntity>>

    @Query("SELECT * FROM flagged_domains WHERE isPersonal = 1 ORDER BY lastReported DESC")
    fun getPersonalFlaggedDomains(): Flow<List<FlaggedDomainEntity>>

    @Query("SELECT * FROM flagged_domains WHERE isPersonal = 0 ORDER BY reportCount DESC")
    fun getCommunityFlaggedDomains(): Flow<List<FlaggedDomainEntity>>

    @Query("SELECT * FROM flagged_domains WHERE domainHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): FlaggedDomainEntity?

    @Query("SELECT * FROM flagged_domains WHERE domain LIKE '%' || :query || '%' ORDER BY reportCount DESC")
    fun searchDomains(query: String): Flow<List<FlaggedDomainEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDomain(domain: FlaggedDomainEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDomains(domains: List<FlaggedDomainEntity>)

    @Query("UPDATE flagged_domains SET reportCount = reportCount + 1, lastReported = :timestamp WHERE domainHash = :hash")
    suspend fun incrementReportCount(hash: String, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteDomain(domain: FlaggedDomainEntity)

    @Query("SELECT COUNT(*) FROM flagged_domains")
    suspend fun getTotalCount(): Int
}

@Dao
interface ContactDao {

    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE phoneHash = :hash LIMIT 1")
    suspend fun findByPhoneHash(hash: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE phoneNumber = :phone LIMIT 1")
    suspend fun findByPhoneNumber(phone: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<ContactEntity>)

    @Query("DELETE FROM contacts")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM contacts")
    suspend fun getCount(): Int
}

@Dao
interface BlockedMessageDao {

    @Query("SELECT * FROM blocked_messages ORDER BY blockedAt DESC")
    fun getAllBlockedMessages(): Flow<List<BlockedMessageEntity>>

    @Query("SELECT * FROM blocked_messages WHERE isReviewed = 0 ORDER BY blockedAt DESC")
    fun getUnreviewedBlockedMessages(): Flow<List<BlockedMessageEntity>>

    @Insert
    suspend fun insertBlockedMessage(message: BlockedMessageEntity)

    @Query("UPDATE blocked_messages SET isReviewed = 1 WHERE id = :id")
    suspend fun markAsReviewed(id: Long)

    @Query("UPDATE blocked_messages SET isOverridden = 1 WHERE id = :id")
    suspend fun overrideBlock(id: Long)

    @Query("SELECT COUNT(*) FROM blocked_messages WHERE blockedAt > :since")
    suspend fun getCountSince(since: Long): Int
}

@Dao
interface StatsDao {

    @Query("SELECT * FROM security_stats WHERE id = 1")
    fun getStats(): Flow<SecurityStatsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateStats(stats: SecurityStatsEntity)

    @Query("UPDATE security_stats SET totalBlocked = totalBlocked + 1, blockedToday = blockedToday + 1 WHERE id = 1")
    suspend fun incrementBlocked()
}
