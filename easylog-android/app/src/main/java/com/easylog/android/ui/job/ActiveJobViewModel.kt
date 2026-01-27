package com.easylog.android.ui.job

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.easylog.android.data.repository.TimeLogRepository
import com.easylog.android.domain.model.TimeLog
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class ActiveJobUiState(
    val activeJob: TimeLog? = null,
    val elapsedTime: Duration = Duration.ZERO,
    val isLoading: Boolean = true, // Start with loading to prevent flash
    val errorMessage: String? = null,
    val isCancelling: Boolean = false,
    val cancelSuccess: Boolean = false
)

class ActiveJobViewModel(
    private val timeLogRepository: TimeLogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActiveJobUiState())
    val uiState: StateFlow<ActiveJobUiState> = _uiState.asStateFlow()

    init {
        loadActiveJob()
    }

    private fun loadActiveJob() {
        viewModelScope.launch {
            timeLogRepository.getActiveTimeLog().collect { timeLog ->
                if (timeLog != null) {
                    val elapsed = (System.currentTimeMillis() - timeLog.timeStarted.toEpochMilli()).milliseconds
                    _uiState.update {
                        it.copy(
                            activeJob = timeLog,
                            elapsedTime = elapsed,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            activeJob = null,
                            isLoading = false,
                            errorMessage = "No active job found"
                        )
                    }
                }
            }
        }
    }

    fun updateElapsedTime() {
        val currentJob = _uiState.value.activeJob ?: return
        val elapsed = (System.currentTimeMillis() - currentJob.timeStarted.toEpochMilli()).milliseconds
        _uiState.update { it.copy(elapsedTime = elapsed) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun cancelJob() {
        viewModelScope.launch {
            val job = _uiState.value.activeJob ?: return@launch
            val documentId = job.serverId ?: return@launch

            _uiState.update { it.copy(isCancelling = true, errorMessage = null) }

            when (val result = timeLogRepository.cancelJob(documentId)) {
                is com.easylog.android.data.repository.TimeLogResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isCancelling = false,
                            cancelSuccess = true,
                            errorMessage = null
                        )
                    }
                }
                is com.easylog.android.data.repository.TimeLogResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isCancelling = false,
                            cancelSuccess = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }
}
