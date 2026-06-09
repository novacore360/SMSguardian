package com.secureguardian.app.data.repository

import com.secureguardian.app.data.local.dao.*
import com.secureguardian.app.data.local.entities.*
import com.secureguardian.app.data.remote.SupabaseDataSource
import com.secureguardian.app.data.remote.SupabaseFlaggedDomain
import com.secureguardian.app.data.remote.SupabaseThreatReport
import com.secureguardian.app.domain.model.*
import com.secureguardian.app.util.HashUtil
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepository @Inject constructor(
    private val messageDao: MessageDao,
    private val flaggedDomainDao: FlaggedDomainDao,
    private val contactDao: ContactDao,
    private val blockedMessageDao: BlockedMessageDao,
    private val statsDao: StatsDao,
    private val supabase: SupabaseDataSource
) {

    // ── Auth ──────────────────────────────────────────────────────────────

    suspend fun signUp(email: String, password: String): Result<Unit> = runCatching {
        supabase.signUp(email, password)
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        supabase.signIn(email, password)
    }

    suspend fun signOut(): Result<Unit> = runCatching { supabase.signOut() }

    suspend fun resetPassword(email: String): Result<Unit> = runCatching {
        supabase.resetPassword(email)
    }

    fun getCurrentUser() = supabase.getCurrentUser()

    fun getSessionFlow() = supabase.getSessionFlow()

    // ── Messages ──────────────────────────────────────────────────────────

    fun getAllMessages(): Flow<List<SmsMessage>> =
        messageDao.getAllMessages().map { it.map { e -> e.toDomain() } }

    fun getInboxMessages(): Flow<List<SmsMessage>> =
        messageDao.getInboxMessages().map { it.map { e -> e.toDomain() } }

    fun getFlaggedMessages(): Flow<List<SmsMessage>> =
        messageDao.getFlaggedMessages().map { it.map { e -> e.toDomain() } }

    fun getSafeMessages(): Flow<List<SmsMessage>> =
        messageDao.getSafeMessages().map { it.map { e -> e.toDomain() } }

    fun getUnreadCount(): Flow<Int> = messageDao.getUnreadCount()

    suspend fun markMessageRead(id: String) = messageDao.markAsRead(id)

    suspend fun flagMessage(message: SmsMessage) {
        // Update local threat level
        messageDao.updateThreatLevel(message.id, ThreatLevel.RED_FLAG, true)

        // Add extracted domains to personal flagged list
        message.extractedDomains.forEach { domain ->
            val hash = HashUtil.sha256(domain)
            if (flaggedDomainDao.findByHash(hash) == null) {
                flaggedDomainDao.insertDomain(
                    FlaggedDomainEntity(
                        id = UUID.randomUUID().toString(),
                        domain = domain,
                        domainHash = hash,
                        threatLevel = ThreatLevel.RED_FLAG,
                        reportCount = 1,
                        lastReported = System.currentTimeMillis(),
                        isPersonal = true
                    )
                )
            }
        }

        // Report to Supabase
        val userId = getCurrentUser()?.id ?: return
        message.extractedDomains.forEach { domain ->
            try {
                supabase.insertFlaggedDomain(
                    SupabaseFlaggedDomain(
                        reporter_user_id = userId,
                        domain = domain,
                        domain_hash = HashUtil.sha256(domain),
                        threat_level = ThreatLevel.RED_FLAG.name,
                        is_personal = true,
                        last_reported = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync flagged domain")
            }
        }
    }

    // ── Flagged Domains ───────────────────────────────────────────────────

    fun getAllFlaggedDomains(): Flow<List<FlaggedDomain>> =
        flaggedDomainDao.getAllFlaggedDomains().map { it.map { e -> e.toDomain() } }

    fun getPersonalFlaggedDomains(): Flow<List<FlaggedDomain>> =
        flaggedDomainDao.getPersonalFlaggedDomains().map { it.map { e -> e.toDomain() } }

    fun getCommunityFlaggedDomains(): Flow<List<FlaggedDomain>> =
        flaggedDomainDao.getCommunityFlaggedDomains().map { it.map { e -> e.toDomain() } }

    fun searchFlaggedDomains(query: String): Flow<List<FlaggedDomain>> =
        flaggedDomainDao.searchDomains(query).map { it.map { e -> e.toDomain() } }

    suspend fun checkDomain(domain: String): FlaggedDomain? {
        val normalized = HashUtil.normalizeDomain(domain)
        val hash = HashUtil.sha256(normalized)
        // Check local first
        val local = flaggedDomainDao.findByHash(hash)
        if (local != null) return local.toDomain()

        // Check remote
        return try {
            supabase.checkDomainHash(hash)?.let {
                FlaggedDomain(
                    id = it.id ?: UUID.randomUUID().toString(),
                    domain = it.domain,
                    domainHash = it.domain_hash,
                    threatLevel = ThreatLevel.valueOf(it.threat_level),
                    reportCount = it.report_count,
                    lastReported = System.currentTimeMillis(),
                    isPersonal = false,
                    reason = it.reason
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Remote domain check failed")
            null
        }
    }

    // ── Reports ───────────────────────────────────────────────────────────

    suspend fun submitThreatReport(report: ThreatReport): Result<Unit> = runCatching {
        val userId = getCurrentUser()?.id ?: throw IllegalStateException("Not authenticated")
        supabase.submitReport(
            SupabaseThreatReport(
                reporter_user_id = userId,
                report_type = report.reportType.name,
                content = report.content,
                reason = report.reason.name,
                notes = report.notes,
                is_anonymous = report.isAnonymous,
                extracted_entities = kotlinx.serialization.json.Json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.builtins.serializer()),
                    report.extractedEntities
                )
            )
        )
    }

    // ── Community Sync ────────────────────────────────────────────────────

    suspend fun syncCommunityFlaggedDomains(): Result<Int> = runCatching {
        val remote = supabase.getCommunityFlaggedDomains()
        val entities = remote.map {
            FlaggedDomainEntity(
                id = it.id ?: UUID.randomUUID().toString(),
                domain = it.domain,
                domainHash = it.domain_hash,
                threatLevel = ThreatLevel.valueOf(it.threat_level),
                reportCount = it.report_count,
                lastReported = System.currentTimeMillis(),
                isPersonal = false,
                reason = it.reason
            )
        }
        flaggedDomainDao.insertDomains(entities)
        entities.size
    }

    // ── Blocked Messages ──────────────────────────────────────────────────

    fun getBlockedMessages(): Flow<List<BlockedMessageEntity>> =
        blockedMessageDao.getAllBlockedMessages()

    suspend fun overrideBlock(id: Long) = blockedMessageDao.overrideBlock(id)

    // ── Stats ─────────────────────────────────────────────────────────────

    fun getStats(): Flow<SecurityStatsEntity?> = statsDao.getStats()
}

// ── Mapper extensions ─────────────────────────────────────────────────────

private fun CachedMessageEntity.toDomain(): SmsMessage {
    val links = try { Json.decodeFromString<List<String>>(extractedLinksJson) } catch (e: Exception) { emptyList() }
    val domains = try { Json.decodeFromString<List<String>>(extractedDomainsJson) } catch (e: Exception) { emptyList() }
    return SmsMessage(
        id = id, sender = sender, senderDisplayName = senderDisplayName,
        isKnownContact = isKnownContact, body = body, timestamp = timestamp,
        extractedLinks = links, extractedDomains = domains, threatLevel = threatLevel,
        isBlocked = isBlocked, isRead = isRead, blockReason = blockReason
    )
}

private fun FlaggedDomainEntity.toDomain() = FlaggedDomain(
    id = id, domain = domain, domainHash = domainHash, threatLevel = threatLevel,
    reportCount = reportCount, lastReported = lastReported,
    isPersonal = isPersonal, reason = reason
)
