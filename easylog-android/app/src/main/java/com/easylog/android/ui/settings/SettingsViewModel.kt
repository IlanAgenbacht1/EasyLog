package com.easylog.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.easylog.android.data.repository.FirebaseAuthRepository
import com.easylog.android.domain.model.Engineer
import com.easylog.android.sync.SyncManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val currentEngineer: Engineer? = null,
    val isLoading: Boolean = true,
    val isLoggingOut: Boolean = false
)

class SettingsViewModel(
    private val firebaseAuthRepository: FirebaseAuthRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _loggedOut = MutableSharedFlow<Unit>()
    val loggedOut: SharedFlow<Unit> = _loggedOut.asSharedFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            firebaseAuthRepository.getCurrentEngineer().collect { engineer ->
                _uiState.update {
                    it.copy(
                        currentEngineer = engineer,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun onLogout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingOut = true) }
            try {
                syncManager.stopAllSync()
                firebaseAuthRepository.signOut()
                _loggedOut.emit(Unit)
            } catch (e: Exception) {
                // Even if logout fails, we should still navigate away
                // since local data is cleared
                _loggedOut.emit(Unit)
            } finally {
                _uiState.update { it.copy(isLoggingOut = false) }
            }
        }
    }
}
