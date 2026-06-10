package com.secureguardian.app.presentation.inbox

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secureguardian.app.domain.model.SmsMessage
import com.secureguardian.app.domain.model.ThreatLevel
import com.secureguardian.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onMessageClick: (String) -> Unit,
    viewModel: InboxViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top bar
        TopAppBar(
            title = {
                Column {
                    Text("Inbox", style = MaterialTheme.typography.headlineSmall)
                    if (uiState.unreadCount > 0) {
                        Text(
                            "${uiState.unreadCount} unread",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            actions = {
                IconButton(onClick = { /* search */ }) {
                    Icon(Icons.Default.Search, "Search")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        // Filter tabs
        FilterTabs(
            selectedFilter = uiState.selectedFilter,
            onFilterChange = viewModel::setFilter
        )

        Spacer(Modifier.height(4.dp))

        if (uiState.isLoading && uiState.messages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.messages.isEmpty()) {
            EmptyInboxPlaceholder(filter = uiState.selectedFilter)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(
                    items = uiState.messages,
                    key = { it.id }
                ) { message ->
                    MessageCard(
                        message = message,
                        onClick = {
                            viewModel.markRead(message.id)
                            onMessageClick(message.id)
                        },
                        onFlagClick = { viewModel.flagMessage(message) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterTabs(
    selectedFilter: InboxFilter,
    onFilterChange: (InboxFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InboxFilter.values().forEach { filter ->
            val isSelected = selectedFilter == filter
            FilterChip(
                selected = isSelected,
                onClick = { onFilterChange(filter) },
                label = {
                    Text(
                        when (filter) {
                            InboxFilter.ALL -> "All"
                            InboxFilter.SAFE -> "Safe"
                            InboxFilter.FLAGGED -> "Flagged"
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            when (filter) {
                                InboxFilter.ALL -> Icons.Default.AllInbox
                                InboxFilter.SAFE -> Icons.Default.CheckCircle
                                InboxFilter.FLAGGED -> Icons.Default.Warning
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = when (filter) {
                        InboxFilter.FLAGGED -> AccentRed.copy(alpha = 0.15f)
                        InboxFilter.SAFE -> SafeGreen.copy(alpha = 0.15f)
                        InboxFilter.ALL -> MaterialTheme.colorScheme.primaryContainer
                    },
                    selectedLabelColor = when (filter) {
                        InboxFilter.FLAGGED -> AccentRed
                        InboxFilter.SAFE -> SafeGreen
                        InboxFilter.ALL -> MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            )
        }
    }
}

@Composable
private fun MessageCard(
    message: SmsMessage,
    onClick: () -> Unit,
    onFlagClick: () -> Unit
) {
    val (threatColor, threatLabel) = when (message.threatLevel) {
        ThreatLevel.RED_FLAG -> AccentRed to "RED FLAG"
        ThreatLevel.SUSPICIOUS -> SuspiciousOrange to "SUSPICIOUS"
        ThreatLevel.SAFE -> SafeGreen to "SAFE"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (message.isRead)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Unread indicator + avatar
            Box {
                ContactAvatar(
                    name = message.senderDisplayName ?: message.sender,
                    isKnownContact = message.isKnownContact,
                    threatLevel = message.threatLevel
                )
                if (!message.isRead) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .align(Alignment.TopEnd)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.senderDisplayName ?: message.sender,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (!message.isRead) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        color = if (!message.isKnownContact) SuspiciousOrange
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = formatTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!message.isKnownContact) {
                    Text(
                        text = "Not in your contacts",
                        style = MaterialTheme.typography.labelSmall,
                        color = SuspiciousOrange
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (message.extractedLinks.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "🔗 ${message.extractedLinks.first()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Threat badge
                    Box(
                        modifier = Modifier
                            .background(threatColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(threatColor, CircleShape)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = threatLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = threatColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Red flag button (only for non-flagged)
                    if (message.threatLevel != ThreatLevel.RED_FLAG) {
                        TextButton(
                            onClick = onFlagClick,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Flag,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = AccentRed
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Flag",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentRed
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactAvatar(
    name: String,
    isKnownContact: Boolean,
    threatLevel: ThreatLevel
) {
    val initials = if (name.isNotBlank()) {
        name.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
    } else "?"

    val bgColor = when {
        !isKnownContact -> SuspiciousOrange.copy(alpha = 0.15f)
        threatLevel == ThreatLevel.RED_FLAG -> AccentRed.copy(alpha = 0.15f)
        else -> DeepNavy.copy(alpha = 0.1f)
    }
    val textColor = when {
        !isKnownContact -> SuspiciousOrange
        threatLevel == ThreatLevel.RED_FLAG -> AccentRed
        else -> DeepNavy
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .background(bgColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials.ifEmpty { "?" },
            style = MaterialTheme.typography.titleSmall,
            color = textColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun EmptyInboxPlaceholder(filter: InboxFilter) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = when (filter) {
                InboxFilter.FLAGGED -> Icons.Default.CheckCircle
                InboxFilter.SAFE -> Icons.Default.MarkEmailRead
                InboxFilter.ALL -> Icons.Default.Inbox
            },
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            when (filter) {
                InboxFilter.FLAGGED -> "No flagged messages"
                InboxFilter.SAFE -> "No safe messages yet"
                InboxFilter.ALL -> "No messages yet"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Messages will appear here as they arrive.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "Now"
        diff < 3_600_000 -> "${diff / 60_000}m"
        diff < 86_400_000 -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
        diff < 604_800_000 -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(timestamp))
    }
}
