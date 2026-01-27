package com.easylog.android.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.easylog.android.data.local.dao.PendingUploadDao
import com.easylog.android.storage.StorageManager
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Background worker that uploads pending files (signatures, photos) to Firebase Storage.
 *
 * Architecture: Firestore SDK handles data sync automatically (offline queuing).
 * This worker ONLY handles file uploads since Firebase Storage SDK doesn't queue offline.
 * Reads pending uploads from local Room DB (NOT Firestore - prevents device-local paths in shared DB).
 *
 * Runs periodically (every 15 minutes) and when network becomes available.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val pendingUploadDao: PendingUploadDao by inject()
    private val storageManager: StorageManager by inject()
    private val firestore = FirebaseFirestore.getInstance()

    override suspend fun doWork(): Result {
        Log.d(TAG, "SyncWorker started (attempt ${runAttemptCount + 1}/$MAX_RETRY_ATTEMPTS) - checking for pending file uploads")

        return try {
            // Read pending uploads from local Room DB (NOT Firestore)
            val pendingUploads = pendingUploadDao.getAllPendingUploads()
            Log.d(TAG, "Found ${pendingUploads.size} jobs with pending uploads in local DB")

            if (pendingUploads.isEmpty()) {
                Log.d(TAG, "No pending uploads")
                return Result.success()
            }

            var successCount = 0
            var failureCount = 0

            for ((jobIndex, upload) in pendingUploads.withIndex()) {
                // Add small delay between jobs to avoid hammering the server
                if (jobIndex > 0) {
                    kotlinx.coroutines.delay(500)
                }

                try {
                    var needsUpdate = false
                    var updatedSignaturePath: String? = null
                    var signatureUploaded = false
                    val uploadedPhotoPaths = mutableListOf<String>()
                    val failedPhotoPaths = mutableListOf<String>()

                    // Upload signature if pending
                    if (!upload.localSignaturePath.isNullOrEmpty()) {
                        val localFile = File(upload.localSignaturePath)
                        if (localFile.exists()) {
                            Log.d(TAG, "Uploading signature for job ${upload.timeLogId}")
                            when (val result = storageManager.uploadSignatureFromFile(upload.timeLogId, upload.localSignaturePath)) {
                                is StorageManager.UploadResult.Success -> {
                                    updatedSignaturePath = result.storagePath
                                    signatureUploaded = true
                                    needsUpdate = true
                                    Log.d(TAG, "Signature uploaded: ${result.storagePath}")
                                    // Delete local file after successful upload
                                    localFile.delete()
                                }
                                is StorageManager.UploadResult.Error -> {
                                    Log.e(TAG, "Failed to upload signature: ${result.message}")
                                    failureCount++
                                }
                            }
                        } else {
                            Log.w(TAG, "Local signature file doesn't exist: ${upload.localSignaturePath}")
                            // Clear the stale reference
                            signatureUploaded = true
                            needsUpdate = true
                        }
                    }

                    // Upload photos if pending
                    if (upload.localPhotoPaths.isNotEmpty()) {
                        Log.d(TAG, "Uploading ${upload.localPhotoPaths.size} photos for job ${upload.timeLogId}")

                        upload.localPhotoPaths.forEachIndexed { index, localPath ->
                            val localFile = File(localPath)
                            if (localFile.exists()) {
                                when (val result = storageManager.uploadPhoto(upload.timeLogId, index, localPath)) {
                                    is StorageManager.UploadResult.Success -> {
                                        uploadedPhotoPaths.add(result.storagePath)
                                        needsUpdate = true
                                        // Delete local file after successful upload
                                        localFile.delete()
                                    }
                                    is StorageManager.UploadResult.Error -> {
                                        Log.e(TAG, "Failed to upload photo: ${result.message}")
                                        failedPhotoPaths.add(localPath)
                                    }
                                }
                            } else {
                                Log.w(TAG, "Local photo file doesn't exist: $localPath")
                                // Don't add to failed - it's gone
                            }
                        }
                    }

                    // Update Firestore with cloud paths if any uploads succeeded
                    if (needsUpdate) {
                        val updates = mutableMapOf<String, Any?>(
                            "updatedAt" to Timestamp.now()
                        )

                        if (signatureUploaded) {
                            updates["signaturePath"] = updatedSignaturePath
                        }

                        if (uploadedPhotoPaths.isNotEmpty()) {
                            // Get existing cloud photo paths and add new ones
                            val doc = firestore.collection(TIMELOGS_COLLECTION)
                                .document(upload.timeLogId)
                                .get()
                                .await()

                            @Suppress("UNCHECKED_CAST")
                            val existingCloudPaths = (doc.get("photoPaths") as? List<String>) ?: emptyList()
                            updates["photoPaths"] = existingCloudPaths + uploadedPhotoPaths
                        }

                        // Update uploadStatus if all files uploaded successfully
                        if (failedPhotoPaths.isEmpty() && (upload.localSignaturePath == null || signatureUploaded)) {
                            updates["uploadStatus"] = "completed"
                            updates["uploadQueuedAt"] = null
                        }

                        firestore.collection(TIMELOGS_COLLECTION)
                            .document(upload.timeLogId)
                            .update(updates)
                            .await()

                        Log.d(TAG, "Updated Firestore with upload results for ${upload.timeLogId}")
                    }

                    // Delete from Room DB if all uploads completed successfully
                    if (failedPhotoPaths.isEmpty() && (upload.localSignaturePath == null || signatureUploaded)) {
                        pendingUploadDao.deletePendingUpload(upload.timeLogId)
                        Log.d(TAG, "Removed completed upload from local DB: ${upload.timeLogId}")
                        successCount++
                    } else if (failedPhotoPaths.isNotEmpty()) {
                        // Update Room DB with only failed photos
                        pendingUploadDao.updatePendingUpload(
                            upload.copy(
                                localSignaturePath = if (signatureUploaded) null else upload.localSignaturePath,
                                localPhotoPaths = failedPhotoPaths
                            )
                        )
                        Log.d(TAG, "Updated local DB with remaining failed uploads: ${upload.timeLogId}")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process job ${upload.timeLogId}: ${e.message}")
                    failureCount++
                }
            }

            Log.d(TAG, "Upload sync completed: $successCount successful, $failureCount failed")

            // Return success even with partial failures - WorkManager periodic job will retry later
            // Only return retry() if everything failed AND we haven't exceeded max attempts
            if (failureCount > 0 && successCount == 0 && runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Log.w(TAG, "All uploads failed, scheduling retry (attempt ${runAttemptCount + 1}/$MAX_RETRY_ATTEMPTS)")
                Result.retry()
            } else {
                if (failureCount > 0) {
                    Log.w(TAG, "Some uploads failed ($failureCount), will retry on next periodic sync")
                }
                Result.success(
                    workDataOf(
                        KEY_SYNCED_COUNT to successCount,
                        KEY_FAILED_COUNT to failureCount
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "SyncWorker failed: ${e.message}", e)
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Log.w(TAG, "Scheduling retry (attempt ${runAttemptCount + 1}/$MAX_RETRY_ATTEMPTS)")
                Result.retry()
            } else {
                Log.e(TAG, "Max retry attempts reached, giving up")
                Result.failure()
            }
        }
    }

    companion object {
        const val TAG = "SyncWorker"
        const val WORK_NAME = "file_upload_sync"
        const val KEY_SYNCED_COUNT = "synced_count"
        const val KEY_FAILED_COUNT = "failed_count"
        const val MAX_RETRY_ATTEMPTS = 5
        private const val TIMELOGS_COLLECTION = "timelogs"

        /**
         * Create a one-time upload request (for immediate upload attempt)
         */
        fun createOneTimeRequest(): OneTimeWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            return OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .addTag(TAG_ONE_TIME_SYNC)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    2,  // Start at 2 minutes, then 4, 8, etc.
                    TimeUnit.MINUTES
                )
                .build()
        }

        private const val TAG_ONE_TIME_SYNC = "one_time_sync"

        /**
         * Create a periodic upload request (runs every 15 minutes when connected)
         */
        fun createPeriodicRequest(): PeriodicWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            return PeriodicWorkRequestBuilder<SyncWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES // Flex interval
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1,
                    TimeUnit.MINUTES
                )
                .build()
        }
    }
}
