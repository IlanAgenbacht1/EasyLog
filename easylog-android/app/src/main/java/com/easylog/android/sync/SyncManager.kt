package com.easylog.android.sync

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.work.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Manages background sync operations using WorkManager.
 * Provides methods to start periodic sync, trigger manual sync, and observe sync status.
 */
class SyncManager(
    private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    /**
     * Initialize periodic background sync.
     * Should be called when app starts and user is logged in.
     */
    fun startPeriodicSync() {
        Log.d(TAG, "Starting periodic sync")

        val periodicRequest = SyncWorker.createPeriodicRequest()

        workManager.enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing if already scheduled
            periodicRequest
        )
    }

    /**
     * Stop all sync operations.
     * Should be called when user logs out.
     */
    fun stopAllSync() {
        Log.d(TAG, "Stopping all sync operations")
        // Cancel periodic sync
        workManager.cancelUniqueWork(SyncWorker.WORK_NAME)
        // Cancel any pending one-time sync requests by tag
        workManager.cancelAllWorkByTag(TAG_ONE_TIME_SYNC)
    }

    /**
     * Trigger an immediate one-time sync.
     * Returns a LiveData to observe the work status.
     */
    fun syncNow(): LiveData<WorkInfo?> {
        Log.d(TAG, "Triggering immediate sync")

        val oneTimeRequest = SyncWorker.createOneTimeRequest()

        workManager.enqueue(oneTimeRequest)

        return workManager.getWorkInfoByIdLiveData(oneTimeRequest.id)
    }

    /**
     * Get the current sync work info as Flow.
     */
    fun getSyncStatus(): Flow<SyncStatus> {
        return workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.WORK_NAME)
            .map { workInfos ->
                val workInfo = workInfos.firstOrNull()
                when (workInfo?.state) {
                    WorkInfo.State.RUNNING -> SyncStatus.SYNCING
                    WorkInfo.State.ENQUEUED -> SyncStatus.PENDING
                    WorkInfo.State.SUCCEEDED -> {
                        val syncedCount = workInfo.outputData.getInt(SyncWorker.KEY_SYNCED_COUNT, 0)
                        val failedCount = workInfo.outputData.getInt(SyncWorker.KEY_FAILED_COUNT, 0)
                        if (failedCount > 0) {
                            SyncStatus.PARTIAL_SUCCESS
                        } else {
                            SyncStatus.SUCCESS
                        }
                    }
                    WorkInfo.State.FAILED -> SyncStatus.FAILED
                    WorkInfo.State.BLOCKED -> SyncStatus.PENDING
                    WorkInfo.State.CANCELLED -> SyncStatus.IDLE
                    null -> SyncStatus.IDLE
                }
            }
    }

    /**
     * Check if there's an active sync running.
     */
    fun isSyncing(): LiveData<Boolean> {
        return workManager.getWorkInfosForUniqueWorkLiveData(SyncWorker.WORK_NAME).let { liveData ->
            object : LiveData<Boolean>() {
                init {
                    value = false
                }

                override fun onActive() {
                    super.onActive()
                    liveData.observeForever { workInfos ->
                        value = workInfos.any { it.state == WorkInfo.State.RUNNING }
                    }
                }
            }
        }
    }

    companion object {
        const val TAG = "SyncManager"
        private const val TAG_ONE_TIME_SYNC = "one_time_sync"
    }
}

enum class SyncStatus {
    IDLE,
    PENDING,
    SYNCING,
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILED
}
