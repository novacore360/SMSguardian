package com.secureguardian.app.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "SecureSMS Guardian Privacy Policy",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Last updated: June 2025",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PolicySection("1. Data We Collect") {
                """
                • SMS Messages: We read incoming SMS messages to analyze them for threats. Message content is stored locally on your device and temporarily synced to our secure servers for 24 hours, then automatically deleted.
                
                • Contacts: We access your contacts to identify known senders. Contact names and phone numbers are stored permanently to provide accurate sender identification.
                
                • URLs and Domains: We extract and analyze links found in messages. Domain hashes (SHA-256) are stored in our community database to protect all users.
                
                • Account Information: Email address used for authentication via Supabase.
                """.trimIndent()
            }

            PolicySection("2. How We Use Your Data") {
                """
                • Threat Analysis: Message content is analyzed locally using pattern matching and compared against our threat database.
                
                • Community Protection: Reported malicious domains are (with your consent) added to a shared database to protect other users.
                
                • Service Improvement: Anonymized threat statistics help us improve detection accuracy.
                """.trimIndent()
            }

            PolicySection("3. Data Retention") {
                """
                • Messages: Automatically deleted after 24 hours from our servers. Local copies remain until you clear app data.
                
                • Contacts: Stored permanently for sender identification. You can delete via Settings.
                
                • Flagged Domains: Retained permanently in our community database to protect users.
                
                • Account Data: Retained until you delete your account.
                """.trimIndent()
            }

            PolicySection("4. Data Security") {
                """
                • All data transmitted to our servers uses TLS 1.3 encryption.
                
                • Supabase Row Level Security (RLS) ensures your data is isolated from other users.
                
                • Phone numbers and sensitive identifiers are hashed using SHA-256 before any community sharing.
                
                • We use Supabase's built-in security infrastructure hosted in secure data centers.
                """.trimIndent()
            }

            PolicySection("5. Community Database Sharing") {
                """
                • When you report a domain as malicious, only the SHA-256 hash of the domain (not the plain text) is shared with our community database by default.
                
                • You can disable community sharing in Settings > Community.
                
                • Anonymous reporting is available — your user ID is not linked to reports when this option is enabled.
                """.trimIndent()
            }

            PolicySection("6. Third-Party Services") {
                """
                • Supabase: Our database and authentication provider. Data processed under Supabase's privacy policy (supabase.com/privacy).
                
                • We do not sell, rent, or share your personal data with advertisers or other third parties.
                """.trimIndent()
            }

            PolicySection("7. Your Rights") {
                """
                • Access: View all data stored about you via the app.
                
                • Deletion: Delete your account and all associated data from Settings > Account.
                
                • Opt-out: Disable community sharing at any time.
                
                • Export: Request a copy of your data by contacting support.
                """.trimIndent()
            }

            PolicySection("8. Children's Privacy") {
                """
                SecureSMS Guardian is not directed at children under 13. We do not knowingly collect data from children under 13.
                """.trimIndent()
            }

            PolicySection("9. Contact Us") {
                """
                For privacy concerns or data requests, contact us at:
                privacy@secureguardian.app
                """.trimIndent()
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PolicySection(title: String, content: () -> String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            content(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider()
    }
}
