package com.easylog.android.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Manages file uploads to Google Cloud Storage via Firebase Storage SDK.
 * Uses the existing GCS bucket (ezl_bucket_01) for storage.
 */
class StorageManager(private val context: Context) {

    companion object {
        private const val TAG = "StorageManager"

        // Custom GCS bucket for EasyLog file storage
        private const val BUCKET_URL = "gs://ezl_bucket_01"

        // Upload constraints
        private const val MAX_SIGNATURE_SIZE_BYTES = 1 * 1024 * 1024  // 1MB
        private const val MAX_PHOTO_SIZE_BYTES = 5 * 1024 * 1024      // 5MB
        private const val SIGNATURE_QUALITY = 80
        private const val PHOTO_QUALITY = 85
        private const val SIGNATURE_MAX_DIMENSION = 1024
        private const val PHOTO_MAX_DIMENSION = 2048

        // Retry configuration
        private const val MAX_RETRIES = 3
        private val RETRY_DELAYS_MS = listOf(1000L, 5000L, 15000L)
    }

    // Firebase Storage instance pointing to custom bucket
    private val storage = FirebaseStorage.getInstance(BUCKET_URL)

    /**
     * Upload result containing the storage path
     */
    sealed class UploadResult {
        data class Success(val storagePath: String) : UploadResult()
        data class Error(val message: String) : UploadResult()
    }

    /**
     * Upload a signature image for a time log.
     * Compresses the image before upload.
     *
     * @param timelogId The Firestore document ID of the time log
     * @param signatureBitmap The signature bitmap to upload
     * @return UploadResult with storage path on success
     */
    suspend fun uploadSignature(
        timelogId: String,
        signatureBitmap: Bitmap
    ): UploadResult {
        return try {
            Log.d(TAG, "Uploading signature for timelog: $timelogId")

            // Compress the signature
            val compressedData = compressImage(
                signatureBitmap,
                maxDimension = SIGNATURE_MAX_DIMENSION,
                quality = SIGNATURE_QUALITY,
                maxSizeBytes = MAX_SIGNATURE_SIZE_BYTES
            )

            // Storage path: timelogs/{timelogId}/signature.png
            val storagePath = "timelogs/$timelogId/signature.png"

            uploadWithRetry(storagePath, compressedData, "image/png")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload signature: ${e.message}", e)
            UploadResult.Error("Failed to upload signature: ${e.localizedMessage}")
        }
    }

    /**
     * Upload a signature from a local file path.
     *
     * @param timelogId The Firestore document ID of the time log
     * @param localFilePath Path to the local signature file
     * @return UploadResult with storage path on success
     */
    suspend fun uploadSignatureFromFile(
        timelogId: String,
        localFilePath: String
    ): UploadResult {
        return try {
            val file = File(localFilePath)
            if (!file.exists()) {
                return UploadResult.Error("Signature file not found: $localFilePath")
            }

            val bitmap = BitmapFactory.decodeFile(localFilePath)
                ?: return UploadResult.Error("Failed to decode signature file")

            val result = uploadSignature(timelogId, bitmap)

            // Delete local file on success
            if (result is UploadResult.Success) {
                file.delete()
                Log.d(TAG, "Deleted local signature file: $localFilePath")
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload signature from file: ${e.message}", e)
            UploadResult.Error("Failed to upload signature: ${e.localizedMessage}")
        }
    }

    /**
     * Upload a photo for a time log.
     * Compresses the image before upload.
     *
     * @param timelogId The Firestore document ID of the time log
     * @param photoIndex Index of the photo (for multiple photos)
     * @param localFilePath Path to the local photo file
     * @return UploadResult with storage path on success
     */
    suspend fun uploadPhoto(
        timelogId: String,
        photoIndex: Int,
        localFilePath: String
    ): UploadResult {
        return try {
            Log.d(TAG, "Uploading photo $photoIndex for timelog: $timelogId")

            val file = File(localFilePath)
            if (!file.exists()) {
                return UploadResult.Error("Photo file not found: $localFilePath")
            }

            // Decode and compress
            val bitmap = BitmapFactory.decodeFile(localFilePath)
                ?: return UploadResult.Error("Failed to decode photo file")

            val compressedData = compressImage(
                bitmap,
                maxDimension = PHOTO_MAX_DIMENSION,
                quality = PHOTO_QUALITY,
                maxSizeBytes = MAX_PHOTO_SIZE_BYTES
            )

            // Determine file extension from original
            val extension = when {
                localFilePath.endsWith(".png", ignoreCase = true) -> "png"
                else -> "jpg"
            }
            val mimeType = if (extension == "png") "image/png" else "image/jpeg"

            // Storage path: timelogs/{timelogId}/photos/photo_{index}.{ext}
            val storagePath = "timelogs/$timelogId/photos/photo_$photoIndex.$extension"

            val result = uploadWithRetry(storagePath, compressedData, mimeType)

            // Delete local file on success
            if (result is UploadResult.Success) {
                file.delete()
                Log.d(TAG, "Deleted local photo file: $localFilePath")
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload photo: ${e.message}", e)
            UploadResult.Error("Failed to upload photo: ${e.localizedMessage}")
        }
    }

    /**
     * Upload multiple photos for a time log.
     *
     * @param timelogId The Firestore document ID of the time log
     * @param localFilePaths List of local photo file paths
     * @return List of results (success paths or errors) and list of failed paths for retry
     */
    suspend fun uploadPhotos(
        timelogId: String,
        localFilePaths: List<String>
    ): Pair<List<String>, List<String>> {
        val uploadedPaths = mutableListOf<String>()
        val failedPaths = mutableListOf<String>()

        localFilePaths.forEachIndexed { index, localPath ->
            when (val result = uploadPhoto(timelogId, index, localPath)) {
                is UploadResult.Success -> {
                    uploadedPaths.add(result.storagePath)
                    Log.d(TAG, "Uploaded photo $index: ${result.storagePath}")
                }
                is UploadResult.Error -> {
                    failedPaths.add(localPath)
                    Log.e(TAG, "Failed to upload photo $index: ${result.message}")
                }
            }
        }

        Log.d(TAG, "Photo upload complete: ${uploadedPaths.size} succeeded, ${failedPaths.size} failed")
        return Pair(uploadedPaths, failedPaths)
    }

    /**
     * Upload data with exponential backoff retry.
     */
    private suspend fun uploadWithRetry(
        storagePath: String,
        data: ByteArray,
        mimeType: String
    ): UploadResult {
        var lastError: Exception? = null

        for (attempt in 0 until MAX_RETRIES) {
            try {
                val ref = storage.reference.child(storagePath)
                val metadata = StorageMetadata.Builder()
                    .setContentType(mimeType)
                    .build()

                ref.putBytes(data, metadata).await()
                Log.d(TAG, "Upload successful: $storagePath (attempt ${attempt + 1})")
                return UploadResult.Success(storagePath)

            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Upload attempt ${attempt + 1} failed: ${e.message}")

                if (attempt < MAX_RETRIES - 1) {
                    val delay = RETRY_DELAYS_MS[attempt]
                    Log.d(TAG, "Retrying in ${delay}ms...")
                    kotlinx.coroutines.delay(delay)
                }
            }
        }

        return UploadResult.Error("Upload failed after $MAX_RETRIES attempts: ${lastError?.localizedMessage}")
    }

    /**
     * Compress an image to fit within size and dimension constraints.
     */
    private fun compressImage(
        bitmap: Bitmap,
        maxDimension: Int,
        quality: Int,
        maxSizeBytes: Int
    ): ByteArray {
        // Scale down if needed
        val scaledBitmap = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
            val scale = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }

        // Compress to JPEG/PNG
        val outputStream = ByteArrayOutputStream()
        var currentQuality = quality

        do {
            outputStream.reset()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, currentQuality, outputStream)
            currentQuality -= 5
        } while (outputStream.size() > maxSizeBytes && currentQuality > 10)

        Log.d(TAG, "Compressed image: ${outputStream.size()} bytes (quality: ${currentQuality + 5})")

        return outputStream.toByteArray()
    }

    /**
     * Check if a path is a local file path (not a cloud storage path).
     */
    fun isLocalPath(path: String): Boolean {
        return path.startsWith("/") && !path.startsWith("gs://") && !path.contains("://")
    }
}
