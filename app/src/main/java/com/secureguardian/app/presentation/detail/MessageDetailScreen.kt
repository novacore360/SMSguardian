package com.secureguardian.app.presentation.detail

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguardian.app.data.repository.AppRepository
import com.secureguardian.app.domain.model.SmsMessage
import com.secureguardian.app.domain.model.ThreatLevel
import com.secureguardian.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: AppRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val messageId: String = checkNotNull(savedStateHandle["messageId"])

    private val _message = MutableStateFlow<SmsMessage?>(null)
    val message: StateFlow<SmsMessage?> = _message.asStateFlow()

    private val _isFlagging = MutableStateFlow(false)
    val isFlagging: StateFlow<Boolean> = _isFlagging.asStateFlow()

    init {
        viewModelScope.launch {
            // Use getAllMessages() so blocked/quarantined messages are also accessible from detail view.
            // getInboxMessages() filters out isBlocked=true, which would leave this screen
            // spinning forever when navigating to a quarantined message.
            repository.getAllMessages()
                .map { messages -> messages.find { it.id == messageId } }
                .collect { _message.value = it }
        }
    }

    fun flagMessage() {
        val msg = _message.value ?: return
        viewModelScope.launch {
            _isFlagging.value = true
            repository.flagMessage(msg)
            _isFlagging.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    onBack: () -> Unit,
    onReport: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val message by viewModel.message.collectAsStateWithLifecycle()
    val isFlagging by viewModel.isFlagging.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Message Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        message?.body?.let {
                            clipboardManager.setText(AnnotatedString(it))
                        }
                    }) {
                        Icon(Icons.Default.ContentCopy, "Copy message")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        message?.let { msg ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ThreatBanner(msg.threatLevel)
                SenderInfoCard(msg)
                MessageBodyCard(msg.body, msg.extractedLinks)

                if (msg.extractedDomains.isNotEmpty()) {
                    ExtractedDomainsCard(
                        domains = msg.extractedDomains,
                        links = msg.extractedLinks,
                        threatLevel = msg.threatLevel,
                        onCopyLink = { link ->
                            clipboardManager.setText(AnnotatedString(link))
                        }
                    )
                }

                ActionButtons(
                    message = msg,
                    isFlagging = isFlagging,
                    onFlagAsPhishing = viewModel::flagMessage,
                    onFlagAsFraud = viewModel::flagMessage,
                    onReport = onReport,
                    onMarkSafe = { /* override to safe */ }
                )

                Spacer(Modifier.height(24.dp))
            }
        } ?: Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun ThreatBanner(level: ThreatLevel) {
    val (color, icon, title, subtitle) = when (level) {
        ThreatLevel.RED_FLAG -> quadruple(
            AccentRed, Icons.Default.GppBad,
            "RED FLAG — Phishing/Fraud Detected",
            "This message has been flagged as dangerous. Do not click any links."
        )
        ThreatLevel.SUSPICIOUS -> quadruple(
            SuspiciousOrange, Icons.Default.Warning,
            "SUSPICIOUS Message",
            "This message shows suspicious patterns. Exercise caution."
        )
        ThreatLevel.SAFE -> quadruple(
            SafeGreen, Icons.Default.VerifiedUser,
            "SAFE Message",
            "No threats detected in this message."
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun SenderInfoCard(message: SmsMessage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sender Information", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            HorizontalDivider()
            InfoRow("Phone", message.sender)
            if (message.senderDisplayName != null) {
                InfoRow("Name", message.senderDisplayName)
            }
            InfoRow(
                "Contact Status",
                if (message.isKnownContact) "✓ In your contacts" else "✗ Not in your contacts",
                valueColor = if (message.isKnownContact) SafeGreen else SuspiciousOrange
            )
            InfoRow(
                "Received",
                SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(message.timestamp))
            )
        }
    }
}

@Composable
private fun MessageBodyCard(body: String, links: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Message Content", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            val annotated = buildAnnotatedString {
                var lastEnd = 0
                links.sortedBy { body.indexOf(it) }.forEach { link ->
                    val start = body.indexOf(link, lastEnd)
                    if (start >= lastEnd) {
                        append(body.substring(lastEnd, start))
                        withStyle(SpanStyle(color = AccentRed, fontWeight = FontWeight.SemiBold)) {
                            append(link)
                        }
                        lastEnd = start + link.length
                    }
                }
                if (lastEnd < body.length) append(body.substring(lastEnd))
            }
            Text(
                text = annotated,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Default
            )
        }
    }
}

@Composable
private fun ExtractedDomainsCard(
    domains: List<String>,
    links: List<String>,
    threatLevel: ThreatLevel,
    onCopyLink: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Link, null, tint = AccentRed, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Extracted Links (${links.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            HorizontalDivider()
            links.forEach { link ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (threatLevel == ThreatLevel.RED_FLAG) AccentRed.copy(alpha = 0.05f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = link,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = AccentRed,
                        modifier = Modifier.weight(1f),
                        maxLines = 2
                    )
                    IconButton(
                        onClick = { onCopyLink(link) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, "Copy link", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButtons(
    message: SmsMessage,
    isFlagging: Boolean,
    onFlagAsPhishing: () -> Unit,
    onFlagAsFraud: () -> Unit,
    onReport: () -> Unit,
    onMarkSafe: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (message.threatLevel != ThreatLevel.RED_FLAG) {
            Button(
                onClick = onFlagAsPhishing,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                shape = RoundedCornerShape(12.dp),
                enabled = !isFlagging
            ) {
                if (isFlagging) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = PureWhite, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.ReportProblem, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Report as Phishing")
                }
            }

            OutlinedButton(
                onClick = onFlagAsFraud,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AccentRed)
            ) {
                Icon(Icons.Default.MoneyOff, null, modifier = Modifier.size(18.dp), tint = AccentRed)
                Spacer(Modifier.width(8.dp))
                Text("Report as Fraud", color = AccentRed)
            }
        }

        OutlinedButton(
            onClick = onReport,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Flag, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Submit Detailed Report")
        }

        if (message.threatLevel != ThreatLevel.SAFE) {
            TextButton(
                onClick = onMarkSafe,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = SafeGreen, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("This message is safe (override)", color = SafeGreen)
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            fontWeight = FontWeight.Medium
        )
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
fun <A, B, C, D> quadruple(a: A, b: B, c: C, d: D) = Quadruple(a, b, c, d)
operator fun <A, B, C, D> Quadruple<A, B, C, D>.component1() = first
operator fun <A, B, C, D> Quadruple<A, B, C, D>.component2() = second
operator fun <A, B, C, D> Quadruple<A, B, C, D>.component3() = third
operator fun <A, B, C, D> Quadruple<A, B, C, D>.component4() = fourth
