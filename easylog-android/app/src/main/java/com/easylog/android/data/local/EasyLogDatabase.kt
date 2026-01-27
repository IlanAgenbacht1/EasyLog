package com.easylog.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.easylog.android.data.local.dao.CustomerDao
import com.easylog.android.data.local.dao.EngineerDao
import com.easylog.android.data.local.dao.PendingUploadDao
import com.easylog.android.data.local.entity.CustomerEntity
import com.easylog.android.data.local.entity.EngineerEntity
import com.easylog.android.data.local.entity.PendingUploadEntity

@Database(
    entities = [
        EngineerEntity::class,
        CustomerEntity::class,
        PendingUploadEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class EasyLogDatabase : RoomDatabase() {

    abstract fun engineerDao(): EngineerDao
    abstract fun customerDao(): CustomerDao
    abstract fun pendingUploadDao(): PendingUploadDao

    companion object {
        const val DATABASE_NAME = "easylog_database"
    }
}
