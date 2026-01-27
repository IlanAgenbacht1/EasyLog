package com.easylog.android.ui.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.easylog.android.data.repository.CustomerRepository
import com.easylog.android.data.repository.CustomerResult
import com.easylog.android.domain.model.Customer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CustomerSelectionUiState(
    val customers: List<Customer> = emptyList(),
    val filteredCustomers: List<Customer> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class CustomerSelectionViewModel(
    private val customerRepository: CustomerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomerSelectionUiState())
    val uiState: StateFlow<CustomerSelectionUiState> = _uiState.asStateFlow()

    init {
        fetchCustomers()
    }

    /**
     * Fetch customers using API-first pattern
     * Tries server first, falls back to cache if offline
     */
    private fun fetchCustomers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            when (val result = customerRepository.fetchCustomers()) {
                is CustomerResult.Success -> {
                    val sorted = result.data.sortedBy { it.name.lowercase() }
                    _uiState.update {
                        it.copy(
                            customers = sorted,
                            filteredCustomers = filterCustomers(sorted, it.searchQuery),
                            isLoading = false,
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

    fun onSearchQueryChanged(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                filteredCustomers = filterCustomers(it.customers, query)
            )
        }
    }

    fun onRefresh() {
        fetchCustomers()
    }

    private fun filterCustomers(customers: List<Customer>, query: String): List<Customer> {
        if (query.isBlank()) return customers

        val lowercaseQuery = query.lowercase()
        return customers.filter { customer ->
            customer.name.lowercase().contains(lowercaseQuery) ||
            customer.address?.lowercase()?.contains(lowercaseQuery) == true ||
            customer.contactPerson?.lowercase()?.contains(lowercaseQuery) == true
        }
    }

    fun getRecentCustomers(): List<Customer> {
        // For now, just return the first 5 customers
        // In the future, this could be based on recently used customers from time logs
        return _uiState.value.filteredCustomers.take(5)
    }
}
