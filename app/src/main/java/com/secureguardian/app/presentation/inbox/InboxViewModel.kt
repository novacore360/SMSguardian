package com.secureguardian.app.presentation.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguardian.app.data.repository.AppRepository
import com.secureguardian.app.domain.model.SmsMessage
import com.secureguardian.app.domain.model.ThreatLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class InboxFilter { ALL, SAFE, FLAGGED }

data class InboxUiState(
    val messages: List<SmsMessage> = emptyList(),
    val isLoading: Boolean = false,
    val selectedFilter: InboxFilter = InboxFilter.ALL,
    val unreadCount: Int = 0,
    val error: String? = null
)

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val repository: AppRepository
) : ViewModel() {

    private val _filter = MutableStateFlow(InboxFilter.ALL)
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<InboxUiState> = combine(
        _filter,
        _filter.flatMapLatest { filter ->
            when (filter) {
                InboxFilter.ALL -> repository.getInboxMessages()
                InboxFilter.SAFE -> repository.getSafeMessages()
                InboxFilter.FLAGGED -> repository.getFlaggedMessages()
            }
        },
        repository.getUnreadCount(),
        _isLoading,
        _error
    ) { filter, messages, unreadCount, isLoading, error ->
        InboxUiState(
            messages = messages,
            isLoading = isLoading,
            selectedFilter = filter,
            unreadCount = unreadCount,
            error = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InboxUiState(isLoading = true)
    )

    fun setFilter(filter: InboxFilter) {
        _filter.value = filter
    }

    fun markRead(id: String) {
        viewModelScope.launch {
            repository.markMessageRead(id)
        }
    }

    fun flagMessage(message: SmsMessage) {
        viewModelScope.launch {
            try {
                repository.flagMessage(message)
            } catch (e: Exception) {
                _error.value = "Failed to flag message: ${e.message}"
            }
        }
    }

    fun clearError() { _error.value = null }
}
