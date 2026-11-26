package com.krunal.monzi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import com.krunal.monzi.data.AuthSessionManager
import com.krunal.monzi.data.SmsImportRunEntity
import com.krunal.monzi.data.SmsParserFailureEntity
import com.krunal.monzi.data.TransactionEntity
import com.krunal.monzi.data.engines.DuplicateDetector
import com.krunal.monzi.data.engines.PostIngestProcessor
import com.krunal.monzi.data.engines.ReconciliationEngine
import com.krunal.monzi.data.engines.RefundDetector
import com.krunal.monzi.data.engines.TransferDetector
import com.krunal.monzi.util.IstTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

data class InboxScanResult(
    val scanned: Int,
    val newlyQueued: Int,
    val financialDetected: Int = 0,
    val duplicateSkipped: Int = 0,
    val failureCount: Int = 0,
    val ignoredCount: Int = 0,
    val status: String = "COMPLETED", // "COMPLETED", "FAILED", "PARTIAL"
    val dateRangeLabel: String = "All Dates",
    val alreadyRunning: Boolean = false,
    val permissionMissing: Boolean = false,
    val error: String? = null
)

data class ReprocessResult(
    val totalExamined: Int = 0,
    val updatedCount: Int = 0,
    val unchangedCount: Int = 0,
    val reclassifiedCount: Int = 0,
    val duplicateCount: Int = 0,
    val newlyDiscovered: Int = 0,
    val status: String = "COMPLETED",
    val error: String? = null
)

object SmsInboxScanner {

    private const val SCAN_BATCH_SIZE = 50
    private val isRunning = AtomicBoolean(false)

    fun hasSmsPermissions(context: Context): Boolean {
        val hasReceive = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        val hasRead = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        return hasReceive && hasRead
    }

    /**
     * Preview count of native SMS messages inside selected date range (FRD Section 3).
     */
    fun previewCount(
        context: Context,
        startDateMs: Long? = null,
        endDateMs: Long? = null
    ): Int {
        val appContext = context.applicationContext
        if (!hasSmsPermissions(appContext)) return 0
        return try {
            val uri = Uri.parse("content://sms/inbox")
            val (selection, selectionArgs) = buildDateSelection(startDateMs, endDateMs, cursorTs = 0L)
            val cursor = appContext.contentResolver.query(
                uri,
                arrayOf("_id"),
                selection,
                selectionArgs,
                null
            )
            cursor?.use { it.count } ?: 0
        } catch (e: Exception) {
            Log.w("MonziHistoryScan", "Could not preview SMS count", e)
            0
        }
    }

    private fun buildDateSelection(
        startDateMs: Long?,
        endDateMs: Long?,
        cursorTs: Long
    ): Pair<String?, Array<String>?> {
        return when {
            startDateMs != null && endDateMs != null -> {
                val s = minOf(startDateMs, endDateMs)
                val e = maxOf(startDateMs, endDateMs)
                "date >= ? AND date <= ?" to arrayOf(s.toString(), e.toString())
            }
            startDateMs != null -> {
                "date >= ?" to arrayOf(startDateMs.toString())
            }
            cursorTs > 0L -> {
                "date > ?" to arrayOf(cursorTs.toString())
            }
            else -> null to null
        }
    }

    /**
     * Scan SMS messages within an optional date range or from last processed cursor (FRD Section 4).
     */
    fun scanBlocking(
        context: Context,
        startDateMs: Long? = null,
        endDateMs: Long? = null,
        limit: Int = 1000,
        fullRescan: Boolean = false,
        dateRangeLabel: String = "Selected Range"
    ): InboxScanResult {
        val appContext = context.applicationContext
        if (!hasSmsPermissions(appContext)) {
            LocalFinanceDbHelper.getInstance(appContext).updateSyncState(monitoringStatus = "INACTIVE")
            return InboxScanResult(0, 0, permissionMissing = true, status = "FAILED", error = "SMS permission missing")
        }
        if (!isRunning.compareAndSet(false, true)) {
            return InboxScanResult(0, 0, alreadyRunning = true, status = "RUNNING", error = "Another scan is already running")
        }

        val db = LocalFinanceDbHelper.getInstance(appContext)
        val sessionMgr = AuthSessionManager(appContext)
        val userId = sessionMgr.getUserId()
        val deviceId = sessionMgr.getOrCreateDeviceIdentifier()
        val safeLimit = limit.coerceIn(1, 3000)

        var scanned = 0
        var financialDetected = 0
        var newlyQueued = 0
        var duplicateSkipped = 0
        var ignoredCount = 0
        var reviewCount = 0
        var failureCount = 0

        val runId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        var currentRun = SmsImportRunEntity(
            id = runId,
            deviceId = deviceId,
            status = "RUNNING",
            startedAtMs = startedAt
        )

        data class ScannedCandidate(
            val inspection: SmsInspectionResult,
            val sender: String,
            val timestamp: Long,
            val rawBody: String
        )

        return try {
            val syncState = db.getSyncState()
            val isCustomRange = startDateMs != null && endDateMs != null
            val cursorTs = if (fullRescan || isCustomRange) 0L else syncState.lastProcessedSmsTs

            db.upsertImportRun(currentRun.copy(checkpointTs = cursorTs))
            db.updateSyncState(historicalScanStatus = LocalFinanceDbHelper.SCAN_IN_PROGRESS)
            db.logAutomation(
                processType = "HISTORICAL_SCAN",
                processStatus = "RUNNING",
                details = "Scanning up to $safeLimit SMS (range=$dateRangeLabel, start=$startDateMs, end=$endDateMs, cursor=$cursorTs)"
            )

            val uri = Uri.parse("content://sms/inbox")
            val (selection, selectionArgs) = buildDateSelection(startDateMs, endDateMs, cursorTs)
            val sortOrder = if (isCustomRange || cursorTs > 0L) "date ASC" else "date DESC"

            val candidates = ArrayList<ScannedCandidate>()
            val cursor: Cursor? = appContext.contentResolver.query(
                uri,
                arrayOf("body", "address", "date"),
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                val bodyIdx = it.getColumnIndex("body")
                val senderIdx = it.getColumnIndex("address")
                val dateIdx = it.getColumnIndex("date")

                while (it.moveToNext() && scanned < safeLimit) {
                    scanned++
                    val body = if (bodyIdx >= 0) it.getString(bodyIdx) ?: "" else ""
                    val sender = if (senderIdx >= 0) it.getString(senderIdx) ?: "" else ""
                    val date = if (dateIdx >= 0) it.getLong(dateIdx) else System.currentTimeMillis()
                    val inspection = BankSmsParser.inspect(sender, body, date)
                    candidates.add(ScannedCandidate(inspection, sender, date, body))
                }
            }

            val orderedCandidates = if (isCustomRange || cursorTs > 0L) candidates else candidates.asReversed()
            var maxProcessedTs = cursorTs
            var batchCount = 0

            for (item in orderedCandidates) {
                val insp = item.inspection
                val stmt = insp.statement
                if (stmt != null) {
                    PostIngestProcessor.applyCreditCardStatement(db, stmt, userId)
                }

                val parsed = insp.parsed
                if (parsed != null) {
                    financialDetected++
                    val fingerprint = BankSmsParser.computeFingerprint(parsed)
                    val savedTxn = db.insertPendingTransactionReturningEntity(
                        parsed = parsed,
                        sender = item.sender,
                        transactionSource = LocalFinanceDbHelper.SOURCE_HISTORICAL_SMS,
                        smsFingerprint = fingerprint,
                        userId = userId,
                        rawBody = item.rawBody
                    )
                    if (savedTxn != null) {
                        newlyQueued++
                        PostIngestProcessor.processTransaction(appContext, db, savedTxn, userId)
                        if (savedTxn.confidenceScore < 0.70 || savedTxn.categoryName == "Uncategorized") {
                            reviewCount++
                        }
                    } else {
                        duplicateSkipped++
                    }
                } else if (insp.status == "FAILED") {
                    failureCount++
                    db.insertParserFailure(
                        SmsParserFailureEntity(
                            id = UUID.randomUUID().toString(),
                            deviceId = deviceId,
                            sender = item.sender.take(24).ifBlank { null },
                            errorReason = insp.reason ?: "Failed/declined SMS notice",
                            occurredAtMs = item.timestamp
                        )
                    )
                } else {
                    ignoredCount++
                }

                if (item.timestamp > maxProcessedTs) {
                    maxProcessedTs = item.timestamp
                }
                batchCount++
                if (batchCount % SCAN_BATCH_SIZE == 0) {
                    if (!isCustomRange) {
                        db.updateSyncState(lastProcessedSmsTs = maxProcessedTs)
                    }
                    currentRun = currentRun.copy(
                        totalExamined = batchCount,
                        financialDetected = financialDetected,
                        importedCount = newlyQueued,
                        duplicateSkipped = duplicateSkipped,
                        ignoredCount = ignoredCount,
                        reviewCount = reviewCount,
                        failureCount = failureCount,
                        checkpointTs = maxProcessedTs
                    )
                    db.upsertImportRun(currentRun)
                }
            }

            // Always recalculate account balances after scan
            ReconciliationEngine.recalculateAllAccountBalances(db)

            val finalStatus = if (failureCount > 0 && newlyQueued == 0) "PARTIAL" else "COMPLETED"
            currentRun = currentRun.copy(
                status = finalStatus,
                totalExamined = scanned,
                financialDetected = financialDetected,
                importedCount = newlyQueued,
                duplicateSkipped = duplicateSkipped,
                ignoredCount = ignoredCount,
                reviewCount = reviewCount,
                failureCount = failureCount,
                checkpointTs = maxProcessedTs,
                completedAtMs = System.currentTimeMillis()
            )
            db.upsertImportRun(currentRun)

            db.updateSyncState(
                historicalScanStatus = finalStatus,
                lastProcessedSmsTs = if (!isCustomRange && maxProcessedTs > syncState.lastProcessedSmsTs) maxProcessedTs else syncState.lastProcessedSmsTs,
                scannedCount = syncState.scannedCount + scanned,
                detectedCount = syncState.detectedCount + newlyQueued,
                monitoringStatus = "ACTIVE"
            )
            db.logAutomation(
                processType = "HISTORICAL_SCAN",
                processStatus = finalStatus,
                details = "Examined $scanned SMS ($dateRangeLabel): imported $newlyQueued, skipped $duplicateSkipped duplicate(s), ignored $ignoredCount, failed $failureCount"
            )

            if (db.getPendingCount() > 0) {
                try {
                    SyncWorker.enqueuePendingFlush(appContext)
                } catch (e: Exception) {
                    Log.w("MonziHistoryScan", "Could not schedule background flush immediately", e)
                }
            }

            InboxScanResult(
                scanned = scanned,
                newlyQueued = newlyQueued,
                financialDetected = financialDetected,
                duplicateSkipped = duplicateSkipped,
                failureCount = failureCount,
                ignoredCount = ignoredCount,
                status = finalStatus,
                dateRangeLabel = dateRangeLabel
            )
        } catch (e: Exception) {
            Log.e("MonziHistoryScan", "Error scanning SMS inbox", e)
            db.upsertImportRun(
                currentRun.copy(
                    status = "FAILED",
                    totalExamined = scanned,
                    financialDetected = financialDetected,
                    importedCount = newlyQueued,
                    duplicateSkipped = duplicateSkipped,
                    ignoredCount = ignoredCount,
                    reviewCount = reviewCount,
                    failureCount = failureCount + 1,
                    completedAtMs = System.currentTimeMillis()
                )
            )
            db.updateSyncState(historicalScanStatus = "FAILED")
            db.logAutomation(
                processType = "HISTORICAL_SCAN",
                processStatus = "FAILED",
                errorCode = "SCAN_EXCEPTION",
                details = e.message ?: "Unknown scan failure"
            )
            InboxScanResult(
                scanned = scanned,
                newlyQueued = newlyQueued,
                financialDetected = financialDetected,
                duplicateSkipped = duplicateSkipped,
                failureCount = failureCount + 1,
                ignoredCount = ignoredCount,
                status = "FAILED",
                dateRangeLabel = dateRangeLabel,
                error = e.message
            )
        } finally {
            isRunning.set(false)
        }
    }

    /**
     * Reprocess already synchronized transactions within selected date range (FRD Section 7).
     * Recalculates transactions with updated logic, re-evaluates duplicate detection and categories,
     * recalculates account balances, and preserves transaction identity.
     */
    fun reprocessBlocking(
        context: Context,
        startDateMs: Long,
        endDateMs: Long
    ): ReprocessResult {
        val appContext = context.applicationContext
        if (!isRunning.compareAndSet(false, true)) {
            return ReprocessResult(status = "FAILED", error = "Another sync or reprocess is already running")
        }

        val db = LocalFinanceDbHelper.getInstance(appContext)
        val sessionMgr = AuthSessionManager(appContext)
        val userId = sessionMgr.getUserId()
        val s = minOf(startDateMs, endDateMs)
        val e = maxOf(startDateMs, endDateMs)

        var totalExamined = 0
        var updatedCount = 0
        var unchangedCount = 0
        var reclassifiedCount = 0
        var duplicateCount = 0
        var newlyDiscovered = 0

        return try {
            val rangeLabel = "${IstTime.formatDateOnly(s)} – ${IstTime.formatDateOnly(e)}"
            db.logAutomation(
                processType = "REPROCESS_RANGE",
                processStatus = "RUNNING",
                details = "Reprocessing transactions for $rangeLabel (start=$s, end=$e)"
            )

            val existingTxns = db.getTransactionsInRange(s, e)
            val allTxns = db.getAllTransactions(5000)
            val accounts = db.getAccounts()

            for (tx in existingTxns) {
                totalExamined++
                var isModified = false
                var newCatId = tx.categoryId
                var newCatName = tx.categoryName
                var newCatColor = tx.categoryColor
                var newMerchant = tx.merchant
                var newTxType = tx.transactionType

                // 1. Re-inspect raw body / notes if available
                val bodyText = tx.notes.orEmpty()
                if (bodyText.isNotBlank()) {
                    val inspection = BankSmsParser.inspect(tx.sender ?: "", bodyText, tx.transactionDateMs)
                    val parsed = inspection.parsed
                    if (parsed != null) {
                        if (parsed.merchant != null && parsed.merchant != tx.merchant) {
                            newMerchant = parsed.merchant
                            isModified = true
                        }
                        // Re-resolve category with updated rules
                        val resolvedCat = db.resolveCategoryForSms(parsed, bodyText)
                        if (resolvedCat != null && resolvedCat.id != tx.categoryId) {
                            newCatId = resolvedCat.id
                            newCatName = resolvedCat.name
                            newCatColor = resolvedCat.color
                            reclassifiedCount++
                            isModified = true
                        }
                    }
                }

                // 2. Re-evaluate duplicate status
                val otherTxns = allTxns.filter { it.localId != tx.localId }
                val dupResult = DuplicateDetector.evaluateDuplicate(tx, otherTxns)
                val newDupStatus = dupResult.status
                if (newDupStatus != tx.duplicateStatus) {
                    db.updateTransactionAutomationFlags(
                        localId = tx.localId,
                        duplicateStatus = newDupStatus,
                        linkedTransactionId = dupResult.linkedTransactionId
                    )
                    if (newDupStatus == "EXACT_DUPLICATE" || newDupStatus == "POSSIBLE_DUPLICATE") {
                        duplicateCount++
                    }
                    isModified = true
                }

                // 3. Re-evaluate transfer status
                val transferOutcome = TransferDetector.detectTransfer(tx, allTxns, accounts, userId)
                if (transferOutcome != null && tx.transferStatus != transferOutcome.transferRecord.status) {
                    db.upsertTransfer(transferOutcome.transferRecord)
                    db.updateTransactionAutomationFlags(
                        localId = tx.localId,
                        transferStatus = transferOutcome.transferRecord.status,
                        isSelfTransfer = true,
                        isExcludedFromTotals = true
                    )
                    isModified = true
                }

                // 4. Re-evaluate refund status
                if (tx.direction == "CREDIT" && (tx.isRefund || tx.transactionType == "REFUND")) {
                    val refundOutcome = RefundDetector.detectRefund(tx, allTxns, userId)
                    if (refundOutcome != null && tx.refundStatus != refundOutcome.refundRecord.status) {
                        db.upsertRefund(refundOutcome.refundRecord)
                        db.updateTransactionAutomationFlags(
                            localId = tx.localId,
                            refundStatus = refundOutcome.refundRecord.status,
                            linkedTransactionId = refundOutcome.matchedOriginalTransaction?.localId
                        )
                        isModified = true
                    }
                }

                if (isModified) {
                    db.updateTransactionLocal(
                        localId = tx.localId,
                        categoryId = newCatId,
                        categoryName = newCatName,
                        categoryColor = newCatColor,
                        notes = tx.notes,
                        merchant = newMerchant,
                        transactionType = newTxType
                    )
                    updatedCount++
                } else {
                    unchangedCount++
                }
            }

            // 5. Scan native SMS inbox for any missed financial SMS in this date range
            if (hasSmsPermissions(appContext)) {
                try {
                    val uri = Uri.parse("content://sms/inbox")
                    val (sel, selArgs) = buildDateSelection(s, e, cursorTs = 0L)
                    val existingFingerprints = allTxns.mapNotNull { it.smsFingerprint }.toHashSet()

                    appContext.contentResolver.query(uri, arrayOf("body", "address", "date"), sel, selArgs, "date ASC")?.use { cursor ->
                        val bIdx = cursor.getColumnIndex("body")
                        val aIdx = cursor.getColumnIndex("address")
                        val dIdx = cursor.getColumnIndex("date")

                        while (cursor.moveToNext()) {
                            val body = if (bIdx >= 0) cursor.getString(bIdx) ?: "" else ""
                            val sender = if (aIdx >= 0) cursor.getString(aIdx) ?: "" else ""
                            val date = if (dIdx >= 0) cursor.getLong(dIdx) else System.currentTimeMillis()
                            val insp = BankSmsParser.inspect(sender, body, date)
                            val parsed = insp.parsed
                            if (parsed != null) {
                                val fp = BankSmsParser.computeFingerprint(parsed)
                                if (!existingFingerprints.contains(fp)) {
                                    val saved = db.insertPendingTransactionReturningEntity(
                                        parsed = parsed,
                                        sender = sender,
                                        transactionSource = LocalFinanceDbHelper.SOURCE_HISTORICAL_SMS,
                                        smsFingerprint = fp,
                                        userId = userId,
                                        rawBody = body
                                    )
                                    if (saved != null) {
                                        newlyDiscovered++
                                        existingFingerprints.add(fp)
                                        PostIngestProcessor.processTransaction(appContext, db, saved, userId)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("MonziReprocess", "Error checking native inbox during reprocess", e)
                }
            }

            // 6. Recalculate account balances and analytics
            ReconciliationEngine.recalculateAllAccountBalances(db)

            db.logAutomation(
                processType = "REPROCESS_RANGE",
                processStatus = "COMPLETED",
                details = "Reprocessed $totalExamined transactions in $rangeLabel: $updatedCount updated, $unchangedCount unchanged, $reclassifiedCount reclassified, $newlyDiscovered newly imported"
            )

            // 7. Flush pending queue to cloud if changes were made
            if (db.getPendingCount() > 0) {
                try {
                    SyncWorker.enqueuePendingFlush(appContext)
                } catch (_: Exception) {}
            }

            ReprocessResult(
                totalExamined = totalExamined,
                updatedCount = updatedCount,
                unchangedCount = unchangedCount,
                reclassifiedCount = reclassifiedCount,
                duplicateCount = duplicateCount,
                newlyDiscovered = newlyDiscovered,
                status = "COMPLETED"
            )
        } catch (e: Exception) {
            Log.e("MonziReprocess", "Error during date range reprocessing", e)
            db.logAutomation(
                processType = "REPROCESS_RANGE",
                processStatus = "FAILED",
                errorCode = "REPROCESS_EXCEPTION",
                details = e.message ?: "Unknown reprocess error"
            )
            ReprocessResult(
                totalExamined = totalExamined,
                updatedCount = updatedCount,
                unchangedCount = unchangedCount,
                status = "FAILED",
                error = e.message
            )
        } finally {
            isRunning.set(false)
        }
    }
}
