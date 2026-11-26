package com.krunal.monzi

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicInteger

object NotificationHelper {

    const val CHANNEL_ID = "monzi_finance_alerts"
    private const val CHANNEL_NAME = "Monzi Finance Alerts"
    private const val CHANNEL_DESC = "Privacy-safe transaction capture and background sync alerts"

    private const val ID_PERMISSION_REVOKED = 4101
    private const val ID_SYNC_SUMMARY = 4102
    private val txnNotificationCounter = AtomicInteger(5000)

    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = CHANNEL_DESC
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.createNotificationChannel(channel)
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun buildMainPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Privacy-safe notification (FRD Section 16):
     * Never exposes rupees amount, merchant, or account balance on lock screen or notification shade.
     */
    fun notifyTransactionProcessed(context: Context, bankName: String) {
        createNotificationChannel(context)
        if (!canPostNotifications(context)) return

        val safeBank = bankName.trim().ifEmpty { "Bank" }
        val bodyText = "$safeBank transaction recorded"

        val publicNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_monzi_notification)
            .setContentTitle("Monzi 2.1")
            .setContentText("New financial activity recorded")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_monzi_notification)
            .setContentTitle("Monzi 2.1")
            .setContentText(bodyText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .setContentIntent(buildMainPendingIntent(context))
            .setAutoCancel(true)
            .build()

        try {
            val id = txnNotificationCounter.updateAndGet { current ->
                if (current >= 5050) 5000 else current + 1
            }
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            Log.w("MonziNotify", "Missing POST_NOTIFICATIONS permission", e)
        }
    }

    fun notifyPermissionRevoked(context: Context) {
        createNotificationChannel(context)
        if (!canPostNotifications(context)) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_monzi_notification)
            .setContentTitle("Monzi SMS Monitoring Paused")
            .setContentText("SMS permissions were revoked. Open Monzi 2.1 to restore automatic tracking.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(buildMainPendingIntent(context))
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(ID_PERMISSION_REVOKED, notification)
        } catch (e: SecurityException) {
            Log.w("MonziNotify", "Missing POST_NOTIFICATIONS permission", e)
        }
    }

    fun notifyBudgetAlert(context: Context, title: String, message: String) {
        createNotificationChannel(context)
        if (!canPostNotifications(context)) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_monzi_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(buildMainPendingIntent(context))
            .setAutoCancel(true)
            .build()

        try {
            val id = txnNotificationCounter.updateAndGet { current ->
                if (current >= 5150) 5051 else current + 1
            }
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            Log.w("MonziNotify", "Missing POST_NOTIFICATIONS permission", e)
        }
    }

    fun notifySyncSummary(context: Context, syncedCount: Int, failedCount: Int) {
        if (syncedCount <= 0 && failedCount <= 0) return
        createNotificationChannel(context)
        if (!canPostNotifications(context)) return

        val message = when {
            syncedCount > 0 && failedCount == 0 ->
                "Synced $syncedCount queued transaction(s) to cloud"
            syncedCount > 0 && failedCount > 0 ->
                "Synced $syncedCount transaction(s); $failedCount pending retry"
            else ->
                "$failedCount queued transaction(s) awaiting network sync"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Monzi Cloud Sync")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(buildMainPendingIntent(context))
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(ID_SYNC_SUMMARY, notification)
        } catch (e: SecurityException) {
            Log.w("MonziNotify", "Missing POST_NOTIFICATIONS permission", e)
        }
    }
}
