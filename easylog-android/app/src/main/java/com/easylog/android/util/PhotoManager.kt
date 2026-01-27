package com.easylog.android.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class PhotoManager(private val context: Context) {

    private val photosDir: File
        get() = File(context.filesDir, "photos").apply { mkdirs() }

    /**
     * Create a temporary file URI for camera capture
     */
    fun createTempPhotoUri(): Uri {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "PHOTO_${timestamp}_${UUID.randomUUID().toString().take(8)}.jpg"
        val file = File(context.cacheDir.resolve("photos").apply { mkdirs() }, fileName)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Save a photo from URI to permanent storage with compression
     */
    fun savePhoto(uri: Uri, jobLocalId: String): String? {
        Log.d("PhotoManager", "savePhoto called with uri: $uri, jobLocalId: $jobLocalId")
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e("PhotoManager", "Failed to open input stream for uri: $uri")
                return null
            }

            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bitmap == null) {
                Log.e("PhotoManager", "Failed to decode bitmap from uri: $uri")
                return null
            }
            Log.d("PhotoManager", "Decoded bitmap: ${bitmap.width}x${bitmap.height}")

            // Compress and resize if needed
            val scaledBitmap = scaleBitmapIfNeeded(bitmap, maxDimension = 1920)
            Log.d("PhotoManager", "Scaled bitmap: ${scaledBitmap.width}x${scaledBitmap.height}")

            // Create job-specific folder
            val jobPhotosDir = File(photosDir, jobLocalId).apply { mkdirs() }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "photo_${timestamp}_${UUID.randomUUID().toString().take(8)}.jpg"
            val file = File(jobPhotosDir, fileName)
            Log.d("PhotoManager", "Saving to: ${file.absolutePath}")

            FileOutputStream(file).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }

            if (bitmap != scaledBitmap) {
                scaledBitmap.recycle()
            }
            bitmap.recycle()

            Log.d("PhotoManager", "Photo saved successfully: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e("PhotoManager", "Error saving photo: ${e.message}", e)
            null
        }
    }

    /**
     * Scale bitmap if larger than max dimension
     */
    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }

        val ratio = minOf(
            maxDimension.toFloat() / width,
            maxDimension.toFloat() / height
        )

        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Delete a photo file
     */
    fun deletePhoto(path: String): Boolean {
        return try {
            File(path).delete()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Delete all photos for a job
     */
    fun deleteJobPhotos(jobLocalId: String): Boolean {
        return try {
            val jobPhotosDir = File(photosDir, jobLocalId)
            jobPhotosDir.deleteRecursively()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get photo file from path
     */
    fun getPhotoFile(path: String): File? {
        val file = File(path)
        return if (file.exists()) file else null
    }

    /**
     * Convert list of paths to JSON string for storage
     */
    fun pathsToJson(paths: List<String>): String {
        return Gson().toJson(paths)
    }

    /**
     * Convert JSON string back to list of paths
     */
    fun jsonToPaths(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        const val MAX_PHOTOS_PER_JOB = 20 // Soft limit for UX
    }
}
