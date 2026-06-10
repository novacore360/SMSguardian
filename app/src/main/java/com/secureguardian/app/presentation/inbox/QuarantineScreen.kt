package com.secureguardian.app.presentation.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secureguardian.app.data.local.entities.BlockedMessageEntity
import com.secureguardian.app.data.repository.AppRepository
import com.secureguardian.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class QuarantineViewModel @Inject constructor(
    private val repository: AppRepository
) : ViewModel() {

    val blockedMessages: StateFlow<List<BlockedMessageEntity>> =
        repository.getBlockedMessages()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun overrideBlock(id: Long) {
        viewModelScope.launch { repository.overrideBlock(id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuarantineScreen(
    onBack: () -> Unit,
    viewModel: QuarantineViewModel = hiltViewModel()
) {
    val messages by viewModel.blockedMessages.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Quarantine")
                        Text(
                            "${messages.size} blocked messages",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Shield,
                        null,
                        modifier = Modifier.size(64.dp),
                        tint = SafeGreen.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Quarantine is empty", style = MaterialTheme.typography.titleMedium)
                    Text("No blocked messages", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AccentRed.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, null, tint = AccentRed, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "These messages were automatically blocked due to detected threats. Review carefully before releasing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AccentRed
                            )
                        }
                    }
                }
                items(messages, key = { it.id }) { message ->
                    BlockedMessageCard(message = message, onRelease = { viewModel.overrideBlock(message.id) })
                }
            }
        }
    }
}

@Composable
private fun BlockedMessageCard(
    message: BlockedMessageEntity,
    onRelease: () -> Unit
) {
    var showBody by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (message.isOverridden)
                MaterialTheme.colorScheme.surface
            else AccentRed.copy(alpha = 0.04f)
        ),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Block,
                        null,
                        tint = if (message.isOverridden) SafeGreen else AccentRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        message.sender,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(message.blockedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                message.blockReason,
                style = MaterialTheme.typography.bodySmall,
                color = AccentRed
            )

            if (showBody) {
                Text(
                    message.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    message.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = { showBody = !showBody },
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    Text(
                        if (showBody) "Show less" else "Show full message",
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                if (!message.isOverridden) {
                    OutlinedButton(
                        onClick = onRelease,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SafeGreen)
                    ) {
                        Text("Release", style = MaterialTheme.typography.labelSmall, color = SafeGreen)
                    }
                } else {
                    Text(
                        "Released",
                        style = MaterialTheme.typography.labelSmall,
                        color = SafeGreen
                    )
                }
            }
        }
    }
}
