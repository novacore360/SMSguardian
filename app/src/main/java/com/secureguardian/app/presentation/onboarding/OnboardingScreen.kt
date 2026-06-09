package com.secureguardian.app.presentation.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.secureguardian.app.ui.theme.*

data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val permission: String? = null,
    val permissionRationale: String? = null
)

val onboardingPages = listOf(
    OnboardingPage(
        icon = Icons.Default.Security,
        title = "Welcome to\nSecureSMS Guardian",
        description = "Real-time protection against SMS phishing, fraud, and malicious links. Your digital safety companion."
    ),
    OnboardingPage(
        icon = Icons.Default.Sms,
        title = "SMS Monitoring",
        description = "We analyze incoming messages for suspicious links and patterns. Messages are stored locally and synced securely.",
        permission = Manifest.permission.RECEIVE_SMS,
        permissionRationale = "Required to analyze incoming SMS messages for threats."
    ),
    OnboardingPage(
        icon = Icons.Default.Contacts,
        title = "Contact Recognition",
        description = "We identify known contacts to help distinguish trusted senders from potential fraudsters.",
        permission = Manifest.permission.READ_CONTACTS,
        permissionRationale = "Required to show contact names and identify unknown senders."
    ),
    OnboardingPage(
        icon = Icons.Default.Notifications,
        title = "Instant Alerts",
        description = "Get notified immediately when a suspicious or dangerous message arrives.",
        permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.POST_NOTIFICATIONS else null,
        permissionRationale = "Required to send threat alerts and security notifications."
    ),
    OnboardingPage(
        icon = Icons.Default.PrivacyTip,
        title = "Your Privacy Matters",
        description = "All data is stored securely. Messages are kept for 24 hours then auto-deleted. Contacts are stored permanently to identify senders. You control what's shared."
    )
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var currentPage by remember { mutableStateOf(0) }
    val page = onboardingPages[currentPage]

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* proceed regardless of grant status */ }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            // Page indicator
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onboardingPages.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .width(if (i == currentPage) 24.dp else 8.dp)
                            .background(
                                color = if (i == currentPage) AccentRed
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(Modifier.height(48.dp))

            // Icon
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "onboarding_icon"
            ) { pageIdx ->
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(
                            color = when (pageIdx) {
                                0 -> DeepNavy
                                1 -> AccentRed.copy(alpha = 0.1f)
                                2 -> SuspiciousOrange.copy(alpha = 0.1f)
                                3 -> SafeGreen.copy(alpha = 0.1f)
                                else -> DeepNavy.copy(alpha = 0.08f)
                            },
                            shape = RoundedCornerShape(28.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = onboardingPages[pageIdx].icon,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = when (pageIdx) {
                            0 -> AccentRed
                            1 -> AccentRed
                            2 -> SuspiciousOrange
                            3 -> SafeGreen
                            else -> DeepNavy
                        }
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    slideInHorizontally { it / 4 } + fadeIn() togetherWith
                            slideOutHorizontally { -it / 4 } + fadeOut()
                },
                label = "onboarding_content"
            ) { pageIdx ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = onboardingPages[pageIdx].title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = onboardingPages[pageIdx].description,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    // Permission rationale
                    onboardingPages[pageIdx].permissionRationale?.let { rationale ->
                        Spacer(Modifier.height(20.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    rationale,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Buttons
            val isLast = currentPage == onboardingPages.lastIndex

            if (!isLast) {
                // Grant permission if this page has one
                page.permission?.let { perm ->
                    OutlinedButton(
                        onClick = {
                            val permsToRequest = mutableListOf(perm)
                            // Also request READ_SMS alongside RECEIVE_SMS
                            if (perm == Manifest.permission.RECEIVE_SMS) {
                                permsToRequest.add(Manifest.permission.READ_SMS)
                            }
                            permissionLauncher.launch(permsToRequest.toTypedArray())
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Grant Permission")
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Button(
                    onClick = { currentPage++ },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepNavy),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Continue", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(18.dp))
                }

                TextButton(onClick = onComplete) {
                    Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Button(
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Shield, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Start Protecting My Phone", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
