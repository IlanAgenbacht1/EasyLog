package com.easylog.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Entity for tracking pending file uploads to Firebase Storage.
 *
 * Architecture: This table is device-local only. It tracks which files need to be uploaded
 * to Firebase Storage. Once uploaded, the entry is deleted. This prevents leaking
 * device-local file paths into the shared Firestore database.
 */
@Entity(tableName = "pending_uploads")
@TypeConverters(StringListConverter::class)
data class PendingUploadEntity(
    @PrimaryKey
    val timeLogId: String,                  // Firestore document ID
    val localSignaturePath: String?,        // Local file path for signature (nullable)
    val localPhotoPaths: List<String>,      // Local file paths for photos
    val createdAt: Long                     // Timestamp when upload was queued
)

/**
 * Type converter for List<String> to JSON string.
 */
class StringListConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromString(value: String?): List<String> {
        if (value == null) return emptyList()
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun fromList(list: List<String>?): String {
        return gson.toJson(list ?: emptyList<String>())
    }
}
