package com.secureguardian.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.secureguardian.app.data.local.dao.BlockedMessageDao
import com.secureguardian.app.data.local.dao.ContactDao
import com.secureguardian.app.data.local.dao.FlaggedDomainDao
import com.secureguardian.app.data.local.dao.MessageDao
import com.secureguardian.app.data.local.entities.BlockedMessageEntity
import com.secureguardian.app.data.local.entities.CachedMessageEntity
import com.secureguardian.app.domain.model.ThreatLevel
import com.secureguardian.app.util.HashUtil
import com.secureguardian.app.util.NotificationHelper
import com.secureguardian.app.util.ThreatAnalyzer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@AndroidEntryPoint
class SmsMonitoringService : Service() {

    companion object {
        const val ACTION_START_MONITORING = "ACTION_START_MONITORING"
        const val ACTION_PROCESS_SMS = "ACTION_PROCESS_SMS"
        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_BODY = "extra_body"
        const val EXTRA_TIMESTAMP = "extra_timestamp"
    }

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var messageDao: MessageDao
    @Inject lateinit var flaggedDomainDao: FlaggedDomainDao
    @Inject lateinit var contactDao: ContactDao
    @Inject lateinit var blockedMessageDao: BlockedMessageDao

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val notificationIdCounter = AtomicInteger(2000)

    override fun onCreate() {
        super.onCreate()
        startForeground(
            NotificationHelper.NOTIFICATION_SERVICE,
            notificationHelper.buildServiceNotification()
        )
        Timber.d("SmsMonitoringService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PROCESS_SMS -> {
                val sender = intent.getStringExtra(EXTRA_SENDER) ?: return START_STICKY
                val body = intent.getStringExtra(EXTRA_BODY) ?: return START_STICKY
                val timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
                processIncomingSms(sender, body, timestamp)
            }
            ACTION_START_MONITORING -> {
                Timber.d("Monitoring mode started")
            }
        }
        return START_STICKY
    }

    private fun processIncomingSms(sender: String, body: String, timestamp: Long) {
        serviceScope.launch {
            try {
                // 1. Load cached flagged domain hashes for offline checking
                val flaggedHashes = flaggedDomainDao.getAllFlaggedDomains()
                    .first()
                    .map { it.domainHash }
                    .toSet()

                // 2. Run threat analysis
                val analysis = ThreatAnalyzer.analyze(body, flaggedHashes)

                // 3. Determine threat level
                val threatLevel = when {
                    analysis.suspiciousScore >= 60 -> ThreatLevel.RED_FLAG
                    analysis.suspiciousScore >= 20 -> ThreatLevel.SUSPICIOUS
                    else -> ThreatLevel.SAFE
                }

                // 4. Resolve contact name
                val normalizedPhone = HashUtil.normalizePhone(sender)
                val phoneHash = HashUtil.sha256(normalizedPhone)
                val contact = contactDao.findByPhoneHash(phoneHash)
                val displayName = contact?.name
                val isKnownContact = contact != null

                // 5. Build entity
                val messageId = UUID.randomUUID().toString()
                val entity = CachedMessageEntity(
                    id = messageId,
                    sender = sender,
                    senderDisplayName = displayName,
                    isKnownContact = isKnownContact,
                    body = body,  // Raw storage as requested
                    timestamp = timestamp,
                    extractedLinksJson = Json.encodeToString(analysis.links),
                    extractedDomainsJson = Json.encodeToString(analysis.domains),
                    threatLevel = threatLevel,
                    isBlocked = threatLevel == ThreatLevel.RED_FLAG,
                    blockReason = if (threatLevel == ThreatLevel.RED_FLAG) analysis.reasons.joinToString("; ") else null
                )

                messageDao.insertMessage(entity)

                // 6. If blocked, add to quarantine
                if (threatLevel == ThreatLevel.RED_FLAG) {
                    blockedMessageDao.insertBlockedMessage(
                        BlockedMessageEntity(
                            originalMessageId = messageId,
                            sender = sender,
                            body = body,
                            blockReason = analysis.reasons.joinToString("; ")
                        )
                    )
                    notificationHelper.showBlockedNotification(
                        displayName ?: sender,
                        notificationIdCounter.getAndIncrement()
                    )
                } else {
                    // 7. Notify for suspicious and safe (only show suspicious/red)
                    if (threatLevel != ThreatLevel.SAFE) {
                        val preview = body.take(80).replace("\n", " ")
                        notificationHelper.showThreatAlert(
                            displayName ?: sender,
                            preview,
                            threatLevel,
                            notificationIdCounter.getAndIncrement()
                        )
                    }
                }

                Timber.d("Processed SMS from $sender: $threatLevel (score=${analysis.suspiciousScore})")

            } catch (e: Exception) {
                Timber.e(e, "Error processing SMS")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}

