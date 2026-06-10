package com.secureguardian.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import com.secureguardian.app.service.SmsMonitoringService
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * Receives incoming SMS broadcasts and forwards to the monitoring service
 * Priority 999 ensures we intercept before other apps
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            // Group messages from the same sender (multi-part SMS)
            val grouped = mutableMapOf<String, StringBuilder>()
            messages.forEach { msg ->
                val sender = msg.displayOriginatingAddress ?: "Unknown"
                grouped.getOrPut(sender) { StringBuilder() }.append(msg.messageBody)
            }

            grouped.forEach { (sender, body) ->
                Timber.d("SMS received from: $sender, length: ${body.length}")

                val serviceIntent = Intent(context, SmsMonitoringService::class.java).apply {
                    action = SmsMonitoringService.ACTION_PROCESS_SMS
                    putExtra(SmsMonitoringService.EXTRA_SENDER, sender)
                    putExtra(SmsMonitoringService.EXTRA_BODY, body.toString())
                    putExtra(SmsMonitoringService.EXTRA_TIMESTAMP, System.currentTimeMillis())
                }

                context.startForegroundService(serviceIntent)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error processing received SMS")
        }
    }
}

/**
 * Restarts background service after device reboot
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Timber.d("Boot completed, starting SMS monitoring service")
            val serviceIntent = Intent(context, SmsMonitoringService::class.java).apply {
                action = SmsMonitoringService.ACTION_START_MONITORING
            }
            context.startForegroundService(serviceIntent)
        }
    }
}
