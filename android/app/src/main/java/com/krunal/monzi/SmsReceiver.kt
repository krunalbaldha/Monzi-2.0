package com.krunal.monzi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val sender = sms.displayOriginatingAddress ?: ""
                val body = sms.displayMessageBody ?: ""
                val timestamp = sms.timestampMillis

                Log.d("MonziReceiver", "SMS received from: $sender")

                val parsed = BankSmsParser.parse(sender, body, timestamp)
                if (parsed != null) {
                    Log.i("MonziReceiver", "Financial SMS extracted: ${parsed.bankName} ₹${parsed.amount} ${parsed.direction}")
                    // Trigger Background Sync to Monzi Backend
                    SyncWorker.enqueueSync(context, parsed)
                }
            }
        }
    }
}
