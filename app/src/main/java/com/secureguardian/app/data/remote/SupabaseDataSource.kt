package com.secureguardian.app.data.remote

import com.secureguardian.app.BuildConfig
import com.secureguardian.app.domain.model.ThreatLevel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.Realtime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

// ─── Supabase row models ───────────────────────────────────────────────────

@Serializable
data class SupabaseMessage(
    val id: String,
    val user_id: String,
    val sender: String,
    val sender_display_name: String? = null,
    val is_known_contact: Boolean = false,
    val message_raw: String,
    val extracted_links: String = "[]",
    val extracted_domains: String = "[]",
    val threat_level: String = "SAFE",
    val is_blocked: Boolean = false,
    val received_at: String,
    val expires_at: String? = null
)

@Serializable
data class SupabaseFlaggedDomain(
    val id: String? = null,
    val reporter_user_id: String,
    val domain: String,
    val domain_hash: String,
    val threat_level: String,
    val report_count: Int = 1,
    val reason: String? = null,
    val is_personal: Boolean = true,
    val last_reported: String
)

@Serializable
data class SupabaseThreatReport(
    val id: String? = null,
    val reporter_user_id: String,
    val report_type: String,
    val content: String,
    val reason: String,
    val notes: String? = null,
    val is_anonymous: Boolean = true,
    val extracted_entities: String = "[]",
    val created_at: String? = null
)

@Serializable
data class SupabaseContact(
    val id: String,
    val user_id: String,
    val name: String,
    val phone_number: String,
    val phone_hash: String
)

// ─── Supabase Data Source ─────────────────────────────────────────────────

@Singleton
class SupabaseDataSource @Inject constructor(
    val client: SupabaseClient
) {

    // ── Auth ──────────────────────────────────────────────────────────────

    suspend fun signUp(email: String, password: String) =
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }

    suspend fun signIn(email: String, password: String) =
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }

    suspend fun signOut() = client.auth.signOut()

    suspend fun resetPassword(email: String) =
        client.auth.resetPasswordForEmail(email)

    fun getCurrentUser() = client.auth.currentUserOrNull()

    fun getSessionFlow() = client.auth.sessionStatus

    // ── Messages ──────────────────────────────────────────────────────────

    suspend fun insertMessage(message: SupabaseMessage) {
        client.postgrest["temporary_messages"].insert(message)
    }

    suspend fun getMessages(userId: String): List<SupabaseMessage> =
        client.postgrest["temporary_messages"]
            .select(Columns.ALL) {
                filter { eq("user_id", userId) }
            }
            .decodeList()

    suspend fun deleteExpiredMessages() {
        client.postgrest["temporary_messages"].delete {
            filter {
                lt("expires_at", "now()")
            }
        }
    }

    // ── Flagged Domains ───────────────────────────────────────────────────

    suspend fun insertFlaggedDomain(domain: SupabaseFlaggedDomain): SupabaseFlaggedDomain =
        client.postgrest["flagged_domains"]
            .insert(domain) { select() }
            .decodeSingle()

    suspend fun getFlaggedDomains(userId: String): List<SupabaseFlaggedDomain> =
        client.postgrest["flagged_domains"]
            .select(Columns.ALL) {
                filter { eq("reporter_user_id", userId) }
            }
            .decodeList()

    suspend fun getCommunityFlaggedDomains(): List<SupabaseFlaggedDomain> =
        client.postgrest["flagged_domains"]
            .select(Columns.ALL) {
                filter { eq("is_personal", false) }
                order("report_count", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                limit(500)
            }
            .decodeList()

    suspend fun checkDomainHash(hash: String): SupabaseFlaggedDomain? =
        client.postgrest["flagged_domains"]
            .select(Columns.ALL) {
                filter { eq("domain_hash", hash) }
                limit(1)
            }
            .decodeList<SupabaseFlaggedDomain>()
            .firstOrNull()

    suspend fun incrementDomainReportCount(hash: String) {
        client.postgrest.rpc(
            "increment_domain_report",
            buildJsonObject { put("p_hash", hash) }
        )
    }

    // ── Reports ───────────────────────────────────────────────────────────

    suspend fun submitReport(report: SupabaseThreatReport): SupabaseThreatReport =
        client.postgrest["threat_reports"]
            .insert(report) { select() }
            .decodeSingle()

    // ── Contacts ──────────────────────────────────────────────────────────

    suspend fun upsertContacts(contacts: List<SupabaseContact>) {
        if (contacts.isEmpty()) return
        client.postgrest["user_contacts"]
            .upsert(contacts) {
                onConflict = "id"
            }
    }

    suspend fun getContacts(userId: String): List<SupabaseContact> =
        client.postgrest["user_contacts"]
            .select(Columns.ALL) {
                filter { eq("user_id", userId) }
            }
            .decodeList()

    // ── Statistics ────────────────────────────────────────────────────────

    suspend fun getThreatIntelStats(): JsonObject =
        client.postgrest.rpc("get_threat_intel_stats").decodeAs()
}
