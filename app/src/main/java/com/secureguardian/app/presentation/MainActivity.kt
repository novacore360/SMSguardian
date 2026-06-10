package com.secureguardian.app.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.secureguardian.app.data.local.dataStore
import com.secureguardian.app.data.repository.AppRepository
import com.secureguardian.app.navigation.AppNavigation
import com.secureguardian.app.service.SmsMonitoringService
import com.secureguardian.app.ui.theme.SecureSMSGuardianTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private val KEY_SEEN_ONBOARDING = booleanPreferencesKey("seen_onboarding")

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: AppRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val sessionStatus = repository.getSessionFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SessionStatus.LoadingFromStorage)

    // Persisted via DataStore so onboarding only shows once.
    val hasSeenOnboarding: StateFlow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_SEEN_ONBOARDING] ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun markOnboardingComplete() {
        viewModelScope.launch {
            context.dataStore.edit { it[KEY_SEEN_ONBOARDING] = true }
        }
    }

    init {
        viewModelScope.launch {
            repository.syncCommunityFlaggedDomains()
        }
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()

        setContent {
            val sessionStatus by viewModel.sessionStatus.collectAsStateWithLifecycle()
            val hasSeenOnboarding by viewModel.hasSeenOnboarding.collectAsStateWithLifecycle()

            val isAuthenticated = sessionStatus is SessionStatus.Authenticated

            // Start the monitoring service only after SMS permission is granted.
            // This avoids a crash on Android 14+ when the service is started
            // before the user has granted RECEIVE_SMS permission.
            LaunchedEffect(isAuthenticated) {
                if (isAuthenticated && hasSmsPermission()) {
                    startMonitoringService()
                }
            }

            SecureSMSGuardianTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (sessionStatus !is SessionStatus.LoadingFromStorage) {
                        AppNavigation(
                            isAuthenticated = isAuthenticated,
                            hasSeenOnboarding = hasSeenOnboarding,
                            onOnboardingComplete = {
                                viewModel.markOnboardingComplete()
                                if (hasSmsPermission()) startMonitoringService()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
                PackageManager.PERMISSION_GRANTED

    private fun startMonitoringService() {
        startForegroundService(
            Intent(this, SmsMonitoringService::class.java).apply {
                action = SmsMonitoringService.ACTION_START_MONITORING
            }
        )
    }
}
