package com.easylog.android.data.local.dao

import androidx.room.*
import com.easylog.android.data.local.entity.PendingUploadEntity

/**
 * DAO for pending file uploads.
 */
@Dao
interface PendingUploadDao {

    @Query("SELECT * FROM pending_uploads ORDER BY createdAt ASC")
    suspend fun getAllPendingUploads(): List<PendingUploadEntity>

    @Query("SELECT * FROM pending_uploads WHERE timeLogId = :timeLogId")
    suspend fun getPendingUpload(timeLogId: String): PendingUploadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingUpload(upload: PendingUploadEntity)

    @Update
    suspend fun updatePendingUpload(upload: PendingUploadEntity)

    @Query("DELETE FROM pending_uploads WHERE timeLogId = :timeLogId")
    suspend fun deletePendingUpload(timeLogId: String)

    @Query("DELETE FROM pending_uploads")
    suspend fun deleteAllPendingUploads()

    @Query("SELECT COUNT(*) FROM pending_uploads")
    suspend fun getPendingUploadCount(): Int
}
