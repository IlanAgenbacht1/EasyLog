package com.easylog.android.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.easylog.android.data.repository.TimeLogRepository
import com.easylog.android.data.repository.TimeLogResult
import com.easylog.android.domain.model.TimeLog
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class DateFilter(val label: String, val days: Int) {
    LAST_7_DAYS("Last 7 Days", 7),
    LAST_30_DAYS("Last 30 Days", 30),
    LAST_90_DAYS("Last 90 Days", 90),
    ALL_TIME("All Time", 365 * 10)
}

data class DayGroup(
    val date: LocalDate,
    val displayDate: String,
    val jobs: List<TimeLog>,
    val totalMinutes: Int
)

data class HistoryUiState(
    val dateFilter: DateFilter = DateFilter.LAST_30_DAYS,
    val dayGroups: List<DayGroup> = emptyList(),
    val totalJobs: Int = 0,
    val totalMinutes: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class HistoryViewModel(
    private val timeLogRepository: TimeLogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val _selectedFilter = MutableStateFlow(DateFilter.LAST_30_DAYS)

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            _selectedFilter.collectLatest { filter ->
                _uiState.update { it.copy(isLoading = true, errorMessage = null, dateFilter = filter) }

                try {
                    val endDate = LocalDate.now().plusDays(1)
                    val startDate = LocalDate.now().minusDays(filter.days.toLong())

                    // Format dates for API (YYYY-MM-DD)
                    val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
                    val startDateStr = startDate.format(dateFormatter)
                    val endDateStr = endDate.format(dateFormatter)

                    // Fetch from API
                    when (val result = timeLogRepository.fetchJobHistory(
                        startDate = startDateStr,
                        endDate = endDateStr,
                        status = "completed",
                        limit = 100
                    )) {
                        is TimeLogResult.Success -> {
                            val completedLogs = result.data

                            // Group by date
                            val grouped = completedLogs.groupBy { log ->
                                log.timeStarted.atZone(ZoneId.systemDefault()).toLocalDate()
                            }

                            val dayGroups = grouped.map { (date, logs) ->
                                DayGroup(
                                    date = date,
                                    displayDate = formatDateHeader(date),
                                    jobs = logs.sortedByDescending { it.timeStarted },
                                    totalMinutes = logs.sumOf { it.totalTimeMinutes }
                                )
                            }.sortedByDescending { it.date }

                            _uiState.update {
                                it.copy(
                                    dayGroups = dayGroups,
                                    totalJobs = completedLogs.size,
                                    totalMinutes = completedLogs.sumOf { log -> log.totalTimeMinutes },
                                    isLoading = false
                                )
                            }
                        }
                        is TimeLogResult.Error -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = result.message
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to load history: ${e.localizedMessage}"
                        )
                    }
                }
            }
        }
    }

    fun onFilterSelected(filter: DateFilter) {
        _selectedFilter.value = filter
    }

    private fun formatDateHeader(date: LocalDate): String {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        return when (date) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> {
                val daysBetween = ChronoUnit.DAYS.between(date, today)
                if (daysBetween < 7) {
                    date.format(DateTimeFormatter.ofPattern("EEEE")) // "Monday", "Tuesday", etc.
                } else {
                    date.format(DateTimeFormatter.ofPattern("EEEE, MMM d")) // "Monday, Jan 15"
                }
            }
        }
    }

    fun formatDuration(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            mins > 0 -> "${mins}m"
            else -> "0m"
        }
    }

    fun formatTime(instant: Instant): String {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault())
        return formatter.format(instant)
    }
}
