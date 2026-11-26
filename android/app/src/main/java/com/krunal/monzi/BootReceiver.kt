package com.krunal.monzi

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val appContext = context.applicationContext
        val pendingResult: BroadcastReceiver.PendingResult? = goAsync()

        executor.execute {
            try {
                NotificationHelper.createNotificationChannel(appContext)
                val db = LocalFinanceDbHelper.getInstance(appContext)
                val prefs = appContext.getSharedPreferences(SyncWorker.PREFS_NAME, Context.MODE_PRIVATE)

                val hasReceive = ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.RECEIVE_SMS
                ) == PackageManager.PERMISSION_GRANTED
                val hasRead = ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.READ_SMS
                ) == PackageManager.PERMISSION_GRANTED
                val hasSmsPermissions = hasReceive && hasRead

                val wasPreviouslyGranted = prefs.getBoolean("sms_was_granted", false)

                if (hasSmsPermissions) {
                    db.updateSyncState(monitoringStatus = "ACTIVE")
                    db.logAutomation(
                        processType = "BOOT_RESUME",
                        processStatus = "ACTIVE",
                        details = "Resumed SMS monitoring on $action"
                    )
                } else if (wasPreviouslyGranted) {
                    db.updateSyncState(monitoringStatus = "PERMISSION_REVOKED")
                    db.logAutomation(
                        processType = "BOOT_RESUME",
                        processStatus = "PERMISSION_REVOKED",
                        errorCode = "SMS_PERMISSION_REVOKED",
                        details = "SMS permissions revoked before $action"
                    )
                    NotificationHelper.notifyPermissionRevoked(appContext)
                } else {
                    db.updateSyncState(monitoringStatus = "INACTIVE")
                    db.logAutomation(
                        processType = "BOOT_RESUME",
                        processStatus = "INACTIVE",
                        details = "SMS permissions not yet granted on $action"
                    )
                }

                // Always arm the 15-minute offline recovery sweep
                try {
                    SyncWorker.schedulePeriodicRetry(appContext)

                    // Immediately flush any pending local transactions queued before reboot/update
                    val pendingCount = db.getPendingCount()
                    if (pendingCount > 0) {
                        Log.i("MonziBoot", "Resuming $pendingCount pending local transaction(s)")
                        SyncWorker.enqueuePendingFlush(appContext)
                    }
                } catch (e: Exception) {
                    Log.w("MonziBoot", "WorkManager not yet ready during $action", e)
                }
            } catch (e: Exception) {
                Log.e("MonziBoot", "Error during boot resume", e)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        private val executor = Executors.newSingleThreadExecutor()
    }
}
