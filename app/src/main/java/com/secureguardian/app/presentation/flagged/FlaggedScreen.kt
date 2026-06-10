package com.secureguardian.app.presentation.flagged

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguardian.app.data.repository.AppRepository
import com.secureguardian.app.domain.model.FlaggedDomain
import com.secureguardian.app.domain.model.ThreatLevel
import com.secureguardian.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class DomainFilter { ALL, PERSONAL, COMMUNITY }

data class FlaggedUiState(
    val domains: List<FlaggedDomain> = emptyList(),
    val selectedFilter: DomainFilter = DomainFilter.ALL,
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val checkResult: FlaggedDomain? = null,
    val isChecking: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class FlaggedViewModel @Inject constructor(
    private val repository: AppRepository
) : ViewModel() {

    private val _filter = MutableStateFlow(DomainFilter.ALL)
    private val _search = MutableStateFlow("")
    private val _checkResult = MutableStateFlow<FlaggedDomain?>(null)
    private val _isChecking = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    @OptIn(FlowPreview::class)
    val uiState: StateFlow<FlaggedUiState> = combine(
        combine(
            _filter,
            _search.debounce(300),
            _filter.flatMapLatest { filter ->
                when (filter) {
                    DomainFilter.ALL -> repository.getAllFlaggedDomains()
                    DomainFilter.PERSONAL -> repository.getPersonalFlaggedDomains()
                    DomainFilter.COMMUNITY -> repository.getCommunityFlaggedDomains()
                }
            }
        ) { filter, search, domains -> Triple(filter, search, domains) },
        _checkResult,
        _isChecking,
        _error
    ) { (filter, search, domains), checkResult, isChecking, error ->
        val filtered = if (search.isBlank()) domains
        else domains.filter {
            it.domain.contains(search, ignoreCase = true) ||
                    it.reason?.contains(search, ignoreCase = true) == true
        }
        FlaggedUiState(
            domains = filtered,
            selectedFilter = filter,
            searchQuery = search,
            checkResult = checkResult,
            isChecking = isChecking,
            error = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FlaggedUiState(isLoading = true))

    fun setFilter(f: DomainFilter) { _filter.value = f }
    fun setSearch(q: String) { _search.value = q }

    fun checkDomain(domain: String) {
        if (domain.isBlank()) return
        viewModelScope.launch {
            _isChecking.value = true
            _checkResult.value = repository.checkDomain(domain)
            _isChecking.value = false
        }
    }

    fun clearCheckResult() { _checkResult.value = null }

    fun syncCommunity() {
        viewModelScope.launch {
            repository.syncCommunityFlaggedDomains()
                .onFailure { _error.value = it.message }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlaggedScreen(viewModel: FlaggedViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var checkInput by remember { mutableStateOf("") }
    var showCheckDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Text("Flagged Database") },
            actions = {
                IconButton(onClick = viewModel::syncCommunity) {
                    Icon(Icons.Default.Sync, "Sync community data")
                }
                IconButton(onClick = { showCheckDialog = true }) {
                    Icon(Icons.Default.Search, "Check URL")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        // Search bar
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = viewModel::setSearch,
            placeholder = { Text("Search domains…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = if (uiState.searchQuery.isNotEmpty()) {
                { IconButton(onClick = { viewModel.setSearch("") }) { Icon(Icons.Default.Clear, null) } }
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DomainFilter.values().forEach { filter ->
                FilterChip(
                    selected = uiState.selectedFilter == filter,
                    onClick = { viewModel.setFilter(filter) },
                    label = {
                        Text(
                            when (filter) {
                                DomainFilter.ALL -> "All"
                                DomainFilter.PERSONAL -> "My Flags"
                                DomainFilter.COMMUNITY -> "Community"
                            },
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                )
            }
        }

        // Stats row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text(
                "${uiState.domains.size} entries",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (uiState.domains.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Shield,
                        null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("No flagged domains yet", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = viewModel::syncCommunity) {
                        Text("Sync community data")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(uiState.domains, key = { it.id }) { domain ->
                    DomainCard(domain)
                }
            }
        }
    }

    // Check URL Dialog
    if (showCheckDialog) {
        CheckUrlDialog(
            input = checkInput,
            onInputChange = { checkInput = it },
            isChecking = uiState.isChecking,
            result = uiState.checkResult,
            onCheck = { viewModel.checkDomain(checkInput) },
            onDismiss = {
                showCheckDialog = false
                checkInput = ""
                viewModel.clearCheckResult()
            }
        )
    }
}

@Composable
private fun DomainCard(domain: FlaggedDomain) {
    val (threatColor, threatLabel) = when (domain.threatLevel) {
        ThreatLevel.RED_FLAG -> AccentRed to "RED FLAG"
        ThreatLevel.SUSPICIOUS -> SuspiciousOrange to "SUSPICIOUS"
        ThreatLevel.SAFE -> SafeGreen to "SAFE"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Threat indicator
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .background(threatColor, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Language,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        domain.domain,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                domain.reason?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Reports: ${domain.reportCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(domain.lastReported)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (domain.isPersonal) {
                        Text(
                            "Personal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            // Threat badge
            Box(
                modifier = Modifier
                    .background(threatColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    threatLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = threatColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CheckUrlDialog(
    input: String,
    onInputChange: (String) -> Unit,
    isChecking: Boolean,
    result: FlaggedDomain?,
    onCheck: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Check URL / Domain") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    placeholder = { Text("Enter URL or domain…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                if (isChecking) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Checking…", style = MaterialTheme.typography.bodySmall)
                    }
                }

                result?.let {
                    val color = when (it.threatLevel) {
                        ThreatLevel.RED_FLAG -> AccentRed
                        ThreatLevel.SUSPICIOUS -> SuspiciousOrange
                        ThreatLevel.SAFE -> SafeGreen
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
                        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("⚠️ Found in flagged database!", color = color, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall)
                            Text("Threat Level: ${it.threatLevel.name}", color = color,
                                style = MaterialTheme.typography.bodySmall)
                            Text("Reports: ${it.reportCount}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } ?: if (!isChecking && input.isNotEmpty()) {
                    Text("✓ Not found in database (may still be dangerous)", style = MaterialTheme.typography.bodySmall,
                        color = SafeGreen)
                } else {
                    // no-op: still checking or no input yet
                }
            }
        },
        confirmButton = {
            Button(onClick = onCheck, enabled = input.isNotEmpty() && !isChecking) {
                Text("Check")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
