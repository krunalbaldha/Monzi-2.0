package com.krunal.monzi

import com.krunal.monzi.util.IstTime
import com.krunal.monzi.util.MoneyMath
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
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
    val transactionDateMs: Long,
    val transactionType: String, // EXPENSE | INCOME | TRANSFER | REFUND | REVERSAL | CC_PURCHASE | CC_BILL_PAYMENT | INTEREST | BANK_CHARGES | ATM_WITHDRAWAL | CASH_DEPOSIT | OTHER
    val transactionCategory: String,
    val isSelfTransfer: Boolean,
    val isRefund: Boolean,
    val isCashback: Boolean,
    val isExcludedFromTotals: Boolean,
    val timestamp: Long,
    val confidenceScore: Double = 1.0,
    val isCardTransaction: Boolean = false
)

data class ParsedCreditCardStatement(
    val bankName: String,
    val cardMask: String,
    val totalDue: Double,
    val minimumDue: Double,
    val dueDateMs: Long?,
    val statementDateMs: Long,
    val statementPeriod: String
)

data class SmsInspectionResult(
    val parsed: ParsedSms?,
    val status: String, // "VALID" | "IGNORED" | "FAILED"
    val reason: String? = null,
    val statement: ParsedCreditCardStatement? = null
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

    private val CC_STATEMENT_PATTERN = Pattern.compile(
        "\\b(total amount due|total due|minimum amount due|min\\.?\\s*amt\\.?\\s*due|min\\.?\\s*due|statement.*credit card|credit card.*statement|bill generated)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val GENERAL_NOTICE_PATTERN = Pattern.compile(
        "\\b(update your kyc|re-?kyc|kyc pending|kyc expired|cheque book|cheque issued|cheque bounced|mandate created|mandate paused|mandate revoked|mandate cancelled|autopay registered|e-?mandate.*registered|emi reminder|payment reminder|will be debited|to be debited|scheduled to be debited|upcoming emi)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val BALANCE_ONLY_PATTERN = Pattern.compile(
        "\\b(avl bal|avail bal|available balance|updated balance|account balance|clear balance|closing balance)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val REFUND_PATTERN = Pattern.compile(
        "\\b(refund|refunded|reversal|reversed back|chargeback|money back|cashback)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val REVERSAL_PATTERN = Pattern.compile(
        "\\b(reversal|reversed back|reversed to|chargeback|txn reversed)\\b",
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

    fun toUtcIso(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(millis))
    }

    fun parseIsoToMillis(iso: String?): Long {
        if (iso.isNullOrBlank()) return System.currentTimeMillis()
        val raw = iso.trim()
        // Normalize Supabase/Postgres timestamps (e.g. "2026-11-25 14:30:00.123456+00")
        val normalized = raw
            .replace(' ', 'T')
            .replace(Regex("([+-]\\d{2})$"), "$1:00")
            .replace(Regex("([+-]\\d{2})(\\d{2})$"), "$1:$2")
        try {
            return Instant.parse(normalized).toEpochMilli()
        } catch (_: Exception) {
        }
        try {
            return OffsetDateTime.parse(normalized).toInstant().toEpochMilli()
        } catch (_: Exception) {
        }
        try {
            return LocalDateTime.parse(normalized).toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (_: Exception) {
        }
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                if (pattern.endsWith("'Z'") || pattern == "yyyy-MM-dd") {
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                }
                val d = sdf.parse(normalized)
                if (d != null) return d.time
            } catch (_: Exception) {
            }
        }
        return System.currentTimeMillis()
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

    fun extractTransactionDateMillis(text: String, fallbackTimestamp: Long): Long {
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
                buildIstMillis(year, month, day, hour, minute, second)?.let { return it }
            }
        }

        // 2. YYYY-MM-DD or YYYY/MM/DD
        val ymdMatcher = Pattern.compile("\\b(20\\d{2})[\\-/](\\d{1,2})[\\-/](\\d{1,2})\\b").matcher(text)
        if (ymdMatcher.find()) {
            val year = ymdMatcher.group(1)?.toIntOrNull()
            val month = ymdMatcher.group(2)?.toIntOrNull()
            val day = ymdMatcher.group(3)?.toIntOrNull()
            if (year != null && month != null && day != null) {
                buildIstMillis(year, month, day, hour, minute, second)?.let { return it }
            }
        }

        // 3. DD/MM/YYYY or DD-MM-YY or DD.MM.YYYY
        val dmyMatcher = Pattern.compile("\\b(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.](\\d{2,4})\\b").matcher(text)
        if (dmyMatcher.find()) {
            val day = dmyMatcher.group(1)?.toIntOrNull()
            val month = dmyMatcher.group(2)?.toIntOrNull()
            val year = dmyMatcher.group(3)?.toIntOrNull()
            if (day != null && month != null && year != null) {
                buildIstMillis(year, month, day, hour, minute, second)?.let { return it }
            }
        }

        return fallbackTimestamp
    }

    fun extractTransactionDateIso(text: String, fallbackTimestamp: Long): String {
        return toUtcIso(extractTransactionDateMillis(text, fallbackTimestamp))
    }

    fun classifyMerchant(merchant: String, description: String = "", direction: String = "DEBIT"): String {
        val haystack = "$merchant $description".lowercase(Locale.US)

        if (Regex("swiggy|zomato|dunzo|mcdonald|kfc|dominos|domino|starbucks|burger king|pizzahut|fassos|barbeque|cafe|restaurant|biryani|chai|food|eatclub|haldiram").containsMatchIn(haystack)) {
            return "Food & Dining"
        }
        if (Regex("amazon|flipkart|myntra|ajio|nykaa|meesho|snapdeal|shopclues|tatacliq|bewakoof|croma|reliance digital").containsMatchIn(haystack)) {
            return "Shopping"
        }
        if (Regex("uber|ola|rapido|irctc|makemytrip|redbus|hpcl|bpcl|indianoil|hp petrol|petrol|fuel|diesel|fastag|indigo|spicejet|goair|vistara|air india|abhibus|yatra|metro").containsMatchIn(haystack)) {
            return "Travel & Transport"
        }
        if (Regex("bsnl|jio|airtel|vodafone|\\bvi\\b|idea|electricity|bescom|mseb|tata power|cesc|adani electricity|wesco|nesco|gas|water bill|postpaid|broadband|internet|recharge|dth").containsMatchIn(haystack)) {
            return "Bills & Utilities"
        }
        if (Regex("netflix|spotify|hotstar|disney|prime video|youtube|zee5|sonyliv|apple music|gaana|wynk").containsMatchIn(haystack)) {
            return "Subscriptions"
        }
        if (Regex("bookmyshow|pvr|inox|cinema|movie|concert").containsMatchIn(haystack)) {
            return "Entertainment"
        }
        if (Regex("pharmacy|medplus|apollo|practo|1mg|netmeds|hospital|clinic|health|doctor|diagnostic|lab test|lybrate|pharmeasy").containsMatchIn(haystack)) {
            return "Healthcare"
        }
        if (Regex("zerodha|groww|upstox|mutual fund|\\bmf\\b|\\bsip\\b|\\bnps\\b|stock|shares|icicidirect|hdfc securities|kotak securities|motilal|angel broking|smallcase|kuvera").containsMatchIn(haystack)) {
            return "Investments"
        }
        if (Regex("bigbasket|blinkit|zepto|instamart|grofers|dmart|jiomart|reliance fresh|more supermarket|nature basket|supermarket|grocery|vegetables|fruits").containsMatchIn(haystack)) {
            return "Groceries"
        }
        if (Regex("salary|payroll|ctc|wages|stipend").containsMatchIn(haystack)) {
            return "Salary"
        }
        if (Regex("\\bemi\\b|loan emi|equated monthly|home loan|car loan|personal loan").containsMatchIn(haystack)) {
            return "EMI & Loan Payments"
        }
        if (Regex("cashback|reward credited").containsMatchIn(haystack)) {
            return "Cashback"
        }
        if (Regex("refund|reversal|returned|chargeback").containsMatchIn(haystack)) {
            return "Refund"
        }
        if (Regex("interest credited|int\\.pd|savings interest").containsMatchIn(haystack)) {
            return "Interest"
        }
        if (Regex("annual fee|late fee|service charge|bank charge|penalty|maintenance fee").containsMatchIn(haystack)) {
            return "Bank Charges"
        }
        return if (direction == "CREDIT") "Other Income" else "Uncategorized"
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

    private fun extractMask(cleanBody: String): String {
        val accRegexes = listOf(
            "(?:a/c|acct|account|ac)\\s*(?:no\\.?)?\\s*(?:ending\\s*(?:with\\s*)?)?[xX*]*([0-9]{3,6})",
            "(?:card)\\s+(?:ending\\s*(?:with\\s*)?)?[xX*]*([0-9]{4})",
            "(?:xxxx|XX|\\*{2,4})([0-9]{4})"
        )
        for (rx in accRegexes) {
            val m = Pattern.compile(rx, Pattern.CASE_INSENSITIVE).matcher(cleanBody)
            if (m.find()) {
                return "XX" + m.group(1)
            }
        }
        return "XX0000"
    }

    fun parseCreditCardStatementSms(
        sender: String,
        body: String,
        timestamp: Long = System.currentTimeMillis()
    ): ParsedCreditCardStatement? {
        if (body.isBlank()) return null
        val cleanBody = body.replace(Regex("[\\r\\n]+"), " ").replace(Regex("\\s+"), " ").trim()
        if (!CC_STATEMENT_PATTERN.matcher(cleanBody).find()) return null

        val totalMatcher = Pattern.compile(
            "(?:total\\s*(?:amount)?\\s*due|statement\\s*(?:bal|balance))\\s*(?:is|:|of)?\\s*(?:rs\\.?|inr|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)",
            Pattern.CASE_INSENSITIVE
        ).matcher(cleanBody)
        val totalDue = if (totalMatcher.find()) {
            totalMatcher.group(1)?.replace(",", "")?.toDoubleOrNull()?.let { MoneyMath.round2(it) }
        } else null
        if (totalDue == null || totalDue <= 0.0) return null

        val minMatcher = Pattern.compile(
            "(?:min(?:imum)?\\s*(?:amt|amount)?\\s*due)\\s*(?:is|:|of)?\\s*(?:rs\\.?|inr|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)",
            Pattern.CASE_INSENSITIVE
        ).matcher(cleanBody)
        val minimumDue = if (minMatcher.find()) {
            minMatcher.group(1)?.replace(",", "")?.toDoubleOrNull()?.let { MoneyMath.round2(it) } ?: 0.0
        } else 0.0

        val dueChunkMatcher = Pattern.compile(
            "(?:due\\s*(?:date|by|on)|pay\\s*by)\\s*:?\\s*(.{4,24})",
            Pattern.CASE_INSENSITIVE
        ).matcher(cleanBody)
        val dueDateMs = if (dueChunkMatcher.find()) {
            val chunk = dueChunkMatcher.group(1).orEmpty()
            val parsedDue = extractTransactionDateMillis(chunk, -1L)
            if (parsedDue > 0L) parsedDue else null
        } else null

        val bankName = identifyBank(sender, cleanBody)
        val cardMask = extractMask(cleanBody)
        return ParsedCreditCardStatement(
            bankName = bankName,
            cardMask = cardMask,
            totalDue = totalDue,
            minimumDue = minimumDue,
            dueDateMs = dueDateMs,
            statementDateMs = timestamp,
            statementPeriod = IstTime.toMonthKey(timestamp)
        )
    }

    fun inspect(sender: String, body: String, timestamp: Long = System.currentTimeMillis()): SmsInspectionResult {
        if (body.isBlank()) {
            return SmsInspectionResult(null, "IGNORED", "Empty SMS message")
        }
        val cleanBody = body.replace(Regex("[\\r\\n]+"), " ").replace(Regex("\\s+"), " ").trim()

        // 1. Strict privacy & noise filtering
        if (OTP_PATTERN.matcher(cleanBody).find()) {
            return SmsInspectionResult(null, "IGNORED", "OTP or verification code filtered out")
        }
        if (PROMO_PATTERN.matcher(cleanBody).find()) {
            return SmsInspectionResult(null, "IGNORED", "Promotional or marketing message ignored")
        }
        if (LOGIN_ALERT_PATTERN.matcher(cleanBody).find()) {
            return SmsInspectionResult(null, "IGNORED", "Login or security alert ignored")
        }
        if (FAILED_TXN_PATTERN.matcher(cleanBody).find()) {
            return SmsInspectionResult(null, "FAILED", "Failed or declined transaction ignored")
        }

        // Check for credit card statement / due reminder before general notices
        val parsedStatement = parseCreditCardStatementSms(sender, cleanBody, timestamp)
        if (parsedStatement != null && !DEBIT_PATTERN.matcher(cleanBody).find() && !CREDIT_PATTERN.matcher(cleanBody).find()) {
            return SmsInspectionResult(
                parsed = null,
                status = "STATEMENT",
                reason = "Credit card statement summary extracted",
                statement = parsedStatement
            )
        }

        if (GENERAL_NOTICE_PATTERN.matcher(cleanBody).find()) {
            return SmsInspectionResult(null, "IGNORED", "General bank notice or reminder ignored")
        }

        // 2. Identify Direction
        val isRefundKeyword = REFUND_PATTERN.matcher(cleanBody).find()
        val isDebit = DEBIT_PATTERN.matcher(cleanBody).find()
        val hasExplicitCredit = CREDIT_PATTERN.matcher(cleanBody).find()
        val isCredit = hasExplicitCredit || (
            isRefundKeyword &&
                Pattern.compile("\\b(to your|a/c|account|card|wallet)\\b", Pattern.CASE_INSENSITIVE).matcher(cleanBody).find() &&
                !isDebit
            )

        if (!isDebit && !isCredit) {
            val reason = if (BALANCE_ONLY_PATTERN.matcher(cleanBody).find()) {
                "Balance-only notification without debit or credit action"
            } else {
                "No valid CREDIT or DEBIT transaction action detected"
            }
            return SmsInspectionResult(null, "IGNORED", reason, parsedStatement)
        }

        val explicitSelfCredit = Pattern.compile(
            "\\b(credited\\s+(?:to|in|into)\\s+(?:your|ur|a/c|acct|account|wallet|card)|credited\\s+(?:with|by)|salary\\s+credited|money\\s+received|added\\s+to\\s+(?:your|ur|a/c|account|wallet)|credited\\s+back|reversed\\s+to\\s+(?:your|ur|a/c|acct|account|card)|credit\\s+of|deposited)\\b",
            Pattern.CASE_INSENSITIVE
        ).matcher(cleanBody).find()
        val hasStrongDebitPhrase = isDebit && Pattern.compile(
            "\\b(debited|withdrawn|spent|purchase|auto-?debited)\\b",
            Pattern.CASE_INSENSITIVE
        ).matcher(cleanBody).find()
        val hasOwnAccountCredit = Pattern.compile(
            "\\b(?:credited|reversed)\\s+(?:to|in|into)\\s+(?:your|ur|a/c|acct|account|card)\\b",
            Pattern.CASE_INSENSITIVE
        ).matcher(cleanBody).find()

        val direction = when {
            isCredit && !isDebit -> "CREDIT"
            isDebit && !isCredit -> "DEBIT"
            isRefundKeyword -> "CREDIT"
            explicitSelfCredit -> {
                if (hasStrongDebitPhrase && !hasOwnAccountCredit) "DEBIT" else "CREDIT"
            }
            isDebit -> "DEBIT"
            else -> "CREDIT"
        }

        // 3. Extract Amount (reject malformed negative amounts like "Rs. -500")
        if (Pattern.compile("(?:rs\\.?|inr|₹)\\s*-\\s*\\d+", Pattern.CASE_INSENSITIVE).matcher(cleanBody).find()) {
            return SmsInspectionResult(null, "IGNORED", "Negative or invalid currency amount ignored", parsedStatement)
        }
        var amount: Double? = null
        var explicitCurrencyFound = false
        val amountRegexes = listOf(
            "(?:rs\\.?|inr|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)",
            "(?:debited by|credited with|credit of|debit of|refund of|transaction of(?:\\s*(?:inr|rs\\.?|₹))?)\\s*([\\d,]+(?:\\.\\d{1,2})?)",
            "(?<![-\\d])([\\d,]+(?:\\.\\d{1,2})?)\\s*(?:debited|credited|spent|paid|withdrawn)"
        )
        for ((idx, rx) in amountRegexes.withIndex()) {
            val m = Pattern.compile(rx, Pattern.CASE_INSENSITIVE).matcher(cleanBody)
            if (m.find()) {
                val parsedAmt = m.group(1)?.replace(",", "")?.toDoubleOrNull()
                if (parsedAmt != null && parsedAmt > 0) {
                    amount = MoneyMath.round2(parsedAmt)
                    explicitCurrencyFound = (idx == 0)
                    break
                }
            }
        }
        if (amount == null || amount <= 0) {
            return SmsInspectionResult(null, "IGNORED", "No valid monetary amount found in SMS", parsedStatement)
        }

        // 4. Extract Masked Account / Card Number
        val accountMask = extractMask(cleanBody)
        val isCardTxn = Pattern.compile("\\b(credit card|cc txn|card ending|on your card)\\b", Pattern.CASE_INSENSITIVE)
            .matcher(cleanBody).find()

        // 5. Extract Available Balance
        var parsedBalance: Double? = null
        val balMatcher = Pattern.compile(
            "(?:avl bal|avail bal|available balance|avl\\. bal|clear bal|bal(?:ance)?|avl\\.?\\s*lmt|available credit limit)\\s*(?:is|:)?\\s*(?:rs\\.?|inr|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)",
            Pattern.CASE_INSENSITIVE
        ).matcher(cleanBody)
        if (balMatcher.find()) {
            parsedBalance = balMatcher.group(1)?.replace(",", "")?.toDoubleOrNull()?.let { MoneyMath.round2(it) }
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

        if (merchant.isNullOrBlank()) {
            val broadMatcher = Pattern.compile(
                "(?:to|at|info|towards)\\s+([A-Za-z0-9\\s._@\\-]{3,35}?)(?:\\s+(?:on|ref|avl|bal|via|using|dated|\\.))",
                Pattern.CASE_INSENSITIVE
            ).matcher(cleanBody)
            if (broadMatcher.find()) {
                merchant = broadMatcher.group(1)?.trim()
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
        val transactionDateMs = extractTransactionDateMillis(cleanBody, timestamp)
        val transactionDateIso = toUtcIso(transactionDateMs)

        // 11. Special flags & Transaction Type (M21-02)
        // Only mark self-transfer from SMS wording itself; cross-account pairs are linked by TransferDetector.
        val isSelfTransfer = Pattern.compile(
            "\\b(own account|self transfer|internal transfer|same bank transfer|between your accounts|transfer to your own|linked account transfer|trf to self|to own a/c)\\b",
            Pattern.CASE_INSENSITIVE
        ).matcher(cleanBody).find()

        val isCashback = Pattern.compile("\\b(cashback|reward credited|cashback received|reward points credited)\\b", Pattern.CASE_INSENSITIVE).matcher(cleanBody).find()
        val isReversal = direction == "CREDIT" && REVERSAL_PATTERN.matcher(cleanBody).find()
        val isRefund = direction == "CREDIT" && isRefundKeyword && !isCashback
        val isCcPayment = Pattern.compile("credit card bill|card payment|cc payment|paid towards.*card", Pattern.CASE_INSENSITIVE).matcher(cleanBody).find()
        val isInterest = direction == "CREDIT" && Pattern.compile("\\b(interest credited|int\\.pd|savings interest)\\b", Pattern.CASE_INSENSITIVE).matcher(cleanBody).find()
        val isBankCharge = direction == "DEBIT" && Pattern.compile("\\b(service charge|annual fee|late fee|penalty|sms alert charge|maintenance fee|bank charge)\\b", Pattern.CASE_INSENSITIVE).matcher(cleanBody).find()
        val isAtmWithdrawal = direction == "DEBIT" && paymentMode == "ATM"
        val isCashDeposit = direction == "CREDIT" && Pattern.compile("\\b(cash deposit|deposited cash|cdm deposit)\\b", Pattern.CASE_INSENSITIVE).matcher(cleanBody).find()

        val transactionType = when {
            isCcPayment -> "CC_BILL_PAYMENT"
            isSelfTransfer -> "TRANSFER"
            isReversal -> "REVERSAL"
            isRefund -> "REFUND"
            isInterest -> "INTEREST"
            isBankCharge -> "BANK_CHARGES"
            isAtmWithdrawal -> "ATM_WITHDRAWAL"
            isCashDeposit -> "CASH_DEPOSIT"
            isCardTxn && direction == "DEBIT" -> "CC_PURCHASE"
            direction == "CREDIT" -> "INCOME"
            else -> "EXPENSE"
        }

        val isExcluded = isCcPayment || isSelfTransfer
        val category = when (transactionType) {
            "CC_BILL_PAYMENT" -> "Bills & Utilities"
            "REFUND", "REVERSAL" -> "Refund"
            "INTEREST" -> "Interest"
            "BANK_CHARGES" -> "Bank Charges"
            else -> classifyMerchant(merchant ?: "", cleanBody, direction)
        }

        // Confidence score calculation (0.30..0.99)
        var score = 0.50
        if (explicitCurrencyFound) score += 0.15
        if (accountMask != "XX0000") score += 0.10
        if (!referenceId.isNullOrBlank()) score += 0.10
        if (!merchant.isNullOrBlank()) score += 0.08
        if (bankName != "Bank") score += 0.06
        if (parsedBalance != null) score += 0.04
        val confidenceScore = MoneyMath.round2(score.coerceIn(0.30, 0.99))

        return SmsInspectionResult(
            parsed = ParsedSms(
                amount = amount,
                direction = direction,
                bankName = bankName,
                accountMask = accountMask,
                merchant = merchant,
                referenceId = referenceId,
                paymentMode = paymentMode,
                parsedAvailableBalance = parsedBalance,
                transactionDateIso = transactionDateIso,
                transactionDateMs = transactionDateMs,
                transactionType = transactionType,
                transactionCategory = category,
                isSelfTransfer = isSelfTransfer,
                isRefund = isRefund || isReversal,
                isCashback = isCashback,
                isExcludedFromTotals = isExcluded,
                timestamp = timestamp,
                confidenceScore = confidenceScore,
                isCardTransaction = isCardTxn
            ),
            status = "VALID",
            reason = null,
            statement = parsedStatement
        )
    }

    fun parse(sender: String, body: String, timestamp: Long = System.currentTimeMillis()): ParsedSms? {
        return inspect(sender, body, timestamp).parsed
    }

    /**
     * Computes a deterministic SHA-256 fingerprint matching deduplication rules
     * so local Room deduplication and cloud Supabase deduplication share identical keys.
     */
    fun computeFingerprint(parsed: ParsedSms): String {
        val normAmount = String.format(Locale.US, "%.2f", parsed.amount)
        val normDir = parsed.direction.trim().uppercase(Locale.US)
        val normBank = parsed.bankName.ifBlank { "bank" }
            .trim()
            .lowercase(Locale.US)
            .replace(Regex("\\s+"), "")
        val normMask = parsed.accountMask.ifBlank { "xx0000" }
            .trim()
            .lowercase(Locale.US)
        val dateIso = parsed.transactionDateIso.ifBlank { toUtcIso(parsed.timestamp) }
        val normRef = (parsed.referenceId ?: "").trim().lowercase(Locale.US)

        val payload = if (normRef.length >= 6) {
            val dateDay = if (dateIso.length >= 10) dateIso.substring(0, 10) else dateIso
            "$normRef|$normBank|$normAmount|$normDir|$dateDay"
        } else {
            val dateHour = if (dateIso.length >= 13) dateIso.substring(0, 13) else dateIso
            val normMerchant = (parsed.merchant ?: "")
                .trim()
                .lowercase(Locale.US)
                .replace(Regex("\\s+"), "")
                .take(12)
            "$dateHour|$normAmount|$normDir|$normMask|$normMerchant"
        }

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
