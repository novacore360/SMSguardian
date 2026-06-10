package com.secureguardian.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.secureguardian.app.R
import com.secureguardian.app.domain.model.ThreatLevel
import com.secureguardian.app.presentation.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_THREAT = "channel_threat_alerts"
        const val CHANNEL_BLOCKED = "channel_blocked"
        const val CHANNEL_SERVICE = "channel_service"
        const val CHANNEL_WEEKLY = "channel_weekly"

        const val NOTIFICATION_SERVICE = 1001
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_THREAT,
                "Threat Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for detected phishing and fraud SMS messages"
                enableVibration(true)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BLOCKED,
                "Blocked Messages",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when suspicious messages are auto-blocked"
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE,
                "SMS Monitoring",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Background SMS monitoring service"
                setShowBadge(false)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WEEKLY,
                "Weekly Security Report",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Weekly summary of blocked threats"
            }
        )
    }

    fun showThreatAlert(
        sender: String,
        preview: String,
        threatLevel: ThreatLevel,
        notificationId: Int
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_TAB", "inbox")
        }
        val pi = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (title, color) = when (threatLevel) {
            ThreatLevel.RED_FLAG -> "🚨 RED FLAG: Phishing/Fraud Detected" to 0xFFE94560.toInt()
            ThreatLevel.SUSPICIOUS -> "⚠️ Suspicious Message Detected" to 0xFFFF8C00.toInt()
            ThreatLevel.SAFE -> "✅ Safe Message" to 0xFF0F9B58.toInt()
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_THREAT)
            .setSmallIcon(R.drawable.ic_shield_notification)
            .setContentTitle(title)
            .setContentText("From: $sender — $preview")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("From: $sender\n\n$preview")
            )
            .setColor(color)
            .setAutoCancel(true)
            .setPriority(if (threatLevel == ThreatLevel.RED_FLAG) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Notification permission not granted
        }
    }

    fun showBlockedNotification(sender: String, notificationId: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_BLOCKED)
            .setSmallIcon(R.drawable.ic_shield_notification)
            .setContentTitle("Blocked Suspicious Message")
            .setContentText("Auto-blocked message from $sender")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) { /* no-op */ }
    }

    fun buildServiceNotification() =
        NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_shield_notification)
            .setContentTitle("SecureSMS Guardian")
            .setContentText("Monitoring incoming messages…")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
}
