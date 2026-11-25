package com.krunal.monzi

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
    val transactionDateIso: String,
    val transactionType: String, // "EXPENSE" | "INCOME" | "TRANSFER" | "REFUND" | "CC_BILL_PAYMENT"
    val isSelfTransfer: Boolean,
    val isRefund: Boolean,
    val timestamp: Long
)

object BankSmsParser {

    private val MONTH_MAP = mapOf(
        "jan" to 1, "january" to 1,
        "feb" to 2, "february" to 2,
        "mar" to 3, "march" to 3,
        "apr" to 4, "april" to 4,
        "may" to 5,
        "jun" to 6, "june" to 6,
        "jul" to 7, "july" to 7,
        "aug" to 8, "august" to 8,
        "sep" to 9, "sept" to 9, "september" to 9,
        "oct" to 10, "october" to 10,
        "nov" to 11, "november" to 11,
        "dec" to 12, "december" to 12
    )

    private val OTP_PATTERN = Pattern.compile(
        "\\b(otp|one[\\s-]?time[\\s-]?password|verification code|secret code|login pin|mpin|tpin|passcode|security code|2fa|auth code)\\b|\\bis your\\s+\\d{4,8}\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val PROMO_PATTERN = Pattern.compile(
        "\\b(pre-?approved|apply now|congratulations|click here|limited period offer|discount coupon|avail loan|instant loan|zero processing|credit limit increase|exciting offers|exclusive offer|shop now|win up to|flat \\d+% off|refer & earn|claim now|cashback offer|subscribe now|unsubscribe|bit\\.ly|tinyurl)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val LOGIN_ALERT_PATTERN = Pattern.compile(
        "\\b(logged in|login alert|successful login|new device login|session started|password changed|mpin changed|pin changed|pin reset|pin generated|biometric enabled)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val FAILED_TXN_PATTERN = Pattern.compile(
        "\\b(transaction failed|txn failed|payment failed|has failed|could not be processed|was declined|card declined|declined due to|unsuccessful|failed transaction|insufficient balance|insufficient funds|timed out|exceeded limit|invalid pin)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val GENERAL_NOTICE_PATTERN = Pattern.compile(
        "\\b(update your kyc|re-?kyc|kyc pending|kyc expired|statement is ready|e-?statement|monthly statement|cheque book|cheque issued|cheque bounced|mandate created|mandate paused|mandate revoked|mandate cancelled|autopay registered|e-?mandate.*registered|emi reminder|payment reminder|bill is due|due date|minimum amount due|total amount due|will be debited|to be debited|scheduled to be debited|upcoming emi)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val REFUND_PATTERN = Pattern.compile(
        "\\b(refund|refunded|reversal|reversed back|money back|cashback)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val DEBIT_PATTERN = Pattern.compile(
        "\\b(debited|spent|paid|withdrawn|sent|deducted|purchase|transfer to|trf to|done on|payment of|txn of|transaction of|charged|auto-?debited)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val CREDIT_PATTERN = Pattern.compile(
        "\\b(credited|received|deposited|refunded|added to|refund of|credit of|money received|reversed to|credited back)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private fun toUtcIso(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(millis))
    }

    private fun buildIstMillis(
        yearRaw: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int
    ): Long? {
        if (month !in 1..12 || day !in 1..31) return null
        val year = if (yearRaw < 100) 2000 + yearRaw else yearRaw
        if (year !in 2000..2100) return null

        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"), Locale.US)
        cal.isLenient = false
        cal.set(year, month - 1, day, hour, minute, second)
        cal.set(Calendar.MILLISECOND, 0)
        return try {
            cal.timeInMillis
        } catch (_: Exception) {
            null
        }
    }

    fun extractTransactionDateIso(text: String, fallbackTimestamp: Long): String {
        val istZone = TimeZone.getTimeZone("Asia/Kolkata")
        val fallbackCal = Calendar.getInstance(istZone, Locale.US).apply {
            timeInMillis = fallbackTimestamp
        }

        var hour = fallbackCal.get(Calendar.HOUR_OF_DAY)
        var minute = fallbackCal.get(Calendar.MINUTE)
        var second = fallbackCal.get(Calendar.SECOND)

        val timeMatcher = Pattern.compile(
            "\\b(?:at\\s+)?([01]?\\d|2[0-3]):([0-5]\\d)(?::([0-5]\\d))?(?:\\s*(AM|PM))?\\b",
            Pattern.CASE_INSENSITIVE
        ).matcher(text)
        if (timeMatcher.find()) {
            var h = timeMatcher.group(1)?.toIntOrNull() ?: hour
            val m = timeMatcher.group(2)?.toIntOrNull() ?: minute
            val s = timeMatcher.group(3)?.toIntOrNull() ?: 0
            val ampm = timeMatcher.group(4)?.uppercase(Locale.US)
            if (ampm == "PM" && h < 12) h += 12
            if (ampm == "AM" && h == 12) h = 0
            hour = h
            minute = m
            second = s
        }

        // 1. DD-MMM-YY or DD MMM YYYY
        val alphaMatcher = Pattern.compile(
            "\\b(\\d{1,2})[\\s\\-/](Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t|tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)[,\\s\\-/]+(\\d{2,4})\\b",
            Pattern.CASE_INSENSITIVE
        ).matcher(text)
        if (alphaMatcher.find()) {
            val day = alphaMatcher.group(1)?.toIntOrNull()
            val month = MONTH_MAP[alphaMatcher.group(2)?.lowercase(Locale.US)]
            val year = alphaMatcher.group(3)?.toIntOrNull()
            if (day != null && month != null && year != null) {
                buildIstMillis(year, month, day, hour, minute, second)?.let { return toUtcIso(it) }
            }
        }

        // 2. YYYY-MM-DD or YYYY/MM/DD
        val ymdMatcher = Pattern.compile("\\b(20\\d{2})[\\-/](\\d{1,2})[\\-/](\\d{1,2})\\b").matcher(text)
        if (ymdMatcher.find()) {
            val year = ymdMatcher.group(1)?.toIntOrNull()
            val month = ymdMatcher.group(2)?.toIntOrNull()
            val day = ymdMatcher.group(3)?.toIntOrNull()
            if (year != null && month != null && day != null) {
                buildIstMillis(year, month, day, hour, minute, second)?.let { return toUtcIso(it) }
            }
        }

        // 3. DD/MM/YYYY or DD-MM-YY or DD.MM.YYYY
        val dmyMatcher = Pattern.compile("\\b(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.](\\d{2,4})\\b").matcher(text)
        if (dmyMatcher.find()) {
            val day = dmyMatcher.group(1)?.toIntOrNull()
            val month = dmyMatcher.group(2)?.toIntOrNull()
            val year = dmyMatcher.group(3)?.toIntOrNull()
            if (day != null && month != null && year != null) {
                buildIstMillis(year, month, day, hour, minute, second)?.let { return toUtcIso(it) }
            }
        }

        return toUtcIso(fallbackTimestamp)
    }

    private fun identifyBank(sender: String, cleanBody: String): String {
        val combined = "$sender $cleanBody".uppercase(Locale.US)
        return when {
            combined.contains("HDFC") -> "HDFC Bank"
            combined.contains("ICICI") -> "ICICI Bank"
            combined.contains("SBI") || combined.contains("STATE BANK") -> "State Bank of India"
            combined.contains("AXIS") -> "Axis Bank"
            combined.contains("KOTAK") -> "Kotak Bank"
            combined.contains("PNB") || combined.contains("PUNJAB NATIONAL") -> "Punjab National Bank"
            combined.contains("BOB") || combined.contains("BANK OF BARODA") -> "Bank of Baroda"
            combined.contains("INDUSIND") || combined.contains("INDUSB") -> "IndusInd Bank"
            combined.contains("YESBANK") || combined.contains("YES BANK") || combined.contains("YESBNK") -> "Yes Bank"
            combined.contains("FEDERAL") || combined.contains("FEDFIN") || combined.contains("FEDBNK") -> "Federal Bank"
            combined.contains("CANARA") || combined.contains("CANBNK") -> "Canara Bank"
            combined.contains("UNION BANK") || combined.contains("UNIONBANK") || combined.contains("UNIONB") -> "Union Bank of India"
            combined.contains("INDIAN BANK") || combined.contains("INDIANBANK") -> "Indian Bank"
            combined.contains("CENTRAL BANK") || combined.contains("CENTBANK") -> "Central Bank of India"
            combined.contains("IDFC") || combined.contains("IDFCFB") -> "IDFC First Bank"
            combined.contains("AU SMALL") || combined.contains("AUBANK") || combined.contains("AU BANK") -> "AU Small Finance Bank"
            combined.contains("RBL") || combined.contains("RBLBNK") -> "RBL Bank"
            combined.contains("STANDARD CHARTERED") || combined.contains("SCBANK") || combined.contains("SCBL") -> "Standard Chartered"
            combined.contains("HSBC") -> "HSBC"
            combined.contains("CITI") -> "Citibank India"
            combined.contains("BANK OF INDIA") || combined.contains("BOIIND") -> "Bank of India"
            combined.contains("INDIAN OVERSEAS") || combined.contains("IOB") -> "Indian Overseas Bank"
            combined.contains("UCO BANK") || combined.contains("UCOBNK") -> "UCO Bank"
            combined.contains("BANDHAN") -> "Bandhan Bank"
            combined.contains("KARNATAKA BANK") || combined.contains("KTKBNK") -> "Karnataka Bank"
            combined.contains("SOUTH INDIAN") || combined.contains("SIB") -> "South Indian Bank"
            combined.contains("DBS") || combined.contains("DIGIBANK") -> "DBS Bank"
            combined.contains("AMEX") || combined.contains("AMERICAN EXPRESS") -> "American Express"
            combined.contains("ONECARD") -> "OneCard"
            combined.contains("SLICE") -> "Slice"
            combined.contains("JUPITER") -> "Jupiter"
            combined.contains("PHONEPE") || combined.contains("PHONPE") -> "PhonePe"
            combined.contains("GPAY") || combined.contains("GOOGLE PAY") || combined.contains("TEZ") -> "Google Pay"
            combined.contains("PAYTM") -> "Paytm Bank"
            combined.contains("AMAZON PAY") || combined.contains("AMAZONPAY") -> "Amazon Pay"
            else -> "Bank"
        }
    }

    fun parse(sender: String, body: String, timestamp: Long): ParsedSms? {
        if (body.isBlank()) return null
        val cleanBody = body.replace(Regex("[\\r\\n]+"), " ").replace(Regex("\\s+"), " ").trim()

        // 1. Strict privacy & noise filtering
        if (OTP_PATTERN.matcher(cleanBody).find()) return null
        if (PROMO_PATTERN.matcher(cleanBody).find()) return null
        if (LOGIN_ALERT_PATTERN.matcher(cleanBody).find()) return null
        if (FAILED_TXN_PATTERN.matcher(cleanBody).find()) return null
        if (GENERAL_NOTICE_PATTERN.matcher(cleanBody).find()) return null

        // 2. Identify Direction (balance-only notifications without debit/credit fail here)
        val isRefundKeyword = REFUND_PATTERN.matcher(cleanBody).find()
        val isDebit = DEBIT_PATTERN.matcher(cleanBody).find()
        val hasExplicitCredit = CREDIT_PATTERN.matcher(cleanBody).find()
        val isCredit = hasExplicitCredit || (
            isRefundKeyword &&
                Pattern.compile("\\b(to your|a/c|account|card|wallet)\\b", Pattern.CASE_INSENSITIVE).matcher(cleanBody).find() &&
                !isDebit
            )

        if (!isDebit && !isCredit) return null

        val direction = if (
            isCredit && (
                isRefundKeyword ||
                    Pattern.compile("\\b(credited|deposited|received|credit of)\\b", Pattern.CASE_INSENSITIVE).matcher(cleanBody).find()
                )
        ) {
            "CREDIT"
        } else if (isDebit) {
            "DEBIT"
        } else {
            "CREDIT"
        }

        // 3. Extract Amount
        var amount: Double? = null
        val amountRegexes = listOf(
            "(?:rs\\.?|inr|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)",
            "(?:debited by|credited with|credit of|debit of|refund of|transaction of(?:\\s*(?:inr|rs\\.?|₹))?)\\s*([\\d,]+(?:\\.\\d{1,2})?)",
            "([\\d,]+(?:\\.\\d{1,2})?)\\s*(?:debited|credited|spent|paid|withdrawn)"
        )
        for (rx in amountRegexes) {
            val m = Pattern.compile(rx, Pattern.CASE_INSENSITIVE).matcher(cleanBody)
            if (m.find()) {
                val parsedAmt = m.group(1)?.replace(",", "")?.toDoubleOrNull()
                if (parsedAmt != null && parsedAmt > 0) {
                    amount = parsedAmt
                    break
                }
            }
        }
        if (amount == null || amount <= 0) return null

        // 4. Extract Masked Account / Card Number
        var accountMask = "XX0000"
        val accRegexes = listOf(
            "(?:a/c|acct|account|ac)\\s*(?:no\\.?)?\\s*(?:ending\\s*(?:with\\s*)?)?[xX*]*([0-9]{3,6})",
            "(?:card)\\s+(?:ending\\s*(?:with\\s*)?)?[xX*]*([0-9]{4})",
            "(?:xxxx|XX|\\*{2,4})([0-9]{4})"
        )
        for (rx in accRegexes) {
            val m = Pattern.compile(rx, Pattern.CASE_INSENSITIVE).matcher(cleanBody)
            if (m.find()) {
                accountMask = "XX" + m.group(1)
                break
            }
        }

        // 5. Extract Available Balance
        var parsedBalance: Double? = null
        val balMatcher = Pattern.compile(
            "(?:avl bal|avail bal|available balance|avl\\. bal|clear bal|bal(?:ance)?)\\s*(?:is|:)?\\s*(?:rs\\.?|inr|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)",
            Pattern.CASE_INSENSITIVE
        ).matcher(cleanBody)
        if (balMatcher.find()) {
            parsedBalance = balMatcher.group(1)?.replace(",", "")?.toDoubleOrNull()
        }

        // 6. Extract Reference ID
        var referenceId: String? = null
        val refMatcher = Pattern.compile(
            "(?:upi\\s*ref(?:\\s*no\\.?)?|ref(?:\\s*no\\.?)?|utr(?:\\s*no\\.?)?|rrn|reference(?:\\s*no\\.?)?)\\s*:?\\s*([a-zA-Z0-9]{6,24})",
            Pattern.CASE_INSENSITIVE
        ).matcher(cleanBody)
        if (refMatcher.find()) {
            referenceId = refMatcher.group(1)
        }

        // 7. Payment Mode
        val paymentMode = when {
            Pattern.compile("\\bupi\\b", Pattern.CASE_INSENSITIVE).matcher(cleanBody).find() -> "UPI"
            Pattern.compile("\\b(atm|wdl|cash withdrawal)\\b", Pattern.CASE_INSENSITIVE).matcher(cleanBody).find() -> "ATM"
            Pattern.compile("\\b(card|pos|debit card|credit card)\\b", Pattern.CASE_INSENSITIVE).matcher(cleanBody).find() -> "CARD"
            Pattern.compile("\\bneft\\b", Pattern.CASE_INSENSITIVE).matcher(cleanBody).find() -> "NEFT"
            Pattern.compile("\\bimps\\b", Pattern.CASE_INSENSITIVE).matcher(cleanBody).find() -> "IMPS"
            Pattern.compile("\\brtgs\\b", Pattern.CASE_INSENSITIVE).matcher(cleanBody).find() -> "RTGS"
            Pattern.compile("\\bnetbanking\\b", Pattern.CASE_INSENSITIVE).matcher(cleanBody).find() -> "NETBANKING"
            else -> "OTHER"
        }

        // 8. Extract Merchant
        var merchant: String? = null
        val toAtMatcher = Pattern.compile(
            "\\b(?:to|at|towards|from)\\s+(?!VPA\\b|A/c\\b|Account\\b|your\\b)([A-Za-z0-9][A-Za-z0-9\\s._\\-&]{2,34}?)(?=\\s+(?:on|ref|avl|bal|via|using|dated|from|a/c|utr|upi|info|for|has|was|is)\\b|[.,;]|$)",
            Pattern.CASE_INSENSITIVE
        ).matcher(cleanBody)
        if (toAtMatcher.find()) {
            merchant = toAtMatcher.group(1)?.trim()
        }

        if (merchant.isNullOrBlank()) {
            val vpaMatcher = Pattern.compile("\\bVPA\\s+([A-Za-z0-9._\\-]+@[A-Za-z0-9.\\-]+)", Pattern.CASE_INSENSITIVE).matcher(cleanBody)
            if (vpaMatcher.find()) {
                merchant = vpaMatcher.group(1)?.substringBefore("@")?.replace(Regex("[._\\-]"), " ")?.trim()
            }
        }

        if (merchant.isNullOrBlank()) {
            val infoMatcher = Pattern.compile(
                "\\b(?:info:|trf to|info\\s*-)\\s*([A-Za-z0-9][A-Za-z0-9\\s._\\-&]{2,34}?)(?=\\s+(?:ref|utr|on|avl|bal)\\b|[.,;]|$)",
                Pattern.CASE_INSENSITIVE
            ).matcher(cleanBody)
            if (infoMatcher.find()) {
                merchant = infoMatcher.group(1)?.trim()
            }
        }

        if (!merchant.isNullOrBlank()) {
            merchant = merchant
                .replace(Regex("^vpa\\s+", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^upi[-\\s]", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^the\\s+", RegexOption.IGNORE_CASE), "")
                .trim()
            if (merchant.length > 40) {
                merchant = merchant.substring(0, 40).trim()
            }
        }

        // 9. Bank Identification
        val bankName = identifyBank(sender, cleanBody)

        // 10. Transaction Date from Body (fallback to SMS timestamp)
        val transactionDateIso = extractTransactionDateIso(cleanBody, timestamp)

        // 11. Special flags & Transaction Type
        val bankFirstWord = bankName.uppercase(Locale.US).split(" ").firstOrNull() ?: ""
        val isSelfTransfer =
            Pattern.compile("\\b(own account|self transfer|internal transfer|same bank)\\b", Pattern.CASE_INSENSITIVE).matcher(cleanBody).find() ||
                (Pattern.compile("\\bneft\\b", Pattern.CASE_INSENSITIVE).matcher(cleanBody).find() &&
                    bankName != "Bank" &&
                    bankFirstWord.isNotEmpty() &&
                    cleanBody.uppercase(Locale.US).contains(bankFirstWord))

        val isRefund = direction == "CREDIT" && isRefundKeyword
        val isCcPayment = Pattern.compile("credit card bill|card payment|cc payment|paid towards.*card", Pattern.CASE_INSENSITIVE).matcher(cleanBody).find()

        val transactionType = when {
            isCcPayment -> "CC_BILL_PAYMENT"
            isSelfTransfer -> "TRANSFER"
            isRefund -> "REFUND"
            direction == "CREDIT" -> "INCOME"
            else -> "EXPENSE"
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
            transactionDateIso = transactionDateIso,
            transactionType = transactionType,
            isSelfTransfer = isSelfTransfer,
            isRefund = isRefund,
            timestamp = timestamp
        )
    }
}
