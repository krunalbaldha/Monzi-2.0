package com.krunal.monzi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.krunal.monzi.data.AuthSessionManager
import com.krunal.monzi.data.SmsLogEntity
import com.krunal.monzi.data.SmsParserFailureEntity
import com.krunal.monzi.data.engines.PostIngestProcessor
import java.util.UUID
import java.util.concurrent.Executors

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Reassemble multi-part SMS from the same originating address
        data class GroupedSms(var body: StringBuilder, var timestamp: Long)
        val grouped = LinkedHashMap<String, GroupedSms>()

        for (sms in messages) {
            val sender = sms.displayOriginatingAddress ?: "UNKNOWN"
            val part = sms.displayMessageBody ?: ""
            val ts = sms.timestampMillis
            val existing = grouped[sender]
            if (existing == null) {
                grouped[sender] = GroupedSms(StringBuilder(part), ts)
            } else {
                existing.body.append(part)
                if (ts > existing.timestamp) existing.timestamp = ts
            }
        }

        val appContext = context.applicationContext
        val pendingResult: BroadcastReceiver.PendingResult? = goAsync()

        executor.execute {
            try {
                val db = LocalFinanceDbHelper.getInstance(appContext)
                val sessionMgr = AuthSessionManager(appContext)
                val userId = sessionMgr.getUserId()
                val deviceId = sessionMgr.getOrCreateDeviceIdentifier()
                var newlyInsertedCount = 0

                for ((sender, smsData) in grouped) {
                    val rawBody = smsData.body.toString()
                    // Parse strictly in memory; never persist raw SMS body
                    val inspection = BankSmsParser.inspect(sender, rawBody, smsData.timestamp)

                    inspection.statement?.let { stmt ->
                        PostIngestProcessor.applyCreditCardStatement(db, stmt, userId)
                    }

                    val parsed = inspection.parsed
                    if (parsed != null) {
                        val fingerprint = BankSmsParser.computeFingerprint(parsed)
                        val savedTxn = db.insertPendingTransactionReturningEntity(
                            parsed = parsed,
                            sender = sender,
                            transactionSource = LocalFinanceDbHelper.SOURCE_INCOMING_SMS,
                            smsFingerprint = fingerprint,
                            userId = userId,
                            rawBody = rawBody
                        )

                        if (savedTxn != null) {
                            newlyInsertedCount++
                            PostIngestProcessor.processTransaction(appContext, db, savedTxn, userId)
                            Log.i(
                                "MonziReceiver",
                                "Financial SMS persisted locally: ${parsed.bankName} ${parsed.direction}"
                            )
                            db.updateSyncState(monitoringStatus = "ACTIVE")
                            db.logAutomation(
                                processType = "INCOMING_SMS",
                                processStatus = "LOCAL_QUEUED",
                                details = "${parsed.bankName} ${parsed.direction} saved locally"
                            )
                            NotificationHelper.notifyTransactionProcessed(
                                appContext,
                                parsed.bankName
                            )
                        } else {
                            db.logSmsProcessing(
                                SmsLogEntity(
                                    id = UUID.randomUUID().toString(),
                                    deviceId = deviceId,
                                    sender = sender,
                                    status = "DUPLICATE",
                                    reason = "Duplicate fingerprint ignored locally",
                                    extractedType = parsed.transactionType,
                                    extractedAmount = parsed.amount,
                                    extractedBank = parsed.bankName,
                                    extractedDateIso = parsed.transactionDateIso,
                                    referenceId = parsed.referenceId,
                                    smsFingerprint = fingerprint
                                )
                            )
                        }
                    } else if (looksFinancialSenderOrText(sender, rawBody)) {
                        db.logSmsProcessing(
                            SmsLogEntity(
                                id = UUID.randomUUID().toString(),
                                deviceId = deviceId,
                                sender = sender,
                                status = inspection.status,
                                reason = inspection.reason ?: "Filtered non-transaction SMS"
                            )
                        )
                        if (inspection.status == "FAILED") {
                            db.insertParserFailure(
                                SmsParserFailureEntity(
                                    id = UUID.randomUUID().toString(),
                                    deviceId = deviceId,
                                    sender = sender.take(24),
                                    errorReason = inspection.reason ?: "Parser could not extract transaction"
                                )
                            )
                        }
                    }
                }

                if (newlyInsertedCount > 0 || db.getPendingCount() > 0) {
                    try {
                        SyncWorker.enqueuePendingFlush(appContext)
                    } catch (e: Exception) {
                        Log.w("MonziReceiver", "Could not schedule WorkManager flush immediately", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("MonziReceiver", "Failed to process incoming SMS locally", e)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    private fun looksFinancialSenderOrText(sender: String, body: String): Boolean {
        val hay = "$sender $body".lowercase()
        return hay.contains("bank") || hay.contains("upi") || hay.contains("a/c") ||
            hay.contains("card") || hay.contains("rs.") || hay.contains("inr") || hay.contains("₹")
    }

    companion object {
        private val executor = Executors.newSingleThreadExecutor()
    }
}
