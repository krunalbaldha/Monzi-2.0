package com.krunal.monzi

import java.util.regex.Pattern

data class ParsedSms(
    val amount: Double,
    val direction: String, // "CREDIT" or "DEBIT"
    val bankName: String,
    val accountMask: String,
    val merchant: String?,
    val referenceId: String?,
    val paymentMode: String,
    val parsedAvailableBalance: Double?,
    val timestamp: Long
)

object BankSmsParser {

    fun parse(sender: String, body: String, timestamp: Long): ParsedSms? {
        if (body.isBlank()) return null
        val cleanBody = body.replace("\n", " ").trim()

        // 1. Filter out OTPs and promotional messages
        if (Pattern.compile("\\b(otp|one time password|verification code|secret code|login pin)\\b", Pattern.CASE_INSENSITIVE).matcher(cleanBody).find()) {
            return null
        }
        if (Pattern.compile("\\b(pre-approved|apply now|congratulations|click here|limited period offer)\\b", Pattern.CASE_INSENSITIVE).matcher(cleanBody).find()) {
            return null
        }

        // Must contain monetary markers
        if (!Pattern.compile("(?:rs\\.?|inr|₹)\\s*[\\d,]+(?:\\.\\d{1,2})?", Pattern.CASE_INSENSITIVE).matcher(cleanBody).find()) {
            return null
        }

        // 2. Identify Direction
        val isDebit = Pattern.compile("\\b(debited|spent|paid|withdrawn|sent|deducted|purchase|transfer to|trf to)\\b", Pattern.CASE_INSENSITIVE).matcher(cleanBody).find()
        val isCredit = Pattern.compile("\\b(credited|received|deposited|refunded|added to|refund of)\\b", Pattern.CASE_INSENSITIVE).matcher(cleanBody).find()

        if (!isDebit && !isCredit) return null
        val direction = if (isDebit) "DEBIT" else "CREDIT"

        // 3. Extract Amount
        var amount: Double? = null
        val amtMatcher = Pattern.compile("(?:rs\\.?|inr|₹|debited by|credited with)\\s*([\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE).matcher(cleanBody)
        if (amtMatcher.find()) {
            amount = amtMatcher.group(1)?.replace(",", "")?.toDoubleOrNull()
        }

        if (amount == null) {
            val fallback = Pattern.compile("([\\d,]+(?:\\.\\d{1,2})?)\\s*(?:debited|credited)", Pattern.CASE_INSENSITIVE).matcher(cleanBody)
            if (fallback.find()) {
                amount = fallback.group(1)?.replace(",", "")?.toDoubleOrNull()
            }
        }

        if (amount == null || amount <= 0) return null

        // 4. Extract Masked Account / Card Number
        var accountMask = "XX0000"
        val accMatcher = Pattern.compile("(?:a/c|acct|account|card|a/c no\\.?|ac)\\s*(?:no\\.?)?\\s*(?:ending\\s*)?(?:xx|\\*+)?\\s*([0-9]{3,5})", Pattern.CASE_INSENSITIVE).matcher(cleanBody)
        if (accMatcher.find()) {
            accountMask = "XX" + accMatcher.group(1)
        } else {
            val cardMatcher = Pattern.compile("(?:card|a/c)\\s+(?:ending\\s+)?(?:\\*+|xx)?([0-9]{4})", Pattern.CASE_INSENSITIVE).matcher(cleanBody)
            if (cardMatcher.find()) {
                accountMask = "XX" + cardMatcher.group(1)
            }
        }

        // 5. Extract Available Balance
        var parsedBalance: Double? = null
        val balMatcher = Pattern.compile("(?:avl bal|avail bal|available balance|bal|balance)\\s*(?:is|:)?\\s*(?:rs\\.?|inr|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE).matcher(cleanBody)
        if (balMatcher.find()) {
            parsedBalance = balMatcher.group(1)?.replace(",", "")?.toDoubleOrNull()
        }

        // 6. Extract Reference ID
        var referenceId: String? = null
        val refMatcher = Pattern.compile("(?:upi ref|ref no|utr|rrn|ref|reference)\\s*(?:no\\.?|:)?\\s*([a-zA-Z0-9]{8,20})", Pattern.CASE_INSENSITIVE).matcher(cleanBody)
        if (refMatcher.find()) {
            referenceId = refMatcher.group(1)
        }

        // 7. Payment Mode
        val paymentMode = when {
            cleanBody.contains("UPI", ignoreCase = true) -> "UPI"
            cleanBody.contains("ATM", ignoreCase = true) || cleanBody.contains("WDL", ignoreCase = true) -> "ATM"
            cleanBody.contains("CARD", ignoreCase = true) || cleanBody.contains("POS", ignoreCase = true) -> "CARD"
            cleanBody.contains("IMPS", ignoreCase = true) -> "IMPS"
            cleanBody.contains("NEFT", ignoreCase = true) -> "NEFT"
            cleanBody.contains("RTGS", ignoreCase = true) -> "RTGS"
            else -> "OTHER"
        }

        // 8. Extract Merchant
        var merchant: String? = null
        val merchMatcher = Pattern.compile("(?:to|at|info|vpa|towards|trf to)\\s+([A-Za-z0-9\\s._@\\-]{3,35}?)(?:\\s+(?:on|ref|avl|bal|via|using|dated|\\.))", Pattern.CASE_INSENSITIVE).matcher(cleanBody)
        if (merchMatcher.find()) {
            merchant = merchMatcher.group(1)?.trim()?.replace(Regex("^(vpa|upi-)\\s*", RegexOption.IGNORE_CASE), "")
        }

        // 9. Bank Identification
        val combined = "$sender $cleanBody".uppercase()
        val bankName = when {
            combined.contains("HDFC") -> "HDFC Bank"
            combined.contains("ICICI") -> "ICICI Bank"
            combined.contains("SBI") || combined.contains("STATE BANK") -> "State Bank of India"
            combined.contains("AXIS") -> "Axis Bank"
            combined.contains("KOTAK") -> "Kotak Bank"
            combined.contains("PNB") -> "Punjab National Bank"
            combined.contains("BOB") -> "Bank of Baroda"
            combined.contains("INDUSIND") -> "IndusInd Bank"
            else -> "Bank"
        }

        return ParsedSms(
            amount = amount,
            direction = direction,
            bankName = bankName,
            accountMask = accountMask,
            merchant = merchant,
            referenceId = referenceId,
            paymentMode = paymentMode,
            parsedAvailableBalance = parsedBalance,
            timestamp = timestamp
        )
    }
}
