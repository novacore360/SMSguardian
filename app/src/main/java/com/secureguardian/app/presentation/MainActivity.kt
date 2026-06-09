package com.secureguardian.app.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.secureguardian.app.data.repository.AppRepository
import com.secureguardian.app.navigation.AppNavigation
import com.secureguardian.app.service.SmsMonitoringService
import com.secureguardian.app.ui.theme.SecureSMSGuardianTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: AppRepository
) : ViewModel() {

    val sessionStatus = repository.getSessionFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SessionStatus.LoadingFromStorage)

    // Track if user has completed onboarding (persisted via DataStore in real impl)
    private val _hasSeenOnboarding = MutableStateFlow(false)
    val hasSeenOnboarding: StateFlow<Boolean> = _hasSeenOnboarding.asStateFlow()

    init {
        viewModelScope.launch {
            // Sync community flagged domains on startup
            repository.syncCommunityFlaggedDomains()
        }
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start SMS monitoring service
        startForegroundService(
            Intent(this, SmsMonitoringService::class.java).apply {
                action = SmsMonitoringService.ACTION_START_MONITORING
            }
        )

        setContent {
            val sessionStatus by viewModel.sessionStatus.collectAsStateWithLifecycle()
            val hasSeenOnboarding by viewModel.hasSeenOnboarding.collectAsStateWithLifecycle()

            val isAuthenticated = sessionStatus is SessionStatus.Authenticated

            SecureSMSGuardianTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Only render navigation once session is determined
                    if (sessionStatus !is SessionStatus.LoadingFromStorage) {
                        AppNavigation(
                            isAuthenticated = isAuthenticated,
                            hasSeenOnboarding = hasSeenOnboarding
                        )
                    }
                }
            }
        }
    }
}
