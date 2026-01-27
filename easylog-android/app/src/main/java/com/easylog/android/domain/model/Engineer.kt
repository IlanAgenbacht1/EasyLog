package com.easylog.android.domain.model

data class Engineer(
    val id: String,
    val name: String,
    val email: String?,
    val phone: String?,
    val isActive: Boolean
)
