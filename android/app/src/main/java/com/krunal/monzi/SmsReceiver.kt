package com.krunal.monzi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

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

        for ((sender, smsData) in grouped) {
            val parsed = BankSmsParser.parse(sender, smsData.body.toString(), smsData.timestamp)
            if (parsed != null) {
                Log.i(
                    "MonziReceiver",
                    "Financial SMS extracted: ${parsed.bankName} ${parsed.direction} ${parsed.amount}"
                )
                // Only extracted financial fields are passed to SyncWorker (never raw SMS body)
                SyncWorker.enqueueSync(context.applicationContext, parsed, sender)
            }
        }
    }
}
