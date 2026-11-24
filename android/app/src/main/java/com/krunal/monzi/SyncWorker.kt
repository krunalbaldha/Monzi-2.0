package com.krunal.monzi

import android.content.Context
import android.util.Log
import androidx.work.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class SyncWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val serverUrl = inputData.getString("server_url") ?: "http://10.0.2.2:3000/api/sync"
        val syncSecret = inputData.getString("sync_secret") ?: "monzi_personal_sync_secret"

        val amount = inputData.getDouble("amount", 0.0)
        val direction = inputData.getString("direction") ?: "DEBIT"
        val bankName = inputData.getString("bank_name") ?: "Bank"
        val accountMask = inputData.getString("account_mask") ?: "XX0000"
        val merchant = inputData.getString("merchant")
        val referenceId = inputData.getString("reference_id")
        val paymentMode = inputData.getString("payment_mode") ?: "UPI"
        val timestamp = inputData.getLong("timestamp", System.currentTimeMillis())

        val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).format(Date(timestamp))

        return try {
            val jsonPayload = JSONObject().apply {
                put("device_id", android.os.Build.MODEL)
                put("sync_secret", syncSecret)
                val txnArray = JSONArray().apply {
                    val txn = JSONObject().apply {
                        put("transaction_date", isoDate)
                        put("amount", amount)
                        put("direction", direction)
                        put("bank_name", bankName)
                        put("account_mask", accountMask)
                        merchant?.let { put("merchant", it) }
                        referenceId?.let { put("reference_id", it) }
                        put("payment_mode", paymentMode)
                    }
                    put(txn)
                }
                put("transactions", txnArray)
            }

            val url = URL(serverUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-monzi-secret", syncSecret)
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(jsonPayload.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                Log.i("MonziSync", "Sync successful (HTTP $responseCode)")
                Result.success()
            } else {
                Log.e("MonziSync", "Sync failed with HTTP $responseCode")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("MonziSync", "Network error during sync", e)
            Result.retry()
        }
    }

    companion object {
        fun enqueueSync(context: Context, parsed: ParsedSms) {
            val prefs = context.getSharedPreferences("monzi_prefs", Context.MODE_PRIVATE)
            val serverUrl = prefs.getString("server_url", "http://10.0.2.2:3000/api/sync")
            val secret = prefs.getString("sync_secret", "monzi_personal_sync_secret")

            val data = Data.Builder()
                .putString("server_url", serverUrl)
                .putString("sync_secret", secret)
                .putDouble("amount", parsed.amount)
                .putString("direction", parsed.direction)
                .putString("bank_name", parsed.bankName)
                .putString("account_mask", parsed.accountMask)
                .putString("merchant", parsed.merchant)
                .putString("reference_id", parsed.referenceId)
                .putString("payment_mode", parsed.paymentMode)
                .putLong("timestamp", parsed.timestamp)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(data)
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
