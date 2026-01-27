package com.easylog.android.domain.model

import java.time.Instant

data class TimeLog(
    val localId: String,
    val serverId: String?,
    val engineerId: String,
    val engineerName: String,
    val customerId: String,
    val customerName: String,
    val travelTimeMinutes: Int,
    val timeStarted: Instant,
    val timeEnded: Instant?,
    val timeOnSiteMinutes: Int?,
    val workDescription: String?,
    val customerSignerName: String?,
    val signaturePath: String?,
    val signatureUrl: String?,
    val signedAt: Instant?,
    val status: TimeLogStatus,
    val isSynced: Boolean
) {
    val isActive: Boolean
        get() = status == TimeLogStatus.IN_PROGRESS

    val isCompleted: Boolean
        get() = status == TimeLogStatus.COMPLETED

    val totalTimeMinutes: Int
        get() = travelTimeMinutes + (timeOnSiteMinutes ?: 0)
}

enum class TimeLogStatus {
    IN_PROGRESS,
    COMPLETED;

    companion object {
        fun fromString(value: String): TimeLogStatus {
            return when (value.lowercase()) {
                "in_progress" -> IN_PROGRESS
                "completed" -> COMPLETED
                else -> IN_PROGRESS
            }
        }
    }

    fun toApiString(): String {
        return when (this) {
            IN_PROGRESS -> "in_progress"
            COMPLETED -> "completed"
        }
    }
}
