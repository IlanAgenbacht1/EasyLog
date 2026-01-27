package com.easylog.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val address: String?,
    val phone: String?,
    val email: String?,
    val contactPerson: String?,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val isSynced: Boolean = false
)
