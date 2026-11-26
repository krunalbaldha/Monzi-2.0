package com.krunal.monzi

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.krunal.monzi.data.ApiResult
import com.krunal.monzi.data.AuthSessionManager
import com.krunal.monzi.data.MonziRepository
import com.krunal.monzi.data.SupabaseApiClient
import com.krunal.monzi.data.SyncQueueItemEntity
import com.krunal.monzi.data.TransactionEntity
import java.util.concurrent.TimeUnit

class SyncWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val db = LocalFinanceDbHelper.getInstance(applicationContext)
        val sessionMgr = AuthSessionManager(applicationContext)
        val initialBatch = db.getPendingTransactions(BATCH_SIZE)
        val hasQueueWork = db.getActionableSyncQueueItems().isNotEmpty() ||
            db.getPendingSyncAccounts().isNotEmpty() ||
            sessionMgr.isPendingCloudReset()
        if (initialBatch.isEmpty() && !hasQueueWork) {
            return Result.success()
        }

        val api = SupabaseApiClient()
        var syncToken = sessionMgr.getDeviceSyncToken().trim()
        val accessToken = sessionMgr.getValidAccessToken(api)
        val userId = sessionMgr.getUserId()

        // Try fetching profile once if we have an active session but missing syncToken
        if (syncToken.isEmpty() && !accessToken.isNullOrBlank() && userId.isNotBlank()) {
            val profileRes = api.fetchOrProvisionProfile(accessToken, userId, sessionMgr.getSession()?.email.orEmpty())
            if (profileRes is ApiResult.Success) {
                sessionMgr.saveProfile(profileRes.data)
                syncToken = profileRes.data.deviceSyncToken.trim()
            }
        }

        if (syncToken.isEmpty() && accessToken.isNullOrBlank()) {
            Log.w("MonziSync", "Deferring queue sync: user not signed in yet")
            db.logAutomation(
                processType = "QUEUE_SYNC",
                processStatus = "DEFERRED",
                errorCode = "NO_AUTH_TOKEN",
                details = "${initialBatch.size} local transaction(s) / queued change(s) waiting for account sign-in"
            )
            return Result.success()
        }

        var totalSynced = 0
        var totalFailed = 0
        var shouldRetry = false
        var hadPriorRetries = false

        // When authenticated, flush pending reset, sync_queue (UPDATE/DELETE), accounts, categories, rules & txns.
        if (!accessToken.isNullOrBlank()) {
            val repo = MonziRepository(applicationContext)
            totalSynced += repo.flushPendingNow()
            if (sessionMgr.isPendingCloudReset() ||
                db.getActionableSyncQueueItems().any { it.syncStatus == "RETRY" || it.syncStatus == "PENDING" }
            ) {
                shouldRetry = true
            }
        }

        val deviceId = sessionMgr.getOrCreateDeviceIdentifier()
        val categories = db.getCategories()
        val categoryIdByName = categories.associate { it.name.lowercase() to it.id }

        var batchNumber = 0
        while (batchNumber < MAX_BATCHES_PER_RUN) {
            val batch = db.getPendingTransactions(BATCH_SIZE)
            if (batch.isEmpty()) break
            if (!accessToken.isNullOrBlank() && syncToken.isEmpty()) break
            batchNumber++

            if (batch.any { it.retryCount > 0 || it.syncStatus != LocalFinanceDbHelper.STATUS_PENDING }) {
                hadPriorRetries = true
            }

            for (row in batch) {
                val resolvedCategoryId = resolveCategoryId(row, categoryIdByName)
                val qId = "sync-txn-${row.localId}"
                val nowAttempt = System.currentTimeMillis()
                db.upsertSyncQueueItem(
                    SyncQueueItemEntity(
                        id = qId,
                        entityType = "TRANSACTION",
                        entityId = row.localId,
                        operation = "UPSERT",
                        payloadJson = "{\"amount\":${row.amount},\"type\":\"${row.transactionType}\"}",
                        syncStatus = "PROCESSING",
                        retryCount = row.retryCount,
                        lastAttemptAtMs = nowAttempt,
                        createdAtMs = row.createdAt
                    )
                )

                val outcome = if (syncToken.isNotEmpty()) {
                    api.deviceSyncIngestTransaction(
                        syncToken = syncToken,
                        deviceId = deviceId,
                        record = row,
                        categoryId = resolvedCategoryId
                    )
                } else {
                    // Fallback to direct authenticated table insert when syncToken isn't set
                    when (val res = api.insertManualTransaction(accessToken!!, row.copy(userId = userId, categoryId = resolvedCategoryId))) {
                        is ApiResult.Success -> ApiResult.Success("PROCESSED")
                        is ApiResult.Error -> res
                    }
                }

                when (outcome) {
                    is ApiResult.Success -> {
                        db.markSyncStatus(row.localId, LocalFinanceDbHelper.STATUS_SYNCED, null)
                        db.upsertSyncQueueItem(
                            SyncQueueItemEntity(
                                id = qId,
                                entityType = "TRANSACTION",
                                entityId = row.localId,
                                operation = "UPSERT",
                                payloadJson = "{\"amount\":${row.amount},\"type\":\"${row.transactionType}\"}",
                                syncStatus = "SYNCED",
                                retryCount = row.retryCount,
                                lastError = null,
                                lastAttemptAtMs = System.currentTimeMillis(),
                                createdAtMs = row.createdAt
                            )
                        )
                        totalSynced++
                    }
                    is ApiResult.Error -> {
                        val isRetryable = outcome.code == 401 || outcome.code == 403 || outcome.code == 0 || outcome.code >= 500
                        val nextStatus = if (isRetryable) LocalFinanceDbHelper.STATUS_RETRY_SCHEDULED else LocalFinanceDbHelper.STATUS_FAILED
                        db.markSyncStatus(row.localId, nextStatus, "HTTP_${outcome.code}")
                        db.upsertSyncQueueItem(
                            SyncQueueItemEntity(
                                id = qId,
                                entityType = "TRANSACTION",
                                entityId = row.localId,
                                operation = "UPSERT",
                                payloadJson = "{\"amount\":${row.amount},\"type\":\"${row.transactionType}\"}",
                                syncStatus = if (isRetryable) "RETRY" else "FAILED",
                                retryCount = row.retryCount + 1,
                                lastError = outcome.message.take(160),
                                lastAttemptAtMs = System.currentTimeMillis(),
                                createdAtMs = row.createdAt
                            )
                        )
                        totalFailed++
                        if (isRetryable) shouldRetry = true
                    }
                }
            }

            if (shouldRetry) break
        }

        if (totalSynced > 0) {
            db.updateSyncState(lastSyncAt = System.currentTimeMillis())
            db.logAutomation(
                processType = "QUEUE_SYNC",
                processStatus = "SYNCED",
                details = "Synced $totalSynced transaction(s) directly to Supabase"
            )
            val syncedIntent = Intent(ACTION_SMS_SYNCED).apply {
                setPackage(applicationContext.packageName)
            }
            applicationContext.sendBroadcast(syncedIntent)

            if (hadPriorRetries || totalSynced >= 2 || totalFailed > 0) {
                NotificationHelper.notifySyncSummary(applicationContext, totalSynced, totalFailed)
            }
        } else if (totalFailed > 0) {
            db.logAutomation(
                processType = "QUEUE_SYNC",
                processStatus = if (shouldRetry) "RETRY_SCHEDULED" else "FAILED",
                errorCode = "SYNC_PARTIAL_FAILURE",
                details = "$totalFailed transaction(s) scheduled for retry"
            )
        }

        return if (shouldRetry) Result.retry() else Result.success()
    }

    private fun resolveCategoryId(
        row: TransactionEntity,
        categoryIdByName: Map<String, String>
    ): String? {
        val currentId = row.categoryId
        if (!currentId.isNullOrBlank() && !currentId.startsWith("local-cat-")) {
            return currentId
        }
        val catName = row.categoryName?.lowercase()?.trim() ?: return null
        return categoryIdByName[catName]
            ?: categoryIdByName.entries.firstOrNull { it.key.contains(catName) || catName.contains(it.key) }?.value
    }

    companion object {
        const val PREFS_NAME = AuthSessionManager.PREFS_NAME
        const val ACTION_SMS_SYNCED = "com.krunal.monzi.SMS_SYNCED"

        private const val BATCH_SIZE = 50
        private const val MAX_BATCHES_PER_RUN = 10
        private const val UNIQUE_FLUSH_WORK_NAME = "monzi_pending_flush"
        private const val UNIQUE_PERIODIC_WORK_NAME = "monzi_periodic_retry_sweep"

        fun enqueuePendingFlush(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_FLUSH_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                workRequest
            )
        }

        fun schedulePeriodicRetry(context: Context) {
            val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        }
    }
}
