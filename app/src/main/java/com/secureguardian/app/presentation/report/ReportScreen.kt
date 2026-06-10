package com.secureguardian.app.presentation.report

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secureguardian.app.data.repository.AppRepository
import com.secureguardian.app.domain.model.ReportType
import com.secureguardian.app.domain.model.ThreatReason
import com.secureguardian.app.domain.model.ThreatReport
import com.secureguardian.app.ui.theme.*
import com.secureguardian.app.util.LinkExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReportUiState(
    val isSubmitting: Boolean = false,
    val isSubmitted: Boolean = false,
    val error: String? = null,
    val extractedEntities: List<String> = emptyList()
)

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val repository: AppRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    fun extractEntities(content: String) {
        val links = LinkExtractor.extractLinks(content)
        val domains = LinkExtractor.extractDomains(content)
        _uiState.update { it.copy(extractedEntities = (links + domains).distinct()) }
    }

    fun submitReport(
        reportType: ReportType,
        content: String,
        reason: ThreatReason,
        notes: String,
        isAnonymous: Boolean
    ) {
        if (content.isBlank()) {
            _uiState.update { it.copy(error = "Content cannot be empty") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            val report = ThreatReport(
                reportType = reportType,
                content = content,
                reason = reason,
                notes = notes.ifBlank { null },
                isAnonymous = isAnonymous,
                extractedEntities = _uiState.value.extractedEntities
            )
            repository.submitThreatReport(report)
                .onSuccess { _uiState.update { it.copy(isSubmitting = false, isSubmitted = true) } }
                .onFailure { e -> _uiState.update { it.copy(isSubmitting = false, error = e.message ?: "Submission failed") } }
        }
    }

    fun reset() { _uiState.value = ReportUiState() }
    fun clearError() { _uiState.update { it.copy(error = null) } }
}

enum class ReportTab { MESSAGE, URL, PHONE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(viewModel: ReportViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(ReportTab.MESSAGE) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Text("Report Threat") },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        if (uiState.isSubmitted) {
            SubmissionSuccessView(onReset = viewModel::reset)
        } else {
            // Tab row
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                ReportTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                when (tab) {
                                    ReportTab.MESSAGE -> "Message"
                                    ReportTab.URL -> "URL/Domain"
                                    ReportTab.PHONE -> "Phone"
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        icon = {
                            Icon(
                                when (tab) {
                                    ReportTab.MESSAGE -> Icons.Default.Sms
                                    ReportTab.URL -> Icons.Default.Link
                                    ReportTab.PHONE -> Icons.Default.Phone
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "report_tab"
            ) { tab ->
                ReportForm(
                    reportType = when (tab) {
                        ReportTab.MESSAGE -> ReportType.MESSAGE
                        ReportTab.URL -> ReportType.URL_DOMAIN
                        ReportTab.PHONE -> ReportType.PHONE_NUMBER
                    },
                    tab = tab,
                    uiState = uiState,
                    onExtract = viewModel::extractEntities,
                    onSubmit = { content, reason, notes, anon ->
                        viewModel.submitReport(
                            when (tab) {
                                ReportTab.MESSAGE -> ReportType.MESSAGE
                                ReportTab.URL -> ReportType.URL_DOMAIN
                                ReportTab.PHONE -> ReportType.PHONE_NUMBER
                            },
                            content, reason, notes, anon
                        )
                    }
                )
            }
        }
    }

    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // show snackbar via scaffold if needed
        }
    }
}

@Composable
private fun ReportForm(
    reportType: ReportType,
    tab: ReportTab,
    uiState: ReportUiState,
    onExtract: (String) -> Unit,
    onSubmit: (String, ThreatReason, String, Boolean) -> Unit
) {
    var content by remember(tab) { mutableStateOf("") }
    var selectedReason by remember { mutableStateOf(ThreatReason.PHISHING) }
    var notes by remember { mutableStateOf("") }
    var isAnonymous by remember { mutableStateOf(true) }
    var showPreview by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Content input
        val (label, placeholder, lines) = when (tab) {
            ReportTab.MESSAGE -> Triple(
                "Paste SMS Message",
                "Paste the full suspicious message here…",
                6
            )
            ReportTab.URL -> Triple(
                "URL or Domain",
                "https://suspicious-site.com or domain.com",
                3
            )
            ReportTab.PHONE -> Triple(
                "Phone Number",
                "+63 9XX XXX XXXX",
                2
            )
        }

        OutlinedTextField(
            value = content,
            onValueChange = {
                content = it
                if (tab == ReportTab.MESSAGE && it.length > 10) onExtract(it)
            },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            modifier = Modifier.fillMaxWidth(),
            minLines = lines,
            maxLines = lines + 2,
            shape = RoundedCornerShape(12.dp)
        )

        // Auto-extracted preview
        if (uiState.extractedEntities.isNotEmpty() && tab == ReportTab.MESSAGE) {
            Card(
                colors = CardDefaults.cardColors(containerColor = AccentRed.copy(alpha = 0.06f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Extracted entities (${uiState.extractedEntities.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentRed
                    )
                    uiState.extractedEntities.forEach { entity ->
                        Text(
                            "• $entity",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentRed.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Reason selector
        Text("Reason", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        ReasonSelector(selected = selectedReason, onSelect = { selectedReason = it })

        // Notes
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Additional Notes (optional)") },
            placeholder = { Text("Describe the threat in more detail…") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5,
            shape = RoundedCornerShape(12.dp)
        )

        // Anonymous toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Anonymous Report", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Your identity won't be linked to this report",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = isAnonymous, onCheckedChange = { isAnonymous = it })
        }

        HorizontalDivider()

        // Submit button
        Button(
            onClick = { onSubmit(content, selectedReason, notes, isAnonymous) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = content.isNotBlank() && !uiState.isSubmitting,
            colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (uiState.isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = PureWhite, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Send, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Submit Report", style = MaterialTheme.typography.labelLarge)
            }
        }

        uiState.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ReasonSelector(
    selected: ThreatReason,
    onSelect: (ThreatReason) -> Unit
) {
    val reasons = ThreatReason.values()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        reasons.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { reason ->
                    FilterChip(
                        selected = selected == reason,
                        onClick = { onSelect(reason) },
                        label = {
                            Text(
                                when (reason) {
                                    ThreatReason.PHISHING -> "Phishing"
                                    ThreatReason.FRAUD -> "Fraud"
                                    ThreatReason.SPAM -> "Spam"
                                    ThreatReason.MALWARE -> "Malware"
                                    ThreatReason.ILLEGAL_CONTENT -> "Illegal"
                                    ThreatReason.OTHER -> "Other"
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentRed.copy(alpha = 0.12f),
                            selectedLabelColor = AccentRed
                        )
                    )
                }
                // pad if odd
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SubmissionSuccessView(onReset: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(SafeGreen.copy(alpha = 0.1f), RoundedCornerShape(40.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = SafeGreen,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
        Text("Report Submitted!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Thank you for helping protect the community. Your report has been submitted and will be reviewed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onReset,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DeepNavy)
        ) {
            Text("Submit Another Report")
        }
    }
}
