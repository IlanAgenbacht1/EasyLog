package com.easylog.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "engineers")
data class EngineerEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val email: String?,
    val phone: String?,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
