package com.easylog.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.easylog.android.data.repository.FirebaseAuthRepository
import com.easylog.android.data.repository.TimeLogRepository
import com.easylog.android.data.repository.TimeLogResult
import com.easylog.android.domain.model.Engineer
import com.easylog.android.domain.model.TimeLog
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val currentEngineer: Engineer? = null,
    val activeJob: TimeLog? = null,
    val todayCompletedCount: Int = 0,
    val todayTotalMinutes: Int = 0,
    val isLoading: Boolean = true, // Start true - prevents premature logout check
    val errorMessage: String? = null
)

class HomeViewModel(
    private val firebaseAuthRepository: FirebaseAuthRepository,
    private val timeLogRepository: TimeLogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeData()
    }

    private fun loadHomeData() {
        viewModelScope.launch {
            try {
                // Don't set isLoading = true for local data - it loads instantly
                // and would cause a brief flicker

                // Combine engineer and active job flows (local data)
                combine(
                    firebaseAuthRepository.getCurrentEngineer(),
                    timeLogRepository.getActiveTimeLog()
                ) { engineer, activeJob ->
                    Pair(engineer, activeJob)
                }.collect { (engineer, activeJob) ->
                    _uiState.update {
                        it.copy(
                            currentEngineer = engineer,
                            activeJob = activeJob,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load data: ${e.localizedMessage}"
                    )
                }
            }
        }

        // Fetch today's stats from server separately
        viewModelScope.launch {
            when (val result = timeLogRepository.fetchTodayStats()) {
                is TimeLogResult.Success -> {
                    _uiState.update {
                        it.copy(
                            todayCompletedCount = result.data.completedCount,
                            todayTotalMinutes = result.data.totalMinutes
                        )
                    }
                }
                is TimeLogResult.Error -> {
                    // Don't show error for stats - just keep defaults
                }
            }
        }
    }

    fun formatTotalTime(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            mins > 0 -> "${mins}m"
            else -> "0m"
        }
    }
}
