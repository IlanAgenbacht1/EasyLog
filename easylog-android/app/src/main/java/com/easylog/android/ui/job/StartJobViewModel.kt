package com.easylog.android.ui.job

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.easylog.android.data.repository.CustomerRepository
import com.easylog.android.data.repository.TimeLogRepository
import com.easylog.android.data.repository.TimeLogResult
import com.easylog.android.domain.model.Customer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class StartJobUiState(
    val customer: Customer? = null,
    val travelHours: Int = 0,
    val travelMinutes: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val jobStarted: Boolean = false
)

class StartJobViewModel(
    private val customerRepository: CustomerRepository,
    private val timeLogRepository: TimeLogRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val customerId: String = checkNotNull(savedStateHandle["customerId"])

    private val _uiState = MutableStateFlow(StartJobUiState())
    val uiState: StateFlow<StartJobUiState> = _uiState.asStateFlow()

    init {
        loadCustomer()
    }

    private fun loadCustomer() {
        viewModelScope.launch {
            // Don't set isLoading = true for local data - it loads instantly
            // and would cause a brief UI flicker

            val customer = customerRepository.getCustomerById(customerId)
            if (customer != null) {
                _uiState.update {
                    it.copy(
                        customer = customer,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Customer not found"
                    )
                }
            }
        }
    }

    fun onTravelHoursChanged(hours: String) {
        val hoursInt = hours.toIntOrNull()?.coerceIn(0, 12) ?: 0
        _uiState.update { it.copy(travelHours = hoursInt) }
    }

    fun onTravelMinutesChanged(minutes: String) {
        val minutesInt = minutes.toIntOrNull()?.coerceIn(0, 59) ?: 0
        _uiState.update { it.copy(travelMinutes = minutesInt) }
    }

    fun onStartJobClicked() {
        val currentState = _uiState.value

        if (currentState.customer == null) {
            _uiState.update { it.copy(errorMessage = "Customer not loaded") }
            return
        }

        val totalTravelMinutes = (currentState.travelHours * 60) + currentState.travelMinutes

        if (totalTravelMinutes == 0) {
            _uiState.update { it.copy(errorMessage = "Please enter travel time") }
            return
        }

        if (totalTravelMinutes > 720) { // Max 12 hours
            _uiState.update { it.copy(errorMessage = "Travel time cannot exceed 12 hours") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            when (val result = timeLogRepository.startJob(
                customerId = currentState.customer.id,
                customerName = currentState.customer.name,
                travelTimeMinutes = totalTravelMinutes
            )) {
                is TimeLogResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            jobStarted = true,
                            errorMessage = null
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
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
