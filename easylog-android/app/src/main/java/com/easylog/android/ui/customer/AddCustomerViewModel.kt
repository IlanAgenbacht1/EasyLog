package com.easylog.android.ui.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.easylog.android.data.repository.AuthTokenProvider
import com.easylog.android.data.repository.CustomerRepository
import com.easylog.android.data.repository.CustomerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddCustomerUiState(
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val contactPerson: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false,
    val createdCustomerId: String? = null
)

class AddCustomerViewModel(
    private val customerRepository: CustomerRepository,
    private val tokenProvider: AuthTokenProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddCustomerUiState())
    val uiState: StateFlow<AddCustomerUiState> = _uiState.asStateFlow()

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name, errorMessage = null) }
    }

    fun onAddressChanged(address: String) {
        _uiState.update { it.copy(address = address) }
    }

    fun onPhoneChanged(phone: String) {
        _uiState.update { it.copy(phone = phone) }
    }

    fun onEmailChanged(email: String) {
        _uiState.update { it.copy(email = email) }
    }

    fun onContactPersonChanged(contactPerson: String) {
        _uiState.update { it.copy(contactPerson = contactPerson) }
    }

    fun onSaveClicked() {
        val currentState = _uiState.value

        // Validation
        if (currentState.name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Customer name is required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val engineerId = tokenProvider.getEngineerId().first()
            if (engineerId == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Not logged in") }
                return@launch
            }

            when (val result = customerRepository.createCustomer(
                name = currentState.name.trim(),
                address = currentState.address.trim().takeIf { it.isNotBlank() },
                phone = currentState.phone.trim().takeIf { it.isNotBlank() },
                email = currentState.email.trim().takeIf { it.isNotBlank() },
                contactPerson = currentState.contactPerson.trim().takeIf { it.isNotBlank() },
                createdBy = engineerId
            )) {
                is CustomerResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSuccess = true,
                            createdCustomerId = result.data.id,
                            errorMessage = null
                        )
                    }
                }
                is CustomerResult.Error -> {
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
