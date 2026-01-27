package com.easylog.android.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.easylog.android.domain.model.TimeLog
import com.easylog.android.domain.model.TimeLogStatus
import com.easylog.android.storage.StorageManager
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.UUID

sealed class TimeLogResult<out T> {
    data class Success<T>(val data: T) : TimeLogResult<T>()
    data class Error(val message: String) : TimeLogResult<Nothing>()
}

/**
 * Repository for time log operations.
 *
 * Architecture: Firestore SDK is the primary source of truth.
 * - All writes go directly to Firestore (SDK handles offline queuing)
 * - Reads use Firestore real-time listeners
 * - File uploads tracked in local Room DB (prevents device-local paths in shared Firestore)
 * - SyncWorker reads Room DB to know which files to upload
 */
class TimeLogRepository(
    private val tokenProvider: AuthTokenProvider,
    private val storageManager: StorageManager,
    private val pendingUploadDao: com.easylog.android.data.local.dao.PendingUploadDao,
    private val context: Context
) {
    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "TimeLogRepository"
        private const val TIMELOGS_COLLECTION = "timelogs"
    }

    /**
     * Get active time log (in progress) using Firestore real-time listener.
     * Returns a Flow that emits updates whenever the active job changes.
     */
    fun getActiveTimeLog(): Flow<TimeLog?> = callbackFlow {
        var listener: ListenerRegistration? = null

        val engineerId = tokenProvider.getEngineerId().first()
        if (engineerId == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        listener = firestore.collection(TIMELOGS_COLLECTION)
            .whereEqualTo("engineerId", engineerId)
            .whereEqualTo("status", "in_progress")
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for active job", error)
                    trySend(null)
                    return@addSnapshotListener
                }

                val activeJob = snapshot?.documents?.firstOrNull()?.let { doc ->
                    documentToTimeLog(doc)
                }
                trySend(activeJob)
            }

        awaitClose { listener?.remove() }
    }

    /**
     * Fetch job history from Firestore.
     */
    suspend fun fetchJobHistory(
        startDate: String? = null,
        endDate: String? = null,
        status: String? = null,
        limit: Int = 100
    ): TimeLogResult<List<TimeLog>> {
        return try {
            val engineerId = tokenProvider.getEngineerId().first()
                ?: return TimeLogResult.Error("Not logged in")

            Log.d(TAG, "Fetching history for engineerId: $engineerId")

            var query = firestore.collection(TIMELOGS_COLLECTION)
                .whereEqualTo("engineerId", engineerId)

            if (status != null) {
                query = query.whereEqualTo("status", status)
            }

            if (startDate != null) {
                val startDateTime = LocalDate.parse(startDate)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                query = query.whereGreaterThanOrEqualTo("timeStarted", Timestamp(Date.from(startDateTime)))
            }

            if (endDate != null) {
                val endDateTime = LocalDate.parse(endDate)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                query = query.whereLessThan("timeStarted", Timestamp(Date.from(endDateTime)))
            }

            query = query.orderBy("timeStarted", Query.Direction.DESCENDING)
                .limit(limit.toLong())

            val snapshot = try {
                query.get(Source.DEFAULT).await()
            } catch (serverError: Exception) {
                Log.d(TAG, "Cache miss for job history, fetching from server")
                query.get(Source.CACHE).await()
            }
            val timeLogs = snapshot.documents.mapNotNull { documentToTimeLog(it) }

            TimeLogResult.Success(timeLogs)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch history: ${e.message}", e)
            TimeLogResult.Error("Failed to fetch history: ${e.localizedMessage}")
        }
    }

    /**
     * Data class for today's stats
     */
    data class TodayStats(
        val completedCount: Int,
        val totalMinutes: Int
    )

    /**
     * Fetch today's stats from Firestore.
     */
    suspend fun fetchTodayStats(): TimeLogResult<TodayStats> {
        return try {
            val engineerId = tokenProvider.getEngineerId().first()
                ?: return TimeLogResult.Error("Not logged in")

            val today = LocalDate.now()
            val todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val tomorrowStart = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()

            val query = firestore.collection(TIMELOGS_COLLECTION)
                .whereEqualTo("engineerId", engineerId)
                .whereEqualTo("status", "completed")
                .whereGreaterThanOrEqualTo("timeStarted", Timestamp(Date.from(todayStart)))
                .whereLessThan("timeStarted", Timestamp(Date.from(tomorrowStart)))

            val snapshot = try {
                query.get(Source.DEFAULT).await()
            } catch (cacheError: Exception) {
                Log.d(TAG, "Cache miss for today's stats, fetching from server")
                query.get(Source.CACHE).await()
            }

            val completedCount = snapshot.size()
            val totalMinutes = snapshot.documents.sumOf { doc ->
                val travel = doc.getLong("travelTimeMinutes")?.toInt() ?: 0
                val onsite = doc.getLong("timeOnSiteMinutes")?.toInt() ?: 0
                travel + onsite
            }

            TimeLogResult.Success(TodayStats(completedCount, totalMinutes))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch stats: ${e.message}", e)
            TimeLogResult.Error("Failed to fetch stats: ${e.localizedMessage}")
        }
    }

    /**
     * Start a new job.
     * Writes directly to Firestore - SDK handles offline queuing.
     */
    suspend fun startJob(
        customerId: String,
        customerName: String,
        travelTimeMinutes: Int
    ): TimeLogResult<TimeLog> {
        return try {
            val engineerId = tokenProvider.getEngineerId().first()
                ?: return TimeLogResult.Error("Not logged in")
            val engineerName = tokenProvider.getEngineerName().first() ?: "Unknown"

            // Check for existing active job
            val existingActive = firestore.collection(TIMELOGS_COLLECTION)
                .whereEqualTo("engineerId", engineerId)
                .whereEqualTo("status", "in_progress")
                .limit(1)
                .get(Source.CACHE)
                .await()

            if (!existingActive.isEmpty) {
                return TimeLogResult.Error("There is already an active job. Please complete it first.")
            }

            val localId = UUID.randomUUID().toString()
            val now = Timestamp.now()

            val timelogData = hashMapOf(
                "localId" to localId,
                "engineerId" to engineerId,
                "engineerName" to engineerName,
                "customerId" to customerId,
                "customerName" to customerName,
                "travelTimeMinutes" to travelTimeMinutes,
                "timeStarted" to now,
                "timeEnded" to null,
                "timeOnSiteMinutes" to null,
                "workDescription" to null,
                "customerSignerName" to null,
                "signaturePath" to null,
                "signedAt" to null,
                "status" to "in_progress",
                "createdAt" to now,
                "updatedAt" to now
            )

            // Write to Firestore - SDK queues if offline
            val docRef = firestore.collection(TIMELOGS_COLLECTION).document()
            docRef.set(timelogData)
            Log.d(TAG, "Job started (queued): ${docRef.id}")

            val timeLog = TimeLog(
                localId = localId,
                serverId = docRef.id,
                engineerId = engineerId,
                engineerName = engineerName,
                customerId = customerId,
                customerName = customerName,
                travelTimeMinutes = travelTimeMinutes,
                timeStarted = now.toDate().toInstant(),
                timeEnded = null,
                timeOnSiteMinutes = null,
                workDescription = null,
                customerSignerName = null,
                signaturePath = null,
                signatureUrl = null,
                signedAt = null,
                status = TimeLogStatus.IN_PROGRESS,
                isSynced = false
            )

            TimeLogResult.Success(timeLog)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start job: ${e.message}", e)
            TimeLogResult.Error("Failed to start job: ${e.localizedMessage}")
        }
    }

    /**
     * Update job start time.
     */
    suspend fun updateStartTime(
        documentId: String,
        newStartTime: Long
    ): TimeLogResult<Unit> {
        return try {
            firestore.collection(TIMELOGS_COLLECTION)
                .document(documentId)
                .update(
                    mapOf(
                        "timeStarted" to Timestamp(Date(newStartTime)),
                        "updatedAt" to Timestamp.now()
                    )
                )
            Log.d(TAG, "Start time update queued for $documentId")
            TimeLogResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update start time: ${e.message}", e)
            TimeLogResult.Error("Failed to update start time: ${e.localizedMessage}")
        }
    }

    /**
     * Update active job with work description.
     */
    suspend fun updateJob(
        documentId: String,
        workDescription: String?
    ): TimeLogResult<Unit> {
        return try {
            firestore.collection(TIMELOGS_COLLECTION)
                .document(documentId)
                .update(
                    mapOf(
                        "workDescription" to workDescription,
                        "updatedAt" to Timestamp.now()
                    )
                )
            Log.d(TAG, "Job update queued for $documentId")
            TimeLogResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update job: ${e.message}", e)
            TimeLogResult.Error("Failed to update job: ${e.localizedMessage}")
        }
    }

    /**
     * Save photo path to local Room DB for pending upload.
     * Does NOT write to Firestore (prevents device-local paths in shared DB).
     */
    suspend fun addPhotoPath(
        documentId: String,
        localPhotoPath: String
    ): TimeLogResult<Unit> {
        return try {
            // Get existing pending upload or create new one
            val existing = pendingUploadDao.getPendingUpload(documentId)

            if (existing != null) {
                // Add photo to existing pending upload
                val updatedPhotos = existing.localPhotoPaths + localPhotoPath
                pendingUploadDao.updatePendingUpload(
                    existing.copy(localPhotoPaths = updatedPhotos)
                )
            } else {
                // Create new pending upload entry
                pendingUploadDao.insertPendingUpload(
                    com.easylog.android.data.local.entity.PendingUploadEntity(
                        timeLogId = documentId,
                        localSignaturePath = null,
                        localPhotoPaths = listOf(localPhotoPath),
                        createdAt = System.currentTimeMillis()
                    )
                )
            }

            Log.d(TAG, "Photo path added to local pending uploads for $documentId")
            TimeLogResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add photo path: ${e.message}", e)
            TimeLogResult.Error("Failed to add photo path: ${e.localizedMessage}")
        }
    }

    /**
     * Get photo paths for a job from local Room DB.
     */
    suspend fun getPhotoPaths(documentId: String): List<String> {
        return try {
            val pending = pendingUploadDao.getPendingUpload(documentId)
            pending?.localPhotoPaths ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get photo paths: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Remove a photo path from local Room DB.
     */
    suspend fun removePhotoPath(
        documentId: String,
        photoPath: String
    ): TimeLogResult<Unit> {
        return try {
            val existing = pendingUploadDao.getPendingUpload(documentId)

            if (existing != null) {
                val updatedPhotos = existing.localPhotoPaths.filter { it != photoPath }
                if (updatedPhotos.isEmpty() && existing.localSignaturePath == null) {
                    // No more pending uploads, delete the entry
                    pendingUploadDao.deletePendingUpload(documentId)
                } else {
                    // Update with remaining photos
                    pendingUploadDao.updatePendingUpload(
                        existing.copy(localPhotoPaths = updatedPhotos)
                    )
                }
            }

            Log.d(TAG, "Photo path removed from local pending uploads for $documentId")
            TimeLogResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove photo path: ${e.message}", e)
            TimeLogResult.Error("Failed to remove photo path: ${e.localizedMessage}")
        }
    }

    /**
     * Save signature bitmap to local storage.
     */
    private fun saveSignatureLocally(documentId: String, signatureBitmap: Bitmap): String {
        val signaturesDir = File(context.filesDir, "signatures")
        if (!signaturesDir.exists()) {
            signaturesDir.mkdirs()
        }

        val fileName = "signature_$documentId.png"
        val file = File(signaturesDir, fileName)

        FileOutputStream(file).use { out ->
            signatureBitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
        }

        return file.absolutePath
    }

    /**
     * Complete active job with signature.
     * Saves files locally, writes to Room DB for upload tracking, and updates Firestore.
     * File uploads happen in background via SyncWorker (non-blocking).
     *
     * Architecture: Local file paths stored ONLY in Room DB (device-local).
     * Firestore gets uploadStatus field, NOT local paths (prevents data corruption).
     */
    suspend fun completeJob(
        documentId: String,
        workDescription: String,
        timeOnSiteMinutes: Int,
        customerSignerName: String,
        signatureBitmap: Bitmap? = null
    ): TimeLogResult<TimeLog> {
        return try {
            Log.d(TAG, "Completing job: $documentId")

            val now = Timestamp.now()
            var localSignaturePath: String? = null

            // Save signature locally (fast operation)
            if (signatureBitmap != null) {
                localSignaturePath = saveSignatureLocally(documentId, signatureBitmap)
            }

            // Get any local photo paths that need upload
            val localPhotoPaths = getPhotoPaths(documentId)

            // Write to Room DB for upload tracking (device-local only)
            if (localSignaturePath != null || localPhotoPaths.isNotEmpty()) {
                val pendingUpload = com.easylog.android.data.local.entity.PendingUploadEntity(
                    timeLogId = documentId,
                    localSignaturePath = localSignaturePath,
                    localPhotoPaths = localPhotoPaths,
                    createdAt = System.currentTimeMillis()
                )
                pendingUploadDao.insertPendingUpload(pendingUpload)
                Log.d(TAG, "Pending upload saved to local Room DB")
            }

            // Update Firestore with completion data - SDK queues if offline
            // uploadStatus field indicates files are pending upload (no local paths!)
            val hasFilesToUpload = localSignaturePath != null || localPhotoPaths.isNotEmpty()
            val completionData = mutableMapOf<String, Any?>(
                "timeEnded" to now,
                "timeOnSiteMinutes" to timeOnSiteMinutes,
                "workDescription" to workDescription,
                "customerSignerName" to customerSignerName,
                "signedAt" to now,
                "status" to "completed",
                "uploadStatus" to if (hasFilesToUpload) "pending" else "completed",
                "uploadQueuedAt" to if (hasFilesToUpload) now else null,
                "updatedAt" to now
            )

            firestore.collection(TIMELOGS_COLLECTION)
                .document(documentId)
                .update(completionData)

            Log.d(TAG, "Job completion queued (uploads will happen in background)")

            // Return success immediately - SyncWorker handles file uploads
            // Build a TimeLog object from current data
            val timeLog = TimeLog(
                localId = "",
                serverId = documentId,
                engineerId = tokenProvider.getEngineerId().first() ?: "",
                engineerName = tokenProvider.getEngineerName().first() ?: "",
                customerId = "",
                customerName = "",
                travelTimeMinutes = 0,
                timeStarted = now.toDate().toInstant(),
                timeEnded = now.toDate().toInstant(),
                timeOnSiteMinutes = timeOnSiteMinutes,
                workDescription = workDescription,
                customerSignerName = customerSignerName,
                signaturePath = localSignaturePath,
                signatureUrl = null,
                signedAt = now.toDate().toInstant(),
                status = com.easylog.android.domain.model.TimeLogStatus.COMPLETED,
                isSynced = false
            )

            TimeLogResult.Success(timeLog)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to complete job: ${e.message}", e)
            TimeLogResult.Error("Failed to complete job: ${e.localizedMessage}")
        }
    }

    /**
     * Cancel an active job (delete from Firestore).
     */
    suspend fun cancelJob(documentId: String): TimeLogResult<Unit> {
        return try {
            firestore.collection(TIMELOGS_COLLECTION)
                .document(documentId)
                .delete()
                .await()
            Log.d(TAG, "Job cancelled: $documentId")
            TimeLogResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel job: ${e.message}", e)
            TimeLogResult.Error("Failed to cancel job: ${e.localizedMessage}")
        }
    }


    /**
     * Convert Firestore document to TimeLog domain model.
     */
    private fun documentToTimeLog(doc: com.google.firebase.firestore.DocumentSnapshot): TimeLog? {
        return try {
            val timeStartedTimestamp = doc.getTimestamp("timeStarted")
            val timeEndedTimestamp = doc.getTimestamp("timeEnded")
            val signedAtTimestamp = doc.getTimestamp("signedAt")

            TimeLog(
                localId = doc.getString("localId") ?: doc.id,
                serverId = doc.id,
                engineerId = doc.getString("engineerId") ?: "",
                engineerName = doc.getString("engineerName") ?: "",
                customerId = doc.getString("customerId") ?: "",
                customerName = doc.getString("customerName") ?: "",
                travelTimeMinutes = doc.getLong("travelTimeMinutes")?.toInt() ?: 0,
                timeStarted = timeStartedTimestamp?.toDate()?.toInstant() ?: Instant.now(),
                timeEnded = timeEndedTimestamp?.toDate()?.toInstant(),
                timeOnSiteMinutes = doc.getLong("timeOnSiteMinutes")?.toInt(),
                workDescription = doc.getString("workDescription"),
                customerSignerName = doc.getString("customerSignerName"),
                signaturePath = doc.getString("signaturePath"),
                signatureUrl = doc.getString("signatureUrl"),
                signedAt = signedAtTimestamp?.toDate()?.toInstant(),
                status = TimeLogStatus.fromString(doc.getString("status") ?: "in_progress"),
                isSynced = true // Always synced since Firestore is source of truth
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing timelog ${doc.id}", e)
            null
        }
    }
}
