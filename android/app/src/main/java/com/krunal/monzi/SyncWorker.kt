package com.krunal.monzi

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class SyncWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val serverUrl = inputData.getString("server_url")
            ?: prefs.getString("server_url", DEFAULT_SYNC_URL)
            ?: DEFAULT_SYNC_URL
        val syncToken = (inputData.getString("device_sync_token")
            ?: prefs.getString("device_sync_token", "")
            ?: "").trim()
        val accessToken = (inputData.getString("access_token")
            ?: prefs.getString("access_token", "")
            ?: "").trim()
        val syncSecret = (inputData.getString("sync_secret")
            ?: prefs.getString("sync_secret", "")
            ?: "").trim()

        if (syncToken.isEmpty() && accessToken.isEmpty()) {
            Log.w("MonziSync", "Skipping sync: user not signed in / no sync token configured yet")
            return Result.failure()
        }

        val amount = inputData.getDouble("amount", 0.0)
        if (amount <= 0.0) {
            return Result.failure()
        }

        val direction = inputData.getString("direction") ?: "DEBIT"
        val bankName = inputData.getString("bank_name") ?: "Bank"
        val accountMask = inputData.getString("account_mask") ?: "XX0000"
        val merchant = inputData.getString("merchant")
        val referenceId = inputData.getString("reference_id")
        val paymentMode = inputData.getString("payment_mode") ?: "UPI"
        val transactionDateIso = inputData.getString("transaction_date_iso") ?: ""
        val transactionType = inputData.getString("transaction_type") ?: ""
        val isSelfTransfer = inputData.getBoolean("is_self_transfer", false)
        val isRefund = inputData.getBoolean("is_refund", false)
        val sender = inputData.getString("sender") ?: ""
        val hasBalance = inputData.getBoolean("has_available_balance", false)
        val availableBalance = inputData.getDouble("parsed_available_balance", 0.0)

        return try {
            val jsonPayload = JSONObject().apply {
                put("device_id", "android-${android.os.Build.MODEL}")
                if (syncToken.isNotEmpty()) put("device_sync_token", syncToken)
                if (syncSecret.isNotEmpty()) put("sync_secret", syncSecret)

                val txnArray = JSONArray().apply {
                    val txn = JSONObject().apply {
                        if (transactionDateIso.isNotEmpty()) {
                            put("transaction_date", transactionDateIso)
                        }
                        put("amount", amount)
                        put("direction", direction)
                        put("bank_name", bankName)
                        put("account_mask", accountMask)
                        if (!merchant.isNullOrBlank()) put("merchant", merchant)
                        if (!referenceId.isNullOrBlank()) put("reference_id", referenceId)
                        put("payment_mode", paymentMode)
                        if (transactionType.isNotEmpty()) put("transaction_type", transactionType)
                        if (isSelfTransfer) put("is_self_transfer", true)
                        if (isRefund) put("is_refund", true)
                        if (sender.isNotEmpty()) put("sender", sender)
                        if (hasBalance) put("parsed_available_balance", availableBalance)
                    }
                    put(txn)
                }
                put("transactions", txnArray)
            }

            val url = URL(serverUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                if (syncToken.isNotEmpty()) {
                    setRequestProperty("x-monzi-sync-token", syncToken)
                }
                if (accessToken.isNotEmpty()) {
                    setRequestProperty("Authorization", "Bearer $accessToken")
                }
                if (syncSecret.isNotEmpty()) {
                    setRequestProperty("x-monzi-secret", syncSecret)
                }
                doOutput = true
                connectTimeout = 12000
                readTimeout = 12000
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(jsonPayload.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            when {
                responseCode in 200..299 -> {
                    Log.i("MonziSync", "Sync successful (HTTP $responseCode)")
                    val syncedIntent = Intent(ACTION_SMS_SYNCED).apply {
                        setPackage(applicationContext.packageName)
                    }
                    applicationContext.sendBroadcast(syncedIntent)
                    Result.success()
                }
                responseCode in 400..499 -> {
                    Log.e("MonziSync", "Sync rejected with HTTP $responseCode")
                    Result.failure()
                }
                else -> {
                    Log.e("MonziSync", "Sync failed with HTTP $responseCode, scheduling retry")
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Log.e("MonziSync", "Network error during sync", e)
            Result.retry()
        }
    }

    companion object {
        const val PREFS_NAME = "monzi_prefs"
        const val DEFAULT_SYNC_URL = "https://ara20.vercel.app/api/sync"
        const val ACTION_SMS_SYNCED = "com.krunal.monzi.SMS_SYNCED"

        fun enqueueSync(context: Context, parsed: ParsedSms, sender: String = "") {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val serverUrl = prefs.getString("server_url", DEFAULT_SYNC_URL) ?: DEFAULT_SYNC_URL
            val syncToken = prefs.getString("device_sync_token", "") ?: ""
            val accessToken = prefs.getString("access_token", "") ?: ""
            val syncSecret = prefs.getString("sync_secret", "") ?: ""

            val builder = Data.Builder()
                .putString("server_url", serverUrl)
                .putString("device_sync_token", syncToken)
                .putString("access_token", accessToken)
                .putString("sync_secret", syncSecret)
                .putDouble("amount", parsed.amount)
                .putString("direction", parsed.direction)
                .putString("bank_name", parsed.bankName)
                .putString("account_mask", parsed.accountMask)
                .putString("merchant", parsed.merchant)
                .putString("reference_id", parsed.referenceId)
                .putString("payment_mode", parsed.paymentMode)
                .putString("transaction_date_iso", parsed.transactionDateIso)
                .putString("transaction_type", parsed.transactionType)
                .putBoolean("is_self_transfer", parsed.isSelfTransfer)
                .putBoolean("is_refund", parsed.isRefund)
                .putString("sender", sender)
                .putLong("timestamp", parsed.timestamp)

            if (parsed.parsedAvailableBalance != null) {
                builder.putBoolean("has_available_balance", true)
                builder.putDouble("parsed_available_balance", parsed.parsedAvailableBalance)
            } else {
                builder.putBoolean("has_available_balance", false)
            }

            val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(builder.build())
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
