package com.easylog.android.domain.model

data class Customer(
    val id: String,
    val name: String,
    val address: String?,
    val phone: String?,
    val email: String?,
    val contactPerson: String?,
    val isActive: Boolean
)
