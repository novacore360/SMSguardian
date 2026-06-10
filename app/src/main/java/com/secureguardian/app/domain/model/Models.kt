package com.secureguardian.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Threat classification levels for SMS messages
 */
enum class ThreatLevel {
    SAFE,        // No threats detected
    SUSPICIOUS,  // Potentially suspicious content
    RED_FLAG     // Confirmed phishing/fraud/illegal
}

/**
 * Core SMS message model with threat analysis metadata
 */
@Serializable
data class SmsMessage(
    val id: String,
    val sender: String,
    val senderDisplayName: String?,
    val isKnownContact: Boolean,
    val body: String,
    val timestamp: Long,
    val extractedLinks: List<String> = emptyList(),
    val extractedDomains: List<String> = emptyList(),
    val threatLevel: ThreatLevel = ThreatLevel.SAFE,
    val isBlocked: Boolean = false,
    val isRead: Boolean = false,
    val blockReason: String? = null
)

/**
 * Flagged domain entry from local or community database
 */
@Serializable
data class FlaggedDomain(
    val id: String,
    val domain: String,
    val domainHash: String,
    val threatLevel: ThreatLevel,
    val reportCount: Int,
    val lastReported: Long,
    val isPersonal: Boolean, // User's own flag vs community
    val reason: String? = null
)

/**
 * User profile model
 */
@Serializable
data class UserProfile(
    val id: String,
    val email: String,
    val createdAt: Long,
    val totalReports: Int = 0,
    val totalBlocked: Int = 0,
    val isBiometricEnabled: Boolean = false
)

/**
 * Threat report submission model
 */
@Serializable
data class ThreatReport(
    val reportType: ReportType,
    val content: String, // raw message, URL, or phone number
    val reason: ThreatReason,
    val notes: String? = null,
    val isAnonymous: Boolean = true,
    val extractedEntities: List<String> = emptyList()
)

enum class ReportType {
    MESSAGE, URL_DOMAIN, PHONE_NUMBER
}

enum class ThreatReason {
    PHISHING, FRAUD, SPAM, MALWARE, ILLEGAL_CONTENT, OTHER
}

/**
 * App statistics for home screen widget / weekly report
 */
data class SecurityStats(
    val totalBlocked: Int = 0,
    val blockedToday: Int = 0,
    val blockedThisWeek: Int = 0,
    val totalReported: Int = 0,
    val communityProtected: Int = 0
)

/**
 * Contact model
 */
@Serializable
data class Contact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val phoneHash: String
)
