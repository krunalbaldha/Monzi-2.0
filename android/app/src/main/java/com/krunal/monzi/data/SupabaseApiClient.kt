package com.krunal.monzi.data

import android.util.Log
import com.krunal.monzi.BankSmsParser
import com.krunal.monzi.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val code: Int = 0) : ApiResult<Nothing>()
}

class SupabaseApiClient(
    baseUrlOverride: String? = null
) {

    private val baseUrl = (baseUrlOverride ?: BuildConfig.SUPABASE_URL).trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    // ── AUTHENTICATION (/auth/v1) ────────────────────────────────────────────

    fun signIn(email: String, password: String): ApiResult<UserSession> {
        val body = JSONObject().apply {
            put("email", email.trim())
            put("password", password)
        }
        val req = Request.Builder()
            .url("$baseUrl/auth/v1/token?grant_type=password")
            .addHeader("apikey", anonKey)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        return executeAuthRequest(req)
    }

    fun signUp(fullName: String, email: String, password: String): ApiResult<Pair<UserSession?, Boolean>> {
        val body = JSONObject().apply {
            put("email", email.trim())
            put("password", password)
            put("data", JSONObject().apply {
                put("full_name", fullName.trim())
                put("name", fullName.trim())
            })
        }
        val req = Request.Builder()
            .url("$baseUrl/auth/v1/signup")
            .addHeader("apikey", anonKey)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        return try {
            client.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    return ApiResult.Error(extractErrorMessage(raw, "Sign up failed"), res.code)
                }
                val json = JSONObject(raw)
                val accessToken = json.optString("access_token", "")
                if (accessToken.isNotBlank()) {
                    val session = parseSession(json)
                    ApiResult.Success(session to false)
                } else {
                    ApiResult.Success(null to true) // Verification email sent
                }
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Network error during sign up")
        }
    }

    fun refreshSession(refreshToken: String): ApiResult<UserSession> {
        val body = JSONObject().apply {
            put("refresh_token", refreshToken)
        }
        val req = Request.Builder()
            .url("$baseUrl/auth/v1/token?grant_type=refresh_token")
            .addHeader("apikey", anonKey)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        return executeAuthRequest(req)
    }

    fun recoverPassword(email: String): ApiResult<Unit> {
        val body = JSONObject().apply {
            put("email", email.trim())
        }
        val req = Request.Builder()
            .url("$baseUrl/auth/v1/recover")
            .addHeader("apikey", anonKey)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        return try {
            client.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    ApiResult.Error(extractErrorMessage(raw, "Could not send recovery email"), res.code)
                } else {
                    ApiResult.Success(Unit)
                }
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Network error")
        }
    }

    fun resendVerification(email: String): ApiResult<Unit> {
        val body = JSONObject().apply {
            put("type", "signup")
            put("email", email.trim())
        }
        val req = Request.Builder()
            .url("$baseUrl/auth/v1/resend")
            .addHeader("apikey", anonKey)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        return try {
            client.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    ApiResult.Error(extractErrorMessage(raw, "Could not resend verification email"), res.code)
                } else {
                    ApiResult.Success(Unit)
                }
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Network error")
        }
    }

    fun signOut(accessToken: String): ApiResult<Unit> {
        val req = Request.Builder()
            .url("$baseUrl/auth/v1/logout")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $accessToken")
            .post("{}".toRequestBody(jsonMedia))
            .build()
        return try {
            client.newCall(req).execute().use { ApiResult.Success(Unit) }
        } catch (_: Exception) {
            ApiResult.Success(Unit)
        }
    }

    fun getUser(accessToken: String): ApiResult<JSONObject> {
        val req = Request.Builder()
            .url("$baseUrl/auth/v1/user")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()
        return try {
            client.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    ApiResult.Error(extractErrorMessage(raw, "Failed to verify user"), res.code)
                } else {
                    ApiResult.Success(JSONObject(raw))
                }
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Network error")
        }
    }

    private fun executeAuthRequest(req: Request): ApiResult<UserSession> {
        return try {
            client.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    return ApiResult.Error(extractErrorMessage(raw, "Authentication failed"), res.code)
                }
                val json = JSONObject(raw)
                ApiResult.Success(parseSession(json))
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Network error during sign in")
        }
    }

    private fun parseSession(json: JSONObject): UserSession {
        val userObj = json.optJSONObject("user") ?: JSONObject()
        val expiresIn = json.optLong("expires_in", 3600L)
        val expiresAt = json.optLong("expires_at", (System.currentTimeMillis() / 1000L) + expiresIn)
        val emailConfirmed = !userObj.optString("email_confirmed_at", "").isNullOrBlank() ||
            !userObj.optString("confirmed_at", "").isNullOrBlank()
        return UserSession(
            userId = userObj.optString("id", ""),
            email = userObj.optString("email", ""),
            accessToken = json.optString("access_token", ""),
            refreshToken = json.optString("refresh_token", ""),
            expiresAtEpochSec = expiresAt,
            emailConfirmed = emailConfirmed
        )
    }

    // ── PROFILE & DEVICE SYNC TOKEN ──────────────────────────────────────────

    fun fetchOrProvisionProfile(accessToken: String, userId: String, email: String): ApiResult<UserProfile> {
        val selectUrl = "$baseUrl/rest/v1/user_profiles?id=eq.$userId&select=*"
        val getReq = authedBuilder(selectUrl, accessToken).get().build()
        return try {
            client.newCall(getReq).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    return ApiResult.Error(extractErrorMessage(raw, "Profile fetch failed"), res.code)
                }
                val arr = JSONArray(raw)
                if (arr.length() > 0) {
                    val obj = arr.getJSONObject(0)
                    var syncToken = obj.optString("device_sync_token", "")
                    if (syncToken.isBlank() || syncToken == "null") {
                        syncToken = generateRandomSyncToken()
                        upsertProfile(
                            accessToken,
                            UserProfile(
                                id = userId,
                                fullName = obj.optString("full_name", email.substringBefore("@")),
                                email = obj.optString("email", email),
                                currency = obj.optString("currency", "INR"),
                                smsPermission = obj.optString("sms_permission", "prompt"),
                                biometricEnabled = obj.optBoolean("biometric_enabled", false),
                                deviceSyncToken = syncToken
                            )
                        )
                    }
                    ApiResult.Success(parseProfile(obj, syncToken))
                } else {
                    val defaultProfile = UserProfile(
                        id = userId,
                        fullName = email.substringBefore("@").ifBlank { "Monzi User" },
                        email = email,
                        currency = "INR",
                        smsPermission = "prompt",
                        biometricEnabled = false,
                        deviceSyncToken = generateRandomSyncToken()
                    )
                    upsertProfile(accessToken, defaultProfile)
                }
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Network error loading profile")
        }
    }

    fun upsertProfile(accessToken: String, profile: UserProfile): ApiResult<UserProfile> {
        val body = JSONObject().apply {
            put("id", profile.id)
            put("full_name", profile.fullName)
            put("email", profile.email)
            put("currency", profile.currency)
            put("sms_permission", profile.smsPermission)
            put("biometric_enabled", profile.biometricEnabled)
            if (profile.deviceSyncToken.isNotBlank()) {
                put("device_sync_token", profile.deviceSyncToken)
            }
            put("updated_at", BankSmsParser.toUtcIso(System.currentTimeMillis()))
        }
        val req = authedBuilder("$baseUrl/rest/v1/user_profiles?on_conflict=id", accessToken)
            .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        return try {
            client.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    return ApiResult.Error(extractErrorMessage(raw, "Profile update failed"), res.code)
                }
                val arr = JSONArray(raw)
                if (arr.length() > 0) {
                    val obj = arr.getJSONObject(0)
                    ApiResult.Success(parseProfile(obj, obj.optString("device_sync_token", profile.deviceSyncToken)))
                } else {
                    ApiResult.Success(profile)
                }
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Network error saving profile")
        }
    }

    private fun parseProfile(obj: JSONObject, tokenOverride: String): UserProfile {
        return UserProfile(
            id = obj.optString("id", ""),
            fullName = obj.optString("full_name", "Monzi User").ifBlank { "Monzi User" },
            email = obj.optString("email", ""),
            currency = obj.optString("currency", "INR").ifBlank { "INR" },
            smsPermission = obj.optString("sms_permission", "prompt").ifBlank { "prompt" },
            biometricEnabled = obj.optBoolean("biometric_enabled", false),
            deviceSyncToken = tokenOverride.takeIf { it.isNotBlank() && it != "null" } ?: ""
        )
    }

    // ── CATEGORIES ───────────────────────────────────────────────────────────

    fun fetchCategories(accessToken: String): ApiResult<List<CategoryEntity>> {
        val req = authedBuilder("$baseUrl/rest/v1/categories?select=*&order=name.asc", accessToken)
            .get()
            .build()
        return executeArray(req) { arr ->
            val list = ArrayList<CategoryEntity>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    CategoryEntity(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        icon = o.optString("icon", "Tag"),
                        color = o.optString("color", "#2563EB"),
                        isIncome = o.optBoolean("is_income", false)
                    )
                )
            }
            list
        }
    }

    fun createCategory(
        accessToken: String,
        name: String,
        color: String,
        isIncome: Boolean
    ): ApiResult<CategoryEntity> {
        val body = JSONObject().apply {
            put("name", name.trim())
            put("icon", "Tag")
            put("color", color)
            put("is_income", isIncome)
        }
        val req = authedBuilder("$baseUrl/rest/v1/categories?on_conflict=name", accessToken)
            .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        return executeSingleFromArray(req) { o ->
            CategoryEntity(
                id = o.optString("id"),
                name = o.optString("name"),
                icon = o.optString("icon", "Tag"),
                color = o.optString("color", "#2563EB"),
                isIncome = o.optBoolean("is_income", false)
            )
        }
    }

    // ── ACCOUNTS ─────────────────────────────────────────────────────────────

    fun fetchAccounts(accessToken: String, userId: String): ApiResult<List<AccountEntity>> {
        val req = authedBuilder(
            "$baseUrl/rest/v1/accounts?user_id=eq.$userId&select=*&order=current_balance.desc",
            accessToken
        ).get().build()
        return executeArray(req) { arr ->
            val list = ArrayList<AccountEntity>(arr.length())
            for (i in 0 until arr.length()) {
                list.add(parseAccount(arr.getJSONObject(i)))
            }
            list
        }
    }

    fun upsertAccount(accessToken: String, account: AccountEntity): ApiResult<AccountEntity> {
        val body = JSONObject().apply {
            put("user_id", account.userId)
            put("bank_name", account.bankName)
            put("account_number_mask", account.accountNumberMask)
            if (!account.accountName.isNullOrBlank()) put("account_name", account.accountName)
            put("account_type", account.accountType)
            put("initial_balance", account.initialBalance)
            put("opening_balance", account.openingBalance)
            put("current_balance", account.currentBalance)
            if (account.availableBalance != null) put("available_balance", account.availableBalance)
            put("balance_source", account.balanceSource)
            put("reconciliation_status", account.reconciliationStatus)
            if (account.creditLimit != null) put("credit_limit", account.creditLimit)
            if (account.statementDay != null) put("statement_day", account.statementDay)
            if (account.dueDay != null) put("due_day", account.dueDay)
            if (!account.billingCycle.isNullOrBlank()) put("billing_cycle", account.billingCycle)
            put("account_status", account.accountStatus)
            put("updated_at", BankSmsParser.toUtcIso(System.currentTimeMillis()))
        }
        val req = authedBuilder(
            "$baseUrl/rest/v1/accounts?on_conflict=user_id,bank_name,account_number_mask",
            accessToken
        )
            .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        return executeSingleFromArray(req) { parseAccount(it) }
    }

    fun updateAccount(accessToken: String, account: AccountEntity): ApiResult<Unit> {
        val body = JSONObject().apply {
            put("bank_name", account.bankName)
            put("account_number_mask", account.accountNumberMask)
            if (account.accountName.isNullOrBlank()) put("account_name", JSONObject.NULL) else put("account_name", account.accountName)
            put("account_type", account.accountType)
            put("opening_balance", account.openingBalance)
            put("current_balance", account.currentBalance)
            put("balance_source", account.balanceSource)
            if (account.creditLimit != null) put("credit_limit", account.creditLimit)
            if (account.statementDay != null) put("statement_day", account.statementDay)
            if (account.dueDay != null) put("due_day", account.dueDay)
            put("updated_at", BankSmsParser.toUtcIso(System.currentTimeMillis()))
        }
        val req = authedBuilder(
            "$baseUrl/rest/v1/accounts?id=eq.${account.id}&user_id=eq.${account.userId}",
            accessToken
        ).patch(body.toString().toRequestBody(jsonMedia)).build()
        return executeVoid(req)
    }

    fun updateAccountBalance(
        accessToken: String,
        userId: String,
        accountId: String,
        newBalance: Double,
        balanceSource: String = "CALCULATED"
    ): ApiResult<Unit> {
        val body = JSONObject().apply {
            put("current_balance", newBalance)
            put("balance_source", balanceSource)
            put("updated_at", BankSmsParser.toUtcIso(System.currentTimeMillis()))
        }
        val req = authedBuilder(
            "$baseUrl/rest/v1/accounts?id=eq.$accountId&user_id=eq.$userId",
            accessToken
        )
            .patch(body.toString().toRequestBody(jsonMedia))
            .build()
        return executeVoid(req)
    }

    fun deleteAccount(accessToken: String, userId: String, accountId: String): ApiResult<Unit> {
        val req = authedBuilder(
            "$baseUrl/rest/v1/accounts?id=eq.$accountId&user_id=eq.$userId",
            accessToken
        ).delete().build()
        return executeVoid(req)
    }

    private fun parseAccount(o: JSONObject): AccountEntity {
        val syncedIso = o.optString("last_synced_at", "").takeIf { it.isNotBlank() && it != "null" }
        return AccountEntity(
            id = o.optString("id"),
            userId = o.optString("user_id"),
            bankName = o.optString("bank_name", "Bank"),
            accountNumberMask = o.optString("account_number_mask", "XX0000"),
            accountName = o.optString("account_name", "").takeIf { it.isNotBlank() && it != "null" },
            accountType = o.optString("account_type", "SAVINGS"),
            initialBalance = o.optDouble("initial_balance", 0.0),
            openingBalance = o.optDouble("opening_balance", 0.0),
            currentBalance = o.optDouble("current_balance", 0.0),
            availableBalance = if (o.isNull("available_balance")) null else o.optDouble("available_balance"),
            balanceSource = o.optString("balance_source", "CALCULATED"),
            reconciliationStatus = o.optString("reconciliation_status", "CALCULATED_ONLY"),
            creditLimit = if (o.isNull("credit_limit")) null else o.optDouble("credit_limit"),
            statementDay = if (o.isNull("statement_day")) null else o.optInt("statement_day"),
            dueDay = if (o.isNull("due_day")) null else o.optInt("due_day"),
            billingCycle = o.optString("billing_cycle", "").takeIf { it.isNotBlank() && it != "null" },
            accountStatus = o.optString("account_status", "ACTIVE"),
            lastSyncedAtIso = syncedIso,
            pendingSync = false,
            updatedAtMs = BankSmsParser.parseIsoToMillis(o.optString("updated_at", ""))
        )
    }

    // ── TRANSACTIONS ─────────────────────────────────────────────────────────

    fun fetchTransactions(
        accessToken: String,
        userId: String,
        limit: Int = 1000
    ): ApiResult<List<TransactionEntity>> {
        val url = "$baseUrl/rest/v1/transactions?user_id=eq.$userId&select=*,accounts(bank_name,account_number_mask,account_type),categories(name,color,icon)&order=transaction_date.desc&limit=$limit"
        val req = authedBuilder(url, accessToken).get().build()
        return executeArray(req) { arr ->
            val list = ArrayList<TransactionEntity>(arr.length())
            for (i in 0 until arr.length()) {
                list.add(parseTransaction(arr.getJSONObject(i)))
            }
            list
        }
    }

    fun insertManualTransaction(
        accessToken: String,
        txn: TransactionEntity
    ): ApiResult<TransactionEntity> {
        val safeAccountId = txn.accountId?.takeIf { isUuid(it) }
        val safeCategoryId = txn.categoryId?.takeIf { isUuid(it) }
        val body = JSONObject().apply {
            put("user_id", txn.userId)
            if (safeAccountId != null) put("account_id", safeAccountId)
            if (safeCategoryId != null) put("category_id", safeCategoryId)
            put("amount", txn.amount)
            put("direction", txn.direction)
            put("transaction_type", toSupabaseTransactionType(txn.transactionType))
            put("merchant", txn.merchant ?: "Manual Entry")
            put("payment_mode", txn.paymentMode)
            if (!txn.referenceId.isNullOrBlank()) put("reference_id", txn.referenceId)
            put("sms_fingerprint", txn.smsFingerprint)
            put("is_excluded_from_totals", txn.isExcludedFromTotals)
            if (!txn.notes.isNullOrBlank()) put("notes", txn.notes)
            put("currency", txn.currency)
            put("transaction_source", txn.transactionSource)
            put("transaction_status", txn.transactionStatus)
            put("transaction_date", txn.transactionDateIso)
        }
        val req = authedBuilder(
            "$baseUrl/rest/v1/transactions?select=*,accounts(bank_name,account_number_mask,account_type),categories(name,color,icon)",
            accessToken
        )
            .addHeader("Prefer", "return=representation")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        return executeSingleFromArray(req) { parseTransaction(it) }
    }

    fun findTransactionIdByFingerprint(
        accessToken: String,
        userId: String,
        fingerprint: String
    ): ApiResult<String?> {
        if (fingerprint.isBlank()) return ApiResult.Success(null)
        val url = "$baseUrl/rest/v1/transactions?user_id=eq.$userId&sms_fingerprint=eq.${urlEncode(fingerprint)}&select=id&limit=1"
        val req = authedBuilder(url, accessToken).get().build()
        return executeArray(req) { arr ->
            if (arr.length() > 0) arr.getJSONObject(0).optString("id").takeIf { it.isNotBlank() } else null
        }
    }

    fun updateTransaction(
        accessToken: String,
        userId: String,
        remoteId: String,
        categoryId: String?,
        notes: String?,
        merchant: String?,
        transactionType: String?,
        amount: Double? = null,
        direction: String? = null,
        accountId: String? = null,
        transactionDateIso: String? = null
    ): ApiResult<Unit> {
        val safeCatId = categoryId?.takeIf { isUuid(it) }
        val safeAccId = accountId?.takeIf { isUuid(it) }
        val body = JSONObject().apply {
            if (safeCatId == null) put("category_id", JSONObject.NULL) else put("category_id", safeCatId)
            if (notes.isNullOrBlank()) put("notes", JSONObject.NULL) else put("notes", notes)
            if (!merchant.isNullOrBlank()) put("merchant", merchant)
            if (!transactionType.isNullOrBlank()) {
                put("transaction_type", toSupabaseTransactionType(transactionType))
                put(
                    "is_excluded_from_totals",
                    transactionType == "TRANSFER" || transactionType == "CC_BILL_PAYMENT"
                )
            }
            if (amount != null && amount > 0.0) put("amount", amount)
            if (!direction.isNullOrBlank()) put("direction", direction)
            if (accountId != null) {
                if (safeAccId == null) put("account_id", JSONObject.NULL) else put("account_id", safeAccId)
            }
            if (!transactionDateIso.isNullOrBlank()) put("transaction_date", transactionDateIso)
            put("updated_at", BankSmsParser.toUtcIso(System.currentTimeMillis()))
        }
        val req = authedBuilder(
            "$baseUrl/rest/v1/transactions?id=eq.$remoteId&user_id=eq.$userId",
            accessToken
        ).patch(body.toString().toRequestBody(jsonMedia)).build()
        return executeVoid(req)
    }

    fun deleteTransaction(accessToken: String, userId: String, remoteId: String): ApiResult<Unit> {
        val req = authedBuilder(
            "$baseUrl/rest/v1/transactions?id=eq.$remoteId&user_id=eq.$userId",
            accessToken
        ).delete().build()
        return executeVoid(req)
    }

    private fun parseTransaction(o: JSONObject): TransactionEntity {
        val accObj = o.optJSONObject("accounts")
        val catObj = o.optJSONObject("categories")
        val dateIso = o.optString("transaction_date")
        val bankName = accObj?.optString("bank_name")?.takeIf { it.isNotBlank() } ?: "Bank"
        val accountMask = accObj?.optString("account_number_mask")?.takeIf { it.isNotBlank() } ?: "XX0000"
        val parsedBal = if (o.isNull("parsed_available_balance")) null else o.optDouble("parsed_available_balance")
        val txType = o.optString("transaction_type", "EXPENSE")

        return TransactionEntity(
            localId = o.optString("id"),
            remoteId = o.optString("id"),
            userId = o.optString("user_id"),
            accountId = o.optString("account_id").takeIf { it.isNotBlank() && it != "null" },
            categoryId = o.optString("category_id").takeIf { it.isNotBlank() && it != "null" },
            smsFingerprint = o.optString("sms_fingerprint"),
            amount = o.optDouble("amount", 0.0),
            direction = o.optString("direction", "DEBIT"),
            transactionType = txType,
            bankName = bankName,
            accountMask = accountMask,
            merchant = o.optString("merchant").takeIf { it.isNotBlank() && it != "null" },
            referenceId = o.optString("reference_id").takeIf { it.isNotBlank() && it != "null" },
            paymentMode = o.optString("payment_mode", "UPI"),
            parsedAvailableBalance = parsedBal,
            isSelfTransfer = txType == "TRANSFER",
            isRefund = txType == "REFUND" || txType == "REVERSAL",
            isExcludedFromTotals = o.optBoolean("is_excluded_from_totals", false) ||
                txType == "TRANSFER" ||
                txType == "CC_BILL_PAYMENT",
            transferStatus = o.optString("transfer_status").takeIf { it.isNotBlank() && it != "null" }
                ?: if (txType == "TRANSFER") "CONFIRMED" else "NONE",
            refundStatus = o.optString("refund_status").takeIf { it.isNotBlank() && it != "null" }
                ?: if (txType == "REFUND" || txType == "REVERSAL") "CONFIRMED" else "NONE",
            linkedTransactionId = o.optString("linked_transaction_id").takeIf { it.isNotBlank() && it != "null" },
            notes = o.optString("notes").takeIf { it.isNotBlank() && it != "null" },
            currency = o.optString("currency", "INR"),
            transactionSource = o.optString("transaction_source", "INCOMING_SMS"),
            transactionStatus = o.optString("transaction_status", "COMPLETED"),
            confidenceScore = if (o.isNull("confidence_score")) 1.0 else o.optDouble("confidence_score", 1.0),
            duplicateStatus = o.optString("duplicate_status", "UNIQUE").ifBlank { "UNIQUE" },
            transactionDateIso = dateIso,
            transactionDateMs = BankSmsParser.parseIsoToMillis(dateIso),
            syncStatus = "SYNCED",
            createdAt = BankSmsParser.parseIsoToMillis(o.optString("created_at", dateIso)),
            categoryName = catObj?.optString("name")?.takeIf { it.isNotBlank() && it != "null" },
            categoryColor = catObj?.optString("color")?.takeIf { it.isNotBlank() && it != "null" },
            accountDisplayName = "$bankName ($accountMask)"
        )
    }

    // ── DEVICE SYNC RPC (Works in background with device_sync_token) ─────────

    fun deviceSyncIngestTransaction(
        syncToken: String,
        deviceId: String,
        record: TransactionEntity,
        categoryId: String?
    ): ApiResult<String> {
        val body = JSONObject().apply {
            put("p_sync_token", syncToken)
            put("p_device_id", deviceId)
            put("p_sender", record.sender ?: record.bankName)
            put("p_amount", record.amount)
            put("p_direction", record.direction)
            put("p_transaction_type", toSupabaseTransactionType(record.transactionType))
            put("p_bank_name", record.bankName)
            put("p_account_mask", record.accountMask)
            put("p_account_type", inferAccountType(record.bankName, record.merchant))
            put("p_merchant", record.merchant ?: "Bank Transaction")
            put("p_payment_mode", record.paymentMode)
            put("p_reference_id", record.referenceId ?: JSONObject.NULL)
            put("p_sms_fingerprint", record.smsFingerprint)
            put("p_parsed_available_balance", record.parsedAvailableBalance ?: JSONObject.NULL)
            put("p_is_excluded_from_totals", record.isExcludedFromTotals)
            put("p_transaction_date", record.transactionDateIso)
            val safeCatId = categoryId?.takeIf { isUuid(it) }
            put("p_category_id", safeCatId ?: JSONObject.NULL)
            put("p_transaction_source", record.transactionSource)
            put("p_transaction_status", record.transactionStatus)
            put("p_currency", record.currency)
        }

        val req = Request.Builder()
            .url("$baseUrl/rest/v1/rpc/device_sync_ingest_transaction")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $anonKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        return try {
            client.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    return ApiResult.Error(extractErrorMessage(raw, "RPC sync failed"), res.code)
                }
                val json = JSONObject(raw)
                val status = json.optString("status", "")
                when (status) {
                    "PROCESSED", "DUPLICATE" -> ApiResult.Success(status)
                    "UNAUTHORIZED" -> ApiResult.Error(json.optString("reason", "Invalid device sync token"), 401)
                    else -> ApiResult.Error(json.optString("reason", "RPC rejected payload"), 400)
                }
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Network error calling device_sync_ingest_transaction")
        }
    }

    // ── RULES, DEVICES, LOGS, RESET ──────────────────────────────────────────

    fun fetchRules(accessToken: String, userId: String): ApiResult<List<TransactionRuleEntity>> {
        val url = "$baseUrl/rest/v1/transaction_rules?user_id=eq.$userId&select=*,categories(name,color)&order=priority.asc"
        val req = authedBuilder(url, accessToken).get().build()
        return executeArray(req) { arr ->
            val list = ArrayList<TransactionRuleEntity>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val cat = o.optJSONObject("categories")
                list.add(
                    TransactionRuleEntity(
                        id = o.optString("id"),
                        userId = o.optString("user_id"),
                        matchingRule = o.optString("matching_rule"),
                        categoryId = o.optString("category_id"),
                        categoryName = cat?.optString("name")?.takeIf { it.isNotBlank() && it != "null" },
                        categoryColor = cat?.optString("color")?.takeIf { it.isNotBlank() && it != "null" },
                        priority = o.optInt("priority", 10),
                        enabled = o.optBoolean("enabled", true),
                        createdAtIso = o.optString("created_at", "")
                    )
                )
            }
            list
        }
    }

    fun createRule(
        accessToken: String,
        userId: String,
        matchingRule: String,
        categoryId: String,
        priority: Int
    ): ApiResult<TransactionRuleEntity> {
        val body = JSONObject().apply {
            put("user_id", userId)
            put("matching_rule", matchingRule.trim())
            put("category_id", categoryId)
            put("priority", priority)
            put("enabled", true)
        }
        val req = authedBuilder(
            "$baseUrl/rest/v1/transaction_rules?select=*,categories(name,color)",
            accessToken
        )
            .addHeader("Prefer", "return=representation")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        return executeSingleFromArray(req) { o ->
            val cat = o.optJSONObject("categories")
            TransactionRuleEntity(
                id = o.optString("id"),
                userId = o.optString("user_id"),
                matchingRule = o.optString("matching_rule"),
                categoryId = o.optString("category_id"),
                categoryName = cat?.optString("name")?.takeIf { it.isNotBlank() && it != "null" },
                categoryColor = cat?.optString("color")?.takeIf { it.isNotBlank() && it != "null" },
                priority = o.optInt("priority", 10),
                enabled = o.optBoolean("enabled", true),
                createdAtIso = o.optString("created_at", "")
            )
        }
    }

    fun updateRule(
        accessToken: String,
        userId: String,
        ruleId: String,
        matchingRule: String,
        categoryId: String,
        priority: Int,
        enabled: Boolean
    ): ApiResult<Unit> {
        val body = JSONObject().apply {
            put("matching_rule", matchingRule.trim())
            put("category_id", categoryId)
            put("priority", priority)
            put("enabled", enabled)
        }
        val req = authedBuilder(
            "$baseUrl/rest/v1/transaction_rules?id=eq.$ruleId&user_id=eq.$userId",
            accessToken
        ).patch(body.toString().toRequestBody(jsonMedia)).build()
        return executeVoid(req)
    }

    fun toggleRule(accessToken: String, userId: String, ruleId: String, enabled: Boolean): ApiResult<Unit> {
        val body = JSONObject().apply { put("enabled", enabled) }
        val req = authedBuilder(
            "$baseUrl/rest/v1/transaction_rules?id=eq.$ruleId&user_id=eq.$userId",
            accessToken
        ).patch(body.toString().toRequestBody(jsonMedia)).build()
        return executeVoid(req)
    }

    fun deleteRule(accessToken: String, userId: String, ruleId: String): ApiResult<Unit> {
        val req = authedBuilder(
            "$baseUrl/rest/v1/transaction_rules?id=eq.$ruleId&user_id=eq.$userId",
            accessToken
        ).delete().build()
        return executeVoid(req)
    }

    fun registerDeviceHeartbeat(
        accessToken: String,
        userId: String,
        deviceIdentifier: String,
        appVersion: String
    ): ApiResult<Unit> {
        val body = JSONObject().apply {
            put("user_id", userId)
            put("device_identifier", deviceIdentifier)
            put("platform", "android-native")
            put("app_version", appVersion)
            put("last_seen_at", BankSmsParser.toUtcIso(System.currentTimeMillis()))
        }
        val req = authedBuilder("$baseUrl/rest/v1/devices?on_conflict=user_id,device_identifier", accessToken)
            .addHeader("Prefer", "resolution=merge-duplicates")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        return executeVoid(req)
    }

    fun fetchDevices(accessToken: String, userId: String): ApiResult<List<DeviceEntity>> {
        val req = authedBuilder(
            "$baseUrl/rest/v1/devices?user_id=eq.$userId&select=*&order=last_seen_at.desc",
            accessToken
        ).get().build()
        return executeArray(req) { arr ->
            val list = ArrayList<DeviceEntity>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    DeviceEntity(
                        id = o.optString("id"),
                        deviceIdentifier = o.optString("device_identifier"),
                        platform = o.optString("platform"),
                        appVersion = o.optString("app_version").takeIf { it.isNotBlank() && it != "null" },
                        lastSeenAtIso = o.optString("last_seen_at", "")
                    )
                )
            }
            list
        }
    }

    fun upsertSmsSyncState(
        accessToken: String,
        userId: String,
        deviceId: String,
        historicalScanStatus: String,
        monitoringStatus: String
    ): ApiResult<Unit> {
        val body = JSONObject().apply {
            put("user_id", userId)
            put("device_id", deviceId)
            put("historical_scan_status", historicalScanStatus)
            put("monitoring_status", monitoringStatus)
            put("last_processed_at", BankSmsParser.toUtcIso(System.currentTimeMillis()))
            put("updated_at", BankSmsParser.toUtcIso(System.currentTimeMillis()))
        }
        val req = authedBuilder("$baseUrl/rest/v1/sms_sync_state?on_conflict=user_id,device_id", accessToken)
            .addHeader("Prefer", "resolution=merge-duplicates")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        return executeVoid(req)
    }

    fun fetchSmsLogs(accessToken: String, userId: String, limit: Int = 50): ApiResult<List<SmsLogEntity>> {
        val req = authedBuilder(
            "$baseUrl/rest/v1/sms_processing_logs?user_id=eq.$userId&select=*&order=created_at.desc&limit=$limit",
            accessToken
        ).get().build()
        return executeArray(req) { arr ->
            val list = ArrayList<SmsLogEntity>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    SmsLogEntity(
                        id = o.optString("id"),
                        deviceId = o.optString("device_id").takeIf { it.isNotBlank() && it != "null" },
                        sender = o.optString("sender").takeIf { it.isNotBlank() && it != "null" },
                        status = o.optString("status", "PROCESSED"),
                        reason = o.optString("reason").takeIf { it.isNotBlank() && it != "null" },
                        extractedType = o.optString("extracted_type").takeIf { it.isNotBlank() && it != "null" },
                        extractedAmount = if (o.isNull("extracted_amount")) null else o.optDouble("extracted_amount"),
                        extractedBank = o.optString("extracted_bank").takeIf { it.isNotBlank() && it != "null" },
                        extractedDateIso = o.optString("extracted_date").takeIf { it.isNotBlank() && it != "null" },
                        referenceId = o.optString("reference_id").takeIf { it.isNotBlank() && it != "null" },
                        smsFingerprint = o.optString("sms_fingerprint").takeIf { it.isNotBlank() && it != "null" },
                        createdAtMs = BankSmsParser.parseIsoToMillis(o.optString("created_at", ""))
                    )
                )
            }
            list
        }
    }

    fun insertSmsLog(accessToken: String, userId: String, log: SmsLogEntity): ApiResult<Unit> {
        val body = JSONObject().apply {
            put("user_id", userId)
            put("device_id", log.deviceId ?: "android-native")
            put("sender", log.sender ?: JSONObject.NULL)
            put("status", log.status)
            put("reason", log.reason ?: JSONObject.NULL)
            put("extracted_type", log.extractedType ?: JSONObject.NULL)
            put("extracted_amount", log.extractedAmount ?: JSONObject.NULL)
            put("extracted_bank", log.extractedBank ?: JSONObject.NULL)
            put("extracted_date", log.extractedDateIso ?: JSONObject.NULL)
            put("reference_id", log.referenceId ?: JSONObject.NULL)
            put("sms_fingerprint", log.smsFingerprint ?: JSONObject.NULL)
        }
        val req = authedBuilder("$baseUrl/rest/v1/sms_processing_logs", accessToken)
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        return executeVoid(req)
    }

    // ── MONZI 2.1 SYNC ENDPOINTS (Budgets, Credit Cards, Adjustments) ──────

    fun fetchBudgets(accessToken: String, userId: String): ApiResult<List<BudgetEntity>> {
        val url = "$baseUrl/rest/v1/budgets?user_id=eq.$userId&select=*,categories(name,color)&order=created_at.desc"
        val req = authedBuilder(url, accessToken).get().build()
        return executeArray(req) { arr ->
            val list = ArrayList<BudgetEntity>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val cat = o.optJSONObject("categories")
                list.add(
                    BudgetEntity(
                        id = o.optString("id"),
                        userId = o.optString("user_id"),
                        name = o.optString("name"),
                        categoryId = o.optString("category_id").takeIf { it.isNotBlank() && it != "null" },
                        categoryName = cat?.optString("name")?.takeIf { it.isNotBlank() && it != "null" },
                        categoryColor = cat?.optString("color")?.takeIf { it.isNotBlank() && it != "null" },
                        monthlyLimit = o.optDouble("monthly_limit", 0.0),
                        startMonth = o.optString("start_month"),
                        alertThreshold = o.optInt("alert_threshold", 80),
                        carryForward = o.optBoolean("carry_forward", false),
                        status = o.optString("status", "ACTIVE"),
                        createdAtMs = BankSmsParser.parseIsoToMillis(o.optString("created_at", ""))
                    )
                )
            }
            list
        }
    }

    fun upsertBudget(accessToken: String, budget: BudgetEntity): ApiResult<Unit> {
        val body = JSONObject().apply {
            if (isUuid(budget.id)) put("id", budget.id)
            put("user_id", budget.userId)
            put("name", budget.name)
            put("category_id", budget.categoryId?.takeIf { isUuid(it) } ?: JSONObject.NULL)
            put("monthly_limit", budget.monthlyLimit)
            put("start_month", budget.startMonth)
            put("alert_threshold", budget.alertThreshold)
            put("carry_forward", budget.carryForward)
            put("status", budget.status)
        }
        val req = authedBuilder("$baseUrl/rest/v1/budgets?on_conflict=id", accessToken)
            .addHeader("Prefer", "resolution=merge-duplicates")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        return executeVoid(req)
    }

    fun deleteBudget(accessToken: String, userId: String, budgetId: String): ApiResult<Unit> {
        val req = authedBuilder(
            "$baseUrl/rest/v1/budgets?id=eq.$budgetId&user_id=eq.$userId",
            accessToken
        ).delete().build()
        return executeVoid(req)
    }

    fun upsertBalanceAdjustment(accessToken: String, adj: AccountBalanceAdjustmentEntity): ApiResult<Unit> {
        if (!isUuid(adj.accountId)) return ApiResult.Error("Account must be synced before adjustment", 400)
        val body = JSONObject().apply {
            if (isUuid(adj.id)) put("id", adj.id)
            put("user_id", adj.userId)
            put("account_id", adj.accountId)
            put("adjustment_amount", adj.adjustmentAmount)
            put("reason", adj.reason)
            put("approved", adj.approved)
        }
        val req = authedBuilder("$baseUrl/rest/v1/account_balance_adjustments", accessToken)
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        return executeVoid(req)
    }

    fun upsertCreditCardStatement(accessToken: String, stmt: CreditCardStatementEntity): ApiResult<Unit> {
        if (!isUuid(stmt.accountId)) return ApiResult.Error("Card must be synced before statement", 400)
        val body = JSONObject().apply {
            if (isUuid(stmt.id)) put("id", stmt.id)
            put("user_id", stmt.userId)
            put("account_id", stmt.accountId)
            put("statement_period", stmt.statementPeriod)
            put("statement_date", BankSmsParser.toUtcIso(stmt.statementDateMs).substring(0, 10))
            put("total_due", stmt.totalDue)
            put("minimum_due", stmt.minimumDue)
            if (stmt.dueDateMs != null) put("due_date", BankSmsParser.toUtcIso(stmt.dueDateMs).substring(0, 10))
            put("paid_amount", stmt.paidAmount)
            put("payment_status", stmt.paymentStatus)
            put("updated_at", BankSmsParser.toUtcIso(System.currentTimeMillis()))
        }
        val req = authedBuilder("$baseUrl/rest/v1/credit_card_statements?on_conflict=id", accessToken)
            .addHeader("Prefer", "resolution=merge-duplicates")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        return executeVoid(req)
    }

    fun insertCreditCardPayment(accessToken: String, pay: CreditCardPaymentEntity): ApiResult<Unit> {
        if (!isUuid(pay.cardAccountId)) return ApiResult.Error("Card must be synced before payment", 400)
        val body = JSONObject().apply {
            if (isUuid(pay.id)) put("id", pay.id)
            put("user_id", pay.userId)
            put("card_account_id", pay.cardAccountId)
            pay.sourceAccountId?.takeIf { isUuid(it) }?.let { put("source_account_id", it) }
            pay.statementId?.takeIf { isUuid(it) }?.let { put("statement_id", it) }
            put("amount", pay.amount)
            put("payment_date", BankSmsParser.toUtcIso(pay.paymentDateMs))
        }
        val req = authedBuilder("$baseUrl/rest/v1/credit_card_payments", accessToken)
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        return executeVoid(req)
    }

    fun fetchCreditCardStatements(accessToken: String, userId: String): ApiResult<List<CreditCardStatementEntity>> {
        val url = "$baseUrl/rest/v1/credit_card_statements?user_id=eq.$userId&select=*&order=statement_date.desc"
        val req = authedBuilder(url, accessToken).get().build()
        return executeArrayAllowMissingTable(req) { arr ->
            val list = ArrayList<CreditCardStatementEntity>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    CreditCardStatementEntity(
                        id = o.optString("id"),
                        userId = o.optString("user_id"),
                        accountId = o.optString("account_id"),
                        statementPeriod = o.optString("statement_period"),
                        statementDateMs = BankSmsParser.parseIsoToMillis(o.optString("statement_date", "")),
                        totalDue = o.optDouble("total_due", 0.0),
                        minimumDue = o.optDouble("minimum_due", 0.0),
                        dueDateMs = o.optString("due_date", "").takeIf { it.isNotBlank() && it != "null" }?.let { BankSmsParser.parseIsoToMillis(it) },
                        paidAmount = o.optDouble("paid_amount", 0.0),
                        paymentStatus = o.optString("payment_status", "UPCOMING"),
                        updatedAtMs = BankSmsParser.parseIsoToMillis(o.optString("updated_at", ""))
                    )
                )
            }
            list
        }
    }

    fun fetchCreditCardPayments(accessToken: String, userId: String): ApiResult<List<CreditCardPaymentEntity>> {
        val url = "$baseUrl/rest/v1/credit_card_payments?user_id=eq.$userId&select=*&order=payment_date.desc"
        val req = authedBuilder(url, accessToken).get().build()
        return executeArrayAllowMissingTable(req) { arr ->
            val list = ArrayList<CreditCardPaymentEntity>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    CreditCardPaymentEntity(
                        id = o.optString("id"),
                        userId = o.optString("user_id"),
                        cardAccountId = o.optString("card_account_id"),
                        sourceAccountId = o.optString("source_account_id").takeIf { it.isNotBlank() && it != "null" },
                        statementId = o.optString("statement_id").takeIf { it.isNotBlank() && it != "null" },
                        transactionId = o.optString("transaction_id").takeIf { it.isNotBlank() && it != "null" },
                        amount = o.optDouble("amount", 0.0),
                        paymentDateMs = BankSmsParser.parseIsoToMillis(o.optString("payment_date", "")),
                        createdAtMs = BankSmsParser.parseIsoToMillis(o.optString("created_at", ""))
                    )
                )
            }
            list
        }
    }

    fun fetchBalanceAdjustments(accessToken: String, userId: String): ApiResult<List<AccountBalanceAdjustmentEntity>> {
        val url = "$baseUrl/rest/v1/account_balance_adjustments?user_id=eq.$userId&select=*&order=created_at.desc"
        val req = authedBuilder(url, accessToken).get().build()
        return executeArrayAllowMissingTable(req) { arr ->
            val list = ArrayList<AccountBalanceAdjustmentEntity>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    AccountBalanceAdjustmentEntity(
                        id = o.optString("id"),
                        userId = o.optString("user_id"),
                        accountId = o.optString("account_id"),
                        adjustmentAmount = o.optDouble("adjustment_amount", 0.0),
                        reason = o.optString("reason", ""),
                        approved = o.optBoolean("approved", true),
                        createdAtMs = BankSmsParser.parseIsoToMillis(o.optString("created_at", ""))
                    )
                )
            }
            list
        }
    }

    fun fetchBalanceSnapshots(accessToken: String, userId: String): ApiResult<List<AccountBalanceSnapshotEntity>> {
        val url = "$baseUrl/rest/v1/account_balance_snapshots?user_id=eq.$userId&select=*&order=snapshot_at.desc&limit=200"
        val req = authedBuilder(url, accessToken).get().build()
        return executeArrayAllowMissingTable(req) { arr ->
            val list = ArrayList<AccountBalanceSnapshotEntity>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    AccountBalanceSnapshotEntity(
                        id = o.optString("id"),
                        userId = o.optString("user_id"),
                        accountId = o.optString("account_id"),
                        reportedBalance = o.optDouble("reported_balance", 0.0),
                        calculatedBalanceAtSnapshot = o.optDouble("calculated_balance_at_snapshot", 0.0),
                        discrepancyAmount = o.optDouble("discrepancy_amount", 0.0),
                        sourceTransactionId = o.optString("source_transaction_id").takeIf { it.isNotBlank() && it != "null" },
                        snapshotAtMs = BankSmsParser.parseIsoToMillis(o.optString("snapshot_at", "")),
                        createdAtMs = BankSmsParser.parseIsoToMillis(o.optString("created_at", ""))
                    )
                )
            }
            list
        }
    }

    fun resetUserData(accessToken: String, userId: String): ApiResult<Unit> {
        // 1. Clear self-referencing linked_transaction_id so transactions delete cleanly
        val clearLinksReq = authedBuilder(
            "$baseUrl/rest/v1/transactions?user_id=eq.$userId",
            accessToken
        ).addHeader("Prefer", "return=minimal")
            .patch("{\"linked_transaction_id\":null}".toRequestBody(jsonMedia))
            .build()
        val clearLinksRes = executeVoid(clearLinksReq)
        if (clearLinksRes is ApiResult.Error && clearLinksRes.code == 401) {
            return clearLinksRes
        }

        // 2. Delete dependent & secondary tables in FK order (allow 404 only if migration not applied)
        val secondaryTables = listOf(
            "budget_alerts",
            "budgets",
            "credit_card_payments",
            "credit_card_statements",
            "account_balance_adjustments",
            "account_balance_snapshots",
            "refunds",
            "transfers",
            "transaction_rules",
            "sync_queue",
            "sms_import_runs",
            "sms_parser_failures",
            "automation_logs",
            "sms_sync_state",
            "sms_processing_logs"
        )
        for (table in secondaryTables) {
            val req = authedBuilder("$baseUrl/rest/v1/$table?user_id=eq.$userId", accessToken)
                .addHeader("Prefer", "return=minimal")
                .delete()
                .build()
            val res = executeVoid(req)
            if (res is ApiResult.Error && res.code != 404) {
                if (res.code == 401 || res.code == 403 || res.code == 0 || res.code >= 500) {
                    return res
                }
            }
        }

        // 3. Delete transactions first (FK to accounts), then accounts
        val delTx = authedBuilder("$baseUrl/rest/v1/transactions?user_id=eq.$userId", accessToken)
            .addHeader("Prefer", "return=minimal")
            .delete()
            .build()
        val r1 = executeVoid(delTx)
        if (r1 is ApiResult.Error) return r1

        val delAcc = authedBuilder("$baseUrl/rest/v1/accounts?user_id=eq.$userId", accessToken)
            .addHeader("Prefer", "return=minimal")
            .delete()
            .build()
        val r2 = executeVoid(delAcc)
        if (r2 is ApiResult.Error) return r2

        return ApiResult.Success(Unit)
    }

    // ── HELPERS ──────────────────────────────────────────────────────────────

    private fun authedBuilder(url: String, accessToken: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
    }

    private fun <T> executeArrayAllowMissingTable(req: Request, mapper: (JSONArray) -> List<T>): ApiResult<List<T>> {
        return try {
            client.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (res.code == 404) {
                    return ApiResult.Success(emptyList())
                }
                if (!res.isSuccessful) {
                    return ApiResult.Error(extractErrorMessage(raw, "Request failed"), res.code)
                }
                ApiResult.Success(mapper(JSONArray(raw)))
            }
        } catch (e: Exception) {
            Log.e("SupabaseApi", "executeArrayAllowMissingTable error", e)
            ApiResult.Error(e.message ?: "Network error")
        }
    }

    private fun <T> executeArray(req: Request, mapper: (JSONArray) -> T): ApiResult<T> {
        return try {
            client.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    return ApiResult.Error(extractErrorMessage(raw, "Request failed"), res.code)
                }
                ApiResult.Success(mapper(JSONArray(raw)))
            }
        } catch (e: Exception) {
            Log.e("SupabaseApi", "executeArray error", e)
            ApiResult.Error(e.message ?: "Network error")
        }
    }

    private fun <T> executeSingleFromArray(req: Request, mapper: (JSONObject) -> T): ApiResult<T> {
        return try {
            client.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    return ApiResult.Error(extractErrorMessage(raw, "Request failed"), res.code)
                }
                val arr = JSONArray(raw)
                if (arr.length() == 0) {
                    return ApiResult.Error("Empty response from server", res.code)
                }
                ApiResult.Success(mapper(arr.getJSONObject(0)))
            }
        } catch (e: Exception) {
            Log.e("SupabaseApi", "executeSingle error", e)
            ApiResult.Error(e.message ?: "Network error")
        }
    }

    private fun executeVoid(req: Request): ApiResult<Unit> {
        return try {
            client.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    return ApiResult.Error(extractErrorMessage(raw, "Request failed"), res.code)
                }
                ApiResult.Success(Unit)
            }
        } catch (e: Exception) {
            Log.e("SupabaseApi", "executeVoid error", e)
            ApiResult.Error(e.message ?: "Network error")
        }
    }

    private fun extractErrorMessage(raw: String, fallback: String): String {
        if (raw.isBlank()) return fallback
        return try {
            val json = JSONObject(raw)
            json.optString("error_description")
                .ifBlank { json.optString("msg") }
                .ifBlank { json.optString("message") }
                .ifBlank { json.optString("error") }
                .ifBlank { fallback }
        } catch (_: Exception) {
            fallback
        }
    }

    private fun inferAccountType(bankName: String, merchant: String?): String {
        val text = "$bankName ${merchant ?: ""}".lowercase()
        if (Regex("credit card|cc bill|cc payment").containsMatchIn(text)) return "CREDIT_CARD"
        if (Regex("phonepe|google pay|paytm|amazon pay").containsMatchIn(text)) return "WALLET"
        return "SAVINGS"
    }

    private fun isUuid(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return UUID_REGEX.matches(value.trim())
    }

    private fun toSupabaseTransactionType(type: String): String {
        return when (type.uppercase()) {
            "EXPENSE", "INCOME", "TRANSFER", "REFUND", "INVESTMENT", "CC_BILL_PAYMENT", "CASH_WITHDRAWAL", "UNCLASSIFIED" -> type.uppercase()
            "REVERSAL" -> "REFUND"
            "CC_PURCHASE", "BANK_CHARGES" -> "EXPENSE"
            "ATM_WITHDRAWAL" -> "CASH_WITHDRAWAL"
            "INTEREST", "CASH_DEPOSIT" -> "INCOME"
            else -> "UNCLASSIFIED"
        }
    }

    private fun generateRandomSyncToken(): String {
        val bytes = ByteArray(24)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")
    }
}
