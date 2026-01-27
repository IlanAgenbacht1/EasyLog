package com.easylog.android.ui.job

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.easylog.android.data.repository.TimeLogRepository
import com.easylog.android.data.repository.TimeLogResult
import com.easylog.android.domain.model.TimeLog
import com.easylog.android.sync.SyncManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SignatureUiState(
    val job: TimeLog? = null,
    val workDescription: String = "",
    val onSiteMinutes: Int = 0,
    val customerSignerName: String = "",
    val hasSignature: Boolean = false,
    val isLoading: Boolean = true, // Start with loading to prevent flash
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val isCompleted: Boolean = false
)

class SignatureViewModel(
    private val timeLogRepository: TimeLogRepository,
    private val syncManager: SyncManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // documentId is the Firestore document ID (formerly serverId)
    private val documentId: String = checkNotNull(savedStateHandle["documentId"])
    private val workDescription: String = savedStateHandle["workDescription"] ?: ""
    private val onSiteMinutes: Int = savedStateHandle["onSiteMinutes"] ?: 0

    private val _uiState = MutableStateFlow(
        SignatureUiState(
            workDescription = workDescription,
            onSiteMinutes = onSiteMinutes
        )
    )
    val uiState: StateFlow<SignatureUiState> = _uiState.asStateFlow()

    init {
        loadJob()
    }

    private fun loadJob() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            timeLogRepository.getActiveTimeLog().collect { timeLog ->
                if (timeLog != null && timeLog.serverId == documentId) {
                    _uiState.update {
                        it.copy(
                            job = timeLog,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Job not found"
                        )
                    }
                }
            }
        }
    }

    fun onCustomerSignerNameChanged(name: String) {
        _uiState.update { it.copy(customerSignerName = name) }
    }

    fun onSignatureChanged(hasSignature: Boolean) {
        _uiState.update { it.copy(hasSignature = hasSignature) }
    }

    fun onCompleteJob(signatureBitmap: android.graphics.Bitmap?) {
        val currentState = _uiState.value

        if (currentState.job == null) {
            _uiState.update { it.copy(errorMessage = "Job not found") }
            return
        }

        if (currentState.customerSignerName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter customer's name") }
            return
        }

        if (signatureBitmap == null || !currentState.hasSignature) {
            _uiState.update { it.copy(errorMessage = "Please provide a signature") }
            Log.d("JobComplete", signatureBitmap.toString())
            Log.d("JobComplete", "hasSignature = " + currentState.hasSignature)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }

            // Complete the job with signature bitmap
            // The repository will handle uploading signature and completing
            when (val result = timeLogRepository.completeJob(
                documentId = documentId,
                workDescription = currentState.workDescription,
                timeOnSiteMinutes = currentState.onSiteMinutes,
                customerSignerName = currentState.customerSignerName,
                signatureBitmap = signatureBitmap
            )) {
                is TimeLogResult.Success -> {
                    // Trigger immediate upload in background
                    syncManager.syncNow()

                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            isCompleted = true,
                            errorMessage = null
                        )
                    }
                }
                is TimeLogResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
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
