package com.secureguardian.app

import com.secureguardian.app.util.HashUtil
import com.secureguardian.app.util.LinkExtractor
import com.secureguardian.app.util.ThreatAnalyzer
import org.junit.Assert.*
import org.junit.Test

class LinkExtractorTest {

    @Test
    fun `extracts simple https URL`() {
        val text = "Click here: https://malicious-site.com/steal-data"
        val links = LinkExtractor.extractLinks(text)
        assertTrue(links.any { it.contains("malicious-site.com") })
    }

    @Test
    fun `extracts www URL without scheme`() {
        val text = "Visit www.phishing-bank.com for your verification"
        val links = LinkExtractor.extractLinks(text)
        assertTrue(links.isNotEmpty())
    }

    @Test
    fun `detects IP-based URL`() {
        val text = "Verify now: http://192.168.1.100/fake-bank"
        assertTrue(LinkExtractor.hasIpBasedUrl(text))
    }

    @Test
    fun `detects no IP in normal text`() {
        val text = "Your OTP is 123456"
        assertFalse(LinkExtractor.hasIpBasedUrl(text))
    }

    @Test
    fun `detects shortened URLs`() {
        val url = "https://bit.ly/abc123"
        assertTrue(LinkExtractor.isShortenedUrl(url))
    }

    @Test
    fun `extracts domain from URL`() {
        val text = "Check https://www.gcash-verify.ph/login now"
        val domains = LinkExtractor.extractDomains(text)
        assertTrue(domains.any { it == "gcash-verify.ph" })
    }

    @Test
    fun `detects suspicious keywords`() {
        val text = "Your GCash account is suspended. Verify now to unlock."
        assertTrue(LinkExtractor.containsSuspiciousPatterns(text))
    }

    @Test
    fun `safe message has no suspicious patterns`() {
        val text = "Hi, meeting at 3pm today."
        assertFalse(LinkExtractor.containsSuspiciousPatterns(text))
    }
}

class HashUtilTest {

    @Test
    fun `sha256 produces consistent hash`() {
        val h1 = HashUtil.sha256("example.com")
        val h2 = HashUtil.sha256("example.com")
        assertEquals(h1, h2)
    }

    @Test
    fun `sha256 different inputs produce different hashes`() {
        val h1 = HashUtil.sha256("example.com")
        val h2 = HashUtil.sha256("evil.com")
        assertNotEquals(h1, h2)
    }

    @Test
    fun `normalize domain strips www and https`() {
        assertEquals("example.com", HashUtil.normalizeDomain("https://www.example.com/path?q=1"))
        assertEquals("example.com", HashUtil.normalizeDomain("http://example.com"))
        assertEquals("example.com", HashUtil.normalizeDomain("www.example.com"))
    }
}

class ThreatAnalyzerTest {

    @Test
    fun `phishing message scores high`() {
        val message = "Your GCash account is locked. Verify: http://192.168.1.1/gcash-login"
        val flaggedHashes = emptySet<String>()
        val result = ThreatAnalyzer.analyze(message, flaggedHashes)
        assertTrue("Score should be >= 40, was ${result.suspiciousScore}", result.suspiciousScore >= 40)
    }

    @Test
    fun `safe message scores zero`() {
        val message = "Mom, I'll be home by 6pm tonight!"
        val result = ThreatAnalyzer.analyze(message, emptySet())
        assertEquals(0, result.suspiciousScore)
    }

    @Test
    fun `known flagged domain scores high`() {
        val domain = "evil-phishing.com"
        val hash = HashUtil.sha256(domain)
        val message = "Click here https://evil-phishing.com/verify"
        val result = ThreatAnalyzer.analyze(message, setOf(hash))
        assertTrue(result.suspiciousScore >= 60)
    }

    @Test
    fun `ip url raises score`() {
        val message = "Click http://45.33.32.156/win-prize"
        val result = ThreatAnalyzer.analyze(message, emptySet())
        assertTrue(result.suspiciousScore >= 40)
    }

    @Test
    fun `multiple links raise score`() {
        val message = "Check bit.ly/1 and bit.ly/2 and tinyurl.com/xyz"
        val result = ThreatAnalyzer.analyze(message, emptySet())
        assertTrue(result.suspiciousScore > 0)
    }
}
