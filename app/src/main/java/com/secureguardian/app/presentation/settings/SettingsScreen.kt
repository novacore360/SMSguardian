package com.secureguardian.app.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secureguardian.app.data.repository.AppRepository
import com.secureguardian.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// DataStore is defined once in data/local/DataStoreExt.kt — import it from there.
import com.secureguardian.app.data.local.dataStore

object PrefKeys {
    val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
    val AUTO_BLOCK_RED_FLAG = booleanPreferencesKey("auto_block_red_flag")
    val NOTIFY_SUSPICIOUS = booleanPreferencesKey("notify_suspicious")
    val NOTIFY_SAFE = booleanPreferencesKey("notify_safe")
    val SHARE_TO_COMMUNITY = booleanPreferencesKey("share_to_community")
    val CLIPBOARD_MONITORING = booleanPreferencesKey("clipboard_monitoring")
    val WEEKLY_REPORT = booleanPreferencesKey("weekly_report")
    val DARK_MODE = booleanPreferencesKey("dark_mode")
}

data class SettingsState(
    val biometricEnabled: Boolean = false,
    val autoBlockRedFlag: Boolean = true,
    val notifySuspicious: Boolean = true,
    val notifySafe: Boolean = false,
    val shareToCommunity: Boolean = true,
    val clipboardMonitoring: Boolean = false,
    val weeklyReport: Boolean = true,
    val userEmail: String = "",
    val totalReports: Int = 0,
    val totalBlocked: Int = 0,
    val isSigningOut: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: AppRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            context.dataStore.data.collect { prefs ->
                _state.update {
                    it.copy(
                        biometricEnabled = prefs[PrefKeys.BIOMETRIC_ENABLED] ?: false,
                        autoBlockRedFlag = prefs[PrefKeys.AUTO_BLOCK_RED_FLAG] ?: true,
                        notifySuspicious = prefs[PrefKeys.NOTIFY_SUSPICIOUS] ?: true,
                        notifySafe = prefs[PrefKeys.NOTIFY_SAFE] ?: false,
                        shareToCommunity = prefs[PrefKeys.SHARE_TO_COMMUNITY] ?: true,
                        clipboardMonitoring = prefs[PrefKeys.CLIPBOARD_MONITORING] ?: false,
                        weeklyReport = prefs[PrefKeys.WEEKLY_REPORT] ?: true
                    )
                }
            }
        }
        loadUserInfo()
    }

    private fun loadUserInfo() {
        val user = repository.getCurrentUser()
        _state.update { it.copy(userEmail = user?.email ?: "") }
    }

    fun toggle(key: Preferences.Key<Boolean>, value: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[key] = value }
        }
    }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isSigningOut = true) }
            repository.signOut()
            _state.update { it.copy(isSigningOut = false) }
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onSignOut: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSignOutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Text("Settings") },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Account section
            SettingsSection("Account") {
                AccountCard(email = state.userEmail, onSignOut = { showSignOutDialog = true })
            }

            // Protection settings
            SettingsSection("Protection") {
                SettingsSwitchRow(
                    icon = Icons.Default.Block,
                    iconTint = AccentRed,
                    title = "Auto-block RED FLAG messages",
                    subtitle = "Automatically quarantine confirmed threats",
                    checked = state.autoBlockRedFlag,
                    onCheckedChange = { viewModel.toggle(PrefKeys.AUTO_BLOCK_RED_FLAG, it) }
                )
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Default.ContentPaste,
                    iconTint = SuspiciousOrange,
                    title = "Clipboard Monitoring",
                    subtitle = "Check copied URLs against threat database",
                    checked = state.clipboardMonitoring,
                    onCheckedChange = { viewModel.toggle(PrefKeys.CLIPBOARD_MONITORING, it) }
                )
            }

            // Notifications
            SettingsSection("Notifications") {
                SettingsSwitchRow(
                    icon = Icons.Default.Warning,
                    iconTint = SuspiciousOrange,
                    title = "Suspicious messages",
                    subtitle = "Notify when suspicious messages arrive",
                    checked = state.notifySuspicious,
                    onCheckedChange = { viewModel.toggle(PrefKeys.NOTIFY_SUSPICIOUS, it) }
                )
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Default.BarChart,
                    iconTint = SafeGreen,
                    title = "Weekly security report",
                    subtitle = "Summary of blocked threats every week",
                    checked = state.weeklyReport,
                    onCheckedChange = { viewModel.toggle(PrefKeys.WEEKLY_REPORT, it) }
                )
            }

            // Community
            SettingsSection("Community") {
                SettingsSwitchRow(
                    icon = Icons.Default.People,
                    iconTint = DeepNavy,
                    title = "Share to community database",
                    subtitle = "Help protect others with your flagged domains",
                    checked = state.shareToCommunity,
                    onCheckedChange = { viewModel.toggle(PrefKeys.SHARE_TO_COMMUNITY, it) }
                )
            }

            // Security
            SettingsSection("Security") {
                SettingsSwitchRow(
                    icon = Icons.Default.Fingerprint,
                    iconTint = DeepNavy,
                    title = "Biometric Lock",
                    subtitle = "Require fingerprint or face to open app",
                    checked = state.biometricEnabled,
                    onCheckedChange = { viewModel.toggle(PrefKeys.BIOMETRIC_ENABLED, it) }
                )
            }

            // About
            SettingsSection("About") {
                SettingsClickRow(
                    icon = Icons.Default.PrivacyTip,
                    title = "Privacy Policy",
                    subtitle = "How we handle your data",
                    onClick = onPrivacyPolicy
                )
                SettingsDivider()
                SettingsClickRow(
                    icon = Icons.Default.Info,
                    title = "App Version",
                    subtitle = "1.0.0",
                    onClick = {}
                )
                SettingsDivider()
                SettingsClickRow(
                    icon = Icons.Default.BugReport,
                    title = "Report a Bug",
                    subtitle = "Help us improve SecureSMS Guardian",
                    onClick = {}
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign Out") },
            text = { Text("Are you sure you want to sign out?") },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutDialog = false
                        viewModel.signOut(onSignOut)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) {
                    if (state.isSigningOut) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = PureWhite, strokeWidth = 2.dp)
                    } else {
                        Text("Sign Out")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp, top = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun AccountCard(email: String, onSignOut: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(DeepNavy.copy(alpha = 0.1f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, null, tint = DeepNavy)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(email.ifEmpty { "Not signed in" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("SecureSMS Guardian Account", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onSignOut) {
            Text("Sign Out", color = AccentRed)
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(iconTint.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsClickRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 64.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    )
}
