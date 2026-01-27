package com.easylog.android.ui.job

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.easylog.android.data.repository.TimeLogRepository
import com.easylog.android.data.repository.TimeLogResult
import com.easylog.android.domain.model.TimeLog
import com.easylog.android.util.PhotoManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.ZoneId

data class CompleteJobUiState(
    val job: TimeLog? = null,
    val onSiteHours: Int = 0,
    val onSiteMinutes: Int = 0,
    val workDescription: String = "",
    val startTimeHour: Int = 0,
    val startTimeMinute: Int = 0,
    val photoPaths: List<String> = emptyList(),
    val isLoading: Boolean = true, // Start with loading to prevent flash
    val errorMessage: String? = null
)

class CompleteJobViewModel(
    private val timeLogRepository: TimeLogRepository,
    private val photoManager: PhotoManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // documentId is the Firestore document ID (formerly serverId)
    private val documentId: String = checkNotNull(savedStateHandle["documentId"])

    private val _uiState = MutableStateFlow(CompleteJobUiState())
    val uiState: StateFlow<CompleteJobUiState> = _uiState.asStateFlow()

    init {
        loadJob()
    }

    private fun loadJob() {
        viewModelScope.launch {
            // Track if this is the initial load
            var isInitialLoad = true

            timeLogRepository.getActiveTimeLog().collect { timeLog ->
                if (timeLog != null && timeLog.serverId == documentId) {
                    // Only set time values and photos on initial load
                    // After that, these are managed by user edits and shouldn't be overwritten
                    if (isInitialLoad) {
                        // Calculate elapsed time in minutes
                        val elapsedMinutes = ((System.currentTimeMillis() - timeLog.timeStarted.toEpochMilli()) / 60000).toInt()
                        val hours = elapsedMinutes / 60
                        val minutes = elapsedMinutes % 60

                        // Extract start time hour and minute
                        val startZoned = timeLog.timeStarted.atZone(ZoneId.systemDefault())
                        val startHour = startZoned.hour
                        val startMinute = startZoned.minute

                        // getPhotoPaths returns List<String> directly from Firestore
                        val existingPhotos = timeLogRepository.getPhotoPaths(documentId)

                        _uiState.update {
                            it.copy(
                                job = timeLog,
                                onSiteHours = hours,
                                onSiteMinutes = minutes,
                                startTimeHour = startHour,
                                startTimeMinute = startMinute,
                                photoPaths = existingPhotos,
                                isLoading = false,
                                errorMessage = null
                            )
                        }
                        isInitialLoad = false
                    } else {
                        // On subsequent emissions, only update the job reference
                        // Don't overwrite user-edited time values or photos
                        _uiState.update {
                            it.copy(
                                job = timeLog,
                                isLoading = false,
                                errorMessage = null
                            )
                        }
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

    fun onOnSiteHoursChanged(hours: String) {
        val hoursInt = hours.toIntOrNull()?.coerceIn(0, 24) ?: 0
        _uiState.update { it.copy(onSiteHours = hoursInt) }
    }

    fun onOnSiteMinutesChanged(minutes: String) {
        val minutesInt = minutes.toIntOrNull()?.coerceIn(0, 59) ?: 0
        _uiState.update { it.copy(onSiteMinutes = minutesInt) }
    }

    fun onWorkDescriptionChanged(description: String) {
        _uiState.update { it.copy(workDescription = description) }
    }

    fun onStartTimeHourChanged(hour: String) {
        val hourInt = hour.toIntOrNull()?.coerceIn(0, 23) ?: 0
        _uiState.update { it.copy(startTimeHour = hourInt) }
        updateStartTimeInDb()
    }

    fun onStartTimeMinuteChanged(minute: String) {
        val minuteInt = minute.toIntOrNull()?.coerceIn(0, 59) ?: 0
        _uiState.update { it.copy(startTimeMinute = minuteInt) }
        updateStartTimeInDb()
    }

    private fun updateStartTimeInDb() {
        val job = _uiState.value.job ?: return
        val jobDocumentId = job.serverId ?: return
        val state = _uiState.value

        viewModelScope.launch {
            // Calculate new start time based on original date but with new hour/minute
            val originalZoned = job.timeStarted.atZone(ZoneId.systemDefault())
            val newStartTime = originalZoned
                .withHour(state.startTimeHour)
                .withMinute(state.startTimeMinute)
                .toInstant()
                .toEpochMilli()

            when (val result = timeLogRepository.updateStartTime(jobDocumentId, newStartTime)) {
                is TimeLogResult.Success -> {
                    // Recalculate on-site time based on new start time
                    val elapsedMinutes = ((System.currentTimeMillis() - newStartTime) / 60000).toInt()
                    val hours = elapsedMinutes / 60
                    val minutes = elapsedMinutes % 60
                    _uiState.update {
                        it.copy(
                            onSiteHours = hours,
                            onSiteMinutes = minutes
                        )
                    }
                }
                is TimeLogResult.Error -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun getTotalOnSiteMinutes(): Int {
        val currentState = _uiState.value
        return (currentState.onSiteHours * 60) + currentState.onSiteMinutes
    }

    // Photo handling

    fun createTempPhotoUri(): Uri {
        return photoManager.createTempPhotoUri()
    }

    fun addPhotoFromUri(uri: Uri) {
        Log.d("PhotoCapture", "addPhotoFromUri called with uri: $uri")
        viewModelScope.launch {
            val savedPath = photoManager.savePhoto(uri, documentId)
            Log.d("PhotoCapture", "savePhoto returned: $savedPath")
            if (savedPath != null) {
                val newPaths = _uiState.value.photoPaths + savedPath
                Log.d("PhotoCapture", "Updating photoPaths to: $newPaths")
                _uiState.update { it.copy(photoPaths = newPaths) }
                // Save to local Room DB for upload tracking
                when (val result = timeLogRepository.addPhotoPath(documentId, savedPath)) {
                    is TimeLogResult.Success -> {
                        Log.d("PhotoCapture", "Photo path saved to local DB: $savedPath")
                    }
                    is TimeLogResult.Error -> {
                        Log.e("PhotoCapture", "Failed to save photo path to local DB: ${result.message}")
                        _uiState.update {
                            it.copy(errorMessage = "Failed to track photo for upload: ${result.message}")
                        }
                    }
                }
            } else {
                Log.e("PhotoCapture", "Failed to save photo from uri: $uri")
                _uiState.update {
                    it.copy(errorMessage = "Failed to save photo")
                }
            }
        }
    }

    fun removePhoto(path: String) {
        viewModelScope.launch {
            photoManager.deletePhoto(path)
            val newPaths = _uiState.value.photoPaths.filter { p -> p != path }
            _uiState.update { it.copy(photoPaths = newPaths) }
            // Remove from local Room DB
            when (val result = timeLogRepository.removePhotoPath(documentId, path)) {
                is TimeLogResult.Success -> {
                    Log.d("PhotoCapture", "Photo path removed from local DB: $path")
                }
                is TimeLogResult.Error -> {
                    Log.e("PhotoCapture", "Failed to remove photo path from local DB: ${result.message}")
                    _uiState.update {
                        it.copy(errorMessage = "Failed to update photo tracking: ${result.message}")
                    }
                }
            }
        }
    }

    fun getPhotoPaths(): List<String> {
        return _uiState.value.photoPaths
    }

    fun getPhotoPathsJson(): String? {
        val paths = _uiState.value.photoPaths
        return if (paths.isEmpty()) null else photoManager.pathsToJson(paths)
    }
}
