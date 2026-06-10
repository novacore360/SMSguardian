package com.secureguardian.app.util

import android.content.Context
import android.util.Base64
import timber.log.Timber
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * SHA-256 hashing utility for domains and phone numbers
 */
object HashUtil {
    fun sha256(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Timber.e(e, "Hashing failed")
            input
        }
    }

    fun normalizeDomain(url: String): String {
        return url.trim()
            .lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .split("/").first()
            .split("?").first()
            .split("#").first()
    }

    fun normalizePhone(phone: String): String {
        return phone.replace(Regex("[^+0-9]"), "")
    }
}

/**
 * Link and domain extraction from SMS message bodies
 */
object LinkExtractor {
    // Comprehensive URL pattern including obfuscated URLs
    private val URL_PATTERN = Regex(
        """(?i)(?:https?://|www\.|(?:[a-z0-9-]+\.)+(?:com|net|org|gov|edu|io|co|app|ph|xyz|info|biz|shop|store|click|link|me|online|site|web|page|ly|gl|to|gd|gg|tt|tl|cc|ws|mobi|tv|us|uk|au|ca|de|fr|jp|cn|in|br|mx|ru|nz|eu))(?:[^\s<>"')\]]*[^\s<>"')\].,;!?])""",
        setOf(RegexOption.MULTILINE)
    )

    // Pattern to find IP-based URLs (potential phishing)
    private val IP_URL_PATTERN = Regex(
        """(?:https?://)?(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(?:/[^\s]*)?"""
    )

    // Shortened URL domains that are commonly used in phishing
    private val SHORTENED_DOMAINS = setOf(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "short.io",
        "rebrand.ly", "cutt.ly", "is.gd", "buff.ly", "dlvr.it", "ift.tt"
    )

    fun extractLinks(text: String): List<String> {
        val links = mutableListOf<String>()
        
        URL_PATTERN.findAll(text).forEach { links.add(it.value) }
        IP_URL_PATTERN.findAll(text).forEach { 
            if (!links.contains(it.value)) links.add(it.value) 
        }
        
        return links.distinct()
    }

    fun extractDomains(text: String): List<String> {
        return extractLinks(text).map { link ->
            HashUtil.normalizeDomain(link)
        }.distinct().filter { it.isNotBlank() }
    }

    fun containsSuspiciousPatterns(text: String): Boolean {
        val suspiciousKeywords = listOf(
            "verify your account", "update your info", "click here now",
            "winner", "you won", "prize", "congratulations", "free gift",
            "limited time", "act now", "urgent", "suspended", "blocked",
            "OTP", "one-time password", "PIN", "your account will be",
            "bank account", "gcash", "maya", "paymaya", "verify now",
            "confirm your", "unusual activity", "security alert"
        )
        val lowerText = text.lowercase()
        return suspiciousKeywords.any { lowerText.contains(it.lowercase()) }
    }

    fun isShortenedUrl(url: String): Boolean {
        val domain = HashUtil.normalizeDomain(url)
        return SHORTENED_DOMAINS.any { domain == it || domain.endsWith(".$it") }
    }

    fun hasIpBasedUrl(text: String): Boolean {
        return IP_URL_PATTERN.containsMatchIn(text)
    }
}

/**
 * Threat analysis logic combining link extraction with domain database checking
 */
object ThreatAnalyzer {
    
    data class AnalysisResult(
        val links: List<String>,
        val domains: List<String>,
        val suspiciousScore: Int, // 0-100
        val reasons: List<String>
    )

    fun analyze(
        messageBody: String,
        flaggedDomainHashes: Set<String>
    ): AnalysisResult {
        val links = LinkExtractor.extractLinks(messageBody)
        val domains = LinkExtractor.extractDomains(messageBody)
        val reasons = mutableListOf<String>()
        var score = 0

        // Check against flagged domains
        domains.forEach { domain ->
            val hash = HashUtil.sha256(domain)
            if (flaggedDomainHashes.contains(hash)) {
                score += 80
                reasons.add("Known malicious domain: $domain")
            }
        }

        // Suspicious keywords in message
        if (LinkExtractor.containsSuspiciousPatterns(messageBody)) {
            score += 20
            reasons.add("Contains phishing keywords")
        }

        // IP-based URLs are very suspicious
        if (LinkExtractor.hasIpBasedUrl(messageBody)) {
            score += 40
            reasons.add("Contains IP-based URL (common in phishing)")
        }

        // Shortened URLs (need expansion to verify)
        links.filter { LinkExtractor.isShortenedUrl(it) }.forEach {
            score += 15
            reasons.add("Contains shortened URL: $it")
        }

        // Multiple links in a single SMS is suspicious
        if (links.size > 2) {
            score += 10
            reasons.add("Multiple links in single message")
        }

        return AnalysisResult(links, domains, minOf(score, 100), reasons)
    }
}
