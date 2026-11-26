package com.krunal.monzi.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.WorkManager
import com.krunal.monzi.BankSmsParser
import com.krunal.monzi.BuildConfig
import com.krunal.monzi.InboxScanResult
import com.krunal.monzi.LocalFinanceDbHelper
import com.krunal.monzi.SmsInboxScanner
import com.krunal.monzi.SyncWorker
import com.krunal.monzi.data.engines.BudgetAndCardEngine
import com.krunal.monzi.data.engines.PostIngestProcessor
import com.krunal.monzi.data.engines.ReconciliationEngine
import com.krunal.monzi.util.IstTime
import com.krunal.monzi.util.MoneyMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

class MonziRepository(
    private val context: Context,
    val api: SupabaseApiClient = SupabaseApiClient(),
    private val onlineOverride: (() -> Boolean)? = null
) {

    val appContext: Context = context.applicationContext
    val db: LocalFinanceDbHelper = LocalFinanceDbHelper.getInstance(appContext)
    val sessionMgr: AuthSessionManager = AuthSessionManager(appContext)

    val dbChanges = db.changes

    fun isOnline(): Boolean {
        onlineOverride?.let { return it() }
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ── AUTH OPERATIONS ──────────────────────────────────────────────────────

    suspend fun signIn(email: String, password: String, rememberEmail: Boolean): ApiResult<UserProfile> =
        withContext(Dispatchers.IO) {
            when (val authRes = api.signIn(email, password)) {
                is ApiResult.Error -> authRes
                is ApiResult.Success -> {
                    val session = authRes.data
                    sessionMgr.saveSession(session)
                    sessionMgr.setSessionLocked(false)
                    sessionMgr.setRememberedEmail(if (rememberEmail) email else null)
                    val profile = when (val profRes = api.fetchOrProvisionProfile(session.accessToken, session.userId, session.email)) {
                        is ApiResult.Success -> profRes.data
                        is ApiResult.Error -> UserProfile(
                            id = session.userId,
                            fullName = session.email.substringBefore("@"),
                            email = session.email
                        )
                    }
                    sessionMgr.saveProfile(profile)
                    SyncWorker.schedulePeriodicRetry(appContext)
                    if (db.getPendingCount() > 0) {
                        SyncWorker.enqueuePendingFlush(appContext)
                    }
                    syncAllFromCloud()
                    ApiResult.Success(profile)
                }
            }
        }

    suspend fun signUp(fullName: String, email: String, password: String): ApiResult<Boolean> =
        withContext(Dispatchers.IO) {
            when (val res = api.signUp(fullName, email, password)) {
                is ApiResult.Error -> res
                is ApiResult.Success -> {
                    val (session, needsEmailVerification) = res.data
                    if (session != null) {
                        sessionMgr.saveSession(session)
                        val profile = UserProfile(
                            id = session.userId,
                            fullName = fullName.trim(),
                            email = session.email
                        )
                        api.upsertProfile(session.accessToken, profile)
                        sessionMgr.saveProfile(profile)
                        syncAllFromCloud()
                    }
                    ApiResult.Success(needsEmailVerification)
                }
            }
        }

    suspend fun resendVerification(email: String): ApiResult<Unit> =
        withContext(Dispatchers.IO) { api.resendVerification(email) }

    suspend fun recoverPassword(email: String): ApiResult<Unit> =
        withContext(Dispatchers.IO) { api.recoverPassword(email) }

    suspend fun verifyPasswordForUnlock(password: String): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            val email = sessionMgr.getSession()?.email ?: sessionMgr.getCachedProfile()?.email.orEmpty()
            if (email.isBlank()) return@withContext ApiResult.Error("No email found for current session")
            when (val res = api.signIn(email, password)) {
                is ApiResult.Success -> {
                    sessionMgr.saveSession(res.data)
                    sessionMgr.setSessionLocked(false)
                    ApiResult.Success(Unit)
                }
                is ApiResult.Error -> res
            }
        }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val token = sessionMgr.getSession()?.accessToken
        if (!token.isNullOrBlank()) {
            api.signOut(token)
        }
        sessionMgr.clearSession()
        db.clearAllUserFinanceData()
    }

    // ── CLOUD SYNC (PULL + PUSH) ─────────────────────────────────────────────

    suspend fun syncAllFromCloud(): ApiResult<Unit> = withContext(Dispatchers.IO) {
        if (!isOnline()) return@withContext ApiResult.Error("Offline mode")
        val token = sessionMgr.getValidAccessToken(api) ?: return@withContext ApiResult.Error("Session expired", 401)
        val userId = sessionMgr.getUserId()
        if (userId.isBlank()) return@withContext ApiResult.Error("Not signed in", 401)

        // -1. Execute deferred offline Reset before pulling anything from cloud
        if (sessionMgr.isPendingCloudReset()) {
            val resetRes = api.resetUserData(token, userId)
            if (resetRes is ApiResult.Error) return@withContext resetRes
            sessionMgr.setPendingCloudReset(false)
        }

        // 0. Flush queued CRUD deletes/updates, pending accounts, rules, and pending SMS txns first
        flushQueuedOperations(token, userId)
        flushPendingAccounts(token, userId)
        flushPendingSyncDirect(token, userId)

        // 1. Profile
        val email = sessionMgr.getSession()?.email.orEmpty()
        val profRes = api.fetchOrProvisionProfile(token, userId, email)
        if (profRes is ApiResult.Success) {
            sessionMgr.saveProfile(profRes.data)
        }

        // 2. Register device heartbeat
        val deviceId = sessionMgr.getOrCreateDeviceIdentifier()
        api.registerDeviceHeartbeat(token, userId, deviceId, BuildConfig.APP_VERSION)

        // 3. Categories (pull first so local-cat-* IDs can be mapped to cloud UUIDs)
        val catRes = api.fetchCategories(token)
        if (catRes is ApiResult.Success) {
            db.replaceCategoriesFromCloud(catRes.data)
        }
        flushPendingCategories(token)

        // 4. Rules (push any pending local-rule-* items, then pull)
        flushPendingRules(token, userId)
        val rulesRes = api.fetchRules(token, userId)
        if (rulesRes is ApiResult.Success) {
            db.replaceRulesFromCloud(rulesRes.data)
        }

        // 5. Accounts
        val accRes = api.fetchAccounts(token, userId)
        if (accRes is ApiResult.Success) {
            db.replaceAccountsFromCloud(accRes.data)
        }

        // 6. Transactions
        val txRes = api.fetchTransactions(token, userId, limit = 2000)
        if (txRes is ApiResult.Success) {
            db.replaceSyncedTransactionsFromCloud(txRes.data)
        } else if (txRes is ApiResult.Error) {
            return@withContext txRes
        }

        // 7. SMS Processing Logs
        val logsRes = api.fetchSmsLogs(token, userId, limit = 100)
        if (logsRes is ApiResult.Success) {
            db.replaceSmsLogsFromCloud(logsRes.data)
        }

        // 8. Budgets, Credit Card Statements/Payments, Reconciliation Snapshots/Adjustments
        val budgetsRes = api.fetchBudgets(token, userId)
        if (budgetsRes is ApiResult.Success) {
            db.replaceBudgetsFromCloud(budgetsRes.data)
        }
        val stmtsRes = api.fetchCreditCardStatements(token, userId)
        if (stmtsRes is ApiResult.Success) {
            db.replaceStatementsFromCloud(stmtsRes.data)
        }
        val ccPayRes = api.fetchCreditCardPayments(token, userId)
        if (ccPayRes is ApiResult.Success) {
            db.replaceCcPaymentsFromCloud(ccPayRes.data)
        }
        val adjRes = api.fetchBalanceAdjustments(token, userId)
        if (adjRes is ApiResult.Success) {
            db.replaceAdjustmentsFromCloud(adjRes.data)
        }
        val snapRes = api.fetchBalanceSnapshots(token, userId)
        if (snapRes is ApiResult.Success) {
            db.replaceSnapshotsFromCloud(snapRes.data)
        }

        db.updateSyncState(lastSyncAt = System.currentTimeMillis())
        ApiResult.Success(Unit)
    }

    private fun resolveCloudCategoryId(categoryId: String?): String? {
        if (categoryId.isNullOrBlank()) return null
        if (!categoryId.startsWith("local-cat-")) return categoryId
        val localName = db.getCategories().firstOrNull { it.id == categoryId }?.name ?: return null
        return db.getCategories()
            .firstOrNull { !it.id.startsWith("local-cat-") && it.name.equals(localName, ignoreCase = true) }
            ?.id
    }

    private fun enqueueSyncOperation(entityType: String, entityId: String, operation: String, payload: JSONObject = JSONObject()) {
        db.upsertSyncQueueItem(
            SyncQueueItemEntity(
                id = "${entityType}_${operation}_${entityId}",
                entityType = entityType,
                entityId = entityId,
                operation = operation,
                payloadJson = payload.toString(),
                syncStatus = "PENDING",
                retryCount = 0,
                createdAtMs = System.currentTimeMillis()
            )
        )
    }

    private fun flushPendingAccounts(token: String, userId: String) {
        val pendingAccs = db.getPendingSyncAccounts()
        for (acc in pendingAccs) {
            val res = api.upsertAccount(token, acc.copy(userId = userId))
            if (res is ApiResult.Success) {
                db.remapLocalAccountId(acc.id, res.data)
            }
        }
    }

    private fun flushPendingCategories(token: String) {
        val localCats = db.getCategories().filter { it.id.startsWith("local-cat-") }
        for (cat in localCats) {
            val res = api.createCategory(token, cat.name, cat.color, cat.isIncome)
            if (res is ApiResult.Success) {
                db.upsertCategory(res.data)
            }
        }
    }

    private fun flushPendingRules(token: String, userId: String) {
        val localRules = db.getRules().filter { it.id.startsWith("local-rule-") }
        for (rule in localRules) {
            val cloudCatId = resolveCloudCategoryId(rule.categoryId) ?: continue
            val res = api.createRule(token, userId, rule.matchingRule, cloudCatId, rule.priority)
            if (res is ApiResult.Success) {
                db.deleteRuleLocal(rule.id)
                db.upsertRule(res.data)
            }
        }
    }

    private fun flushQueuedOperations(token: String, userId: String) {
        val items = db.getActionableSyncQueueItems()
        for (item in items) {
            val payload = try { JSONObject(item.payloadJson) } catch (_: Exception) { JSONObject() }
            val result: ApiResult<Unit> = when (item.entityType) {
                "TRANSACTION" -> when (item.operation) {
                    "DELETE" -> api.deleteTransaction(token, userId, item.entityId)
                    "UPDATE" -> api.updateTransaction(
                        accessToken = token,
                        userId = userId,
                        remoteId = item.entityId,
                        categoryId = payload.optString("category_id").takeIf { it.isNotBlank() && it != "null" },
                        notes = payload.optString("notes").takeIf { it.isNotBlank() && it != "null" },
                        merchant = payload.optString("merchant").takeIf { it.isNotBlank() && it != "null" },
                        transactionType = payload.optString("transaction_type").takeIf { it.isNotBlank() && it != "null" },
                        amount = if (payload.has("amount")) payload.optDouble("amount") else null,
                        direction = payload.optString("direction").takeIf { it.isNotBlank() && it != "null" },
                        accountId = payload.optString("account_id").takeIf { it.isNotBlank() && it != "null" }
                    )
                    else -> ApiResult.Success(Unit)
                }
                "ACCOUNT" -> when (item.operation) {
                    "DELETE" -> api.deleteAccount(token, userId, item.entityId)
                    "UPDATE" -> {
                        val acc = db.getAccountById(item.entityId)
                        if (acc != null && !acc.id.startsWith("local-acc-")) api.updateAccount(token, acc)
                        else ApiResult.Success(Unit)
                    }
                    else -> ApiResult.Success(Unit)
                }
                "BUDGET" -> when (item.operation) {
                    "DELETE" -> api.deleteBudget(token, userId, item.entityId)
                    "UPSERT" -> {
                        val b = db.getAllBudgets().firstOrNull { it.id == item.entityId }
                        if (b != null) api.upsertBudget(token, b.copy(categoryId = resolveCloudCategoryId(b.categoryId)))
                        else ApiResult.Success(Unit)
                    }
                    else -> ApiResult.Success(Unit)
                }
                "RULE" -> when (item.operation) {
                    "DELETE" -> api.deleteRule(token, userId, item.entityId)
                    "UPDATE" -> {
                        val r = db.getRules().firstOrNull { it.id == item.entityId }
                        val catId = r?.let { resolveCloudCategoryId(it.categoryId) }
                        if (r != null && catId != null && !r.id.startsWith("local-rule-")) {
                            api.updateRule(token, userId, r.id, r.matchingRule, catId, r.priority, r.enabled)
                        } else ApiResult.Success(Unit)
                    }
                    else -> ApiResult.Success(Unit)
                }
                else -> ApiResult.Success(Unit)
            }
            if (result is ApiResult.Success || (result is ApiResult.Error && result.code == 404)) {
                db.deleteSyncQueueItem(item.id)
            }
        }
    }

    suspend fun flushPendingNow(): Int = withContext(Dispatchers.IO) {
        if (!isOnline()) return@withContext 0
        val token = sessionMgr.getValidAccessToken(api)
        val userId = sessionMgr.getUserId()
        if (!token.isNullOrBlank() && userId.isNotBlank()) {
            flushQueuedOperations(token, userId)
            flushPendingAccounts(token, userId)
            flushPendingCategories(token)
            flushPendingRules(token, userId)
        }
        val count = flushPendingSyncDirect(token, userId)
        if (count > 0 && !token.isNullOrBlank()) {
            syncAllFromCloud()
        }
        count
    }

    private fun flushPendingSyncDirect(accessToken: String?, userId: String): Int {
        val pending = db.getPendingTransactions(200)
        if (pending.isEmpty()) return 0

        var syncToken = sessionMgr.getDeviceSyncToken().trim()
        if (syncToken.isEmpty() && !accessToken.isNullOrBlank() && userId.isNotBlank()) {
            val p = api.fetchOrProvisionProfile(accessToken, userId, sessionMgr.getSession()?.email.orEmpty())
            if (p is ApiResult.Success) {
                sessionMgr.saveProfile(p.data)
                syncToken = p.data.deviceSyncToken.trim()
            }
        }
        if (syncToken.isEmpty() && accessToken.isNullOrBlank()) return 0

        val deviceId = sessionMgr.getOrCreateDeviceIdentifier()
        val catByName = db.getCategories().associate { it.name.lowercase() to it.id }

        var synced = 0
        for (row in pending) {
            val catId = row.categoryId?.takeIf { !it.startsWith("local-cat-") }
                ?: row.categoryName?.lowercase()?.let { catByName[it] }

            val res = if (syncToken.isNotEmpty()) {
                api.deviceSyncIngestTransaction(syncToken, deviceId, row, catId)
            } else {
                when (val r = api.insertManualTransaction(accessToken!!, row.copy(userId = userId, categoryId = catId))) {
                    is ApiResult.Success -> ApiResult.Success("PROCESSED")
                    is ApiResult.Error -> r
                }
            }
            if (res is ApiResult.Success) {
                db.markSyncStatus(row.localId, LocalFinanceDbHelper.STATUS_SYNCED, null)
                synced++
            } else if (res is ApiResult.Error) {
                db.markSyncStatus(row.localId, LocalFinanceDbHelper.STATUS_RETRY_SCHEDULED, "HTTP_${res.code}")
            }
        }
        if (synced > 0) {
            db.updateSyncState(lastSyncAt = System.currentTimeMillis())
        }
        return synced
    }

    // ── SMS OPERATIONS ───────────────────────────────────────────────────────

    fun previewSmsCount(startDateMs: Long? = null, endDateMs: Long? = null): Int =
        SmsInboxScanner.previewCount(appContext, startDateMs, endDateMs)

    suspend fun scanSmsInbox(
        startDateMs: Long? = null,
        endDateMs: Long? = null,
        limit: Int = 1000,
        fullRescan: Boolean = false,
        dateRangeLabel: String = "Selected Range"
    ): InboxScanResult =
        withContext(Dispatchers.IO) {
            val result = SmsInboxScanner.scanBlocking(
                context = appContext,
                startDateMs = startDateMs,
                endDateMs = endDateMs,
                limit = limit,
                fullRescan = fullRescan,
                dateRangeLabel = dateRangeLabel
            )
            if (result.newlyQueued > 0 && isOnline()) {
                flushPendingNow()
            }
            val token = sessionMgr.getValidAccessToken(api)
            val userId = sessionMgr.getUserId()
            if (!token.isNullOrBlank() && userId.isNotBlank() && isOnline()) {
                api.upsertSmsSyncState(
                    accessToken = token,
                    userId = userId,
                    deviceId = sessionMgr.getOrCreateDeviceIdentifier(),
                    historicalScanStatus = db.getSyncState().historicalScanStatus,
                    monitoringStatus = db.getSyncState().monitoringStatus
                )
            }
            result
        }

    suspend fun reprocessDateRange(
        startDateMs: Long,
        endDateMs: Long
    ): com.krunal.monzi.ReprocessResult = withContext(Dispatchers.IO) {
        val res = SmsInboxScanner.reprocessBlocking(appContext, startDateMs, endDateMs)
        if (res.updatedCount > 0 && isOnline()) {
            flushPendingNow()
        }
        res
    }

    suspend fun ingestManualSms(rawText: String): ApiResult<String> = withContext(Dispatchers.IO) {
        val inspection = BankSmsParser.inspect("", rawText, System.currentTimeMillis())
        val parsed = inspection.parsed
            ?: return@withContext ApiResult.Error(inspection.reason ?: "Not a valid bank SMS")

        val fp = BankSmsParser.computeFingerprint(parsed)
        val userId = sessionMgr.getUserId()
        val savedTxn = db.insertPendingTransactionReturningEntity(
            parsed = parsed,
            sender = parsed.bankName,
            transactionSource = LocalFinanceDbHelper.SOURCE_INCOMING_SMS,
            smsFingerprint = fp,
            userId = userId,
            rawBody = rawText
        ) ?: return@withContext ApiResult.Success("Duplicate transaction skipped")
        PostIngestProcessor.processTransaction(appContext, db, savedTxn, userId)
        if (isOnline()) {
            flushPendingNow()
        } else {
            SyncWorker.enqueuePendingFlush(appContext)
        }
        ApiResult.Success("Saved ₹${parsed.amount.toInt()} (${parsed.bankName})")
    }

    suspend fun ingestBulkSms(rawBlocks: List<String>): ApiResult<Pair<Int, Int>> =
        withContext(Dispatchers.IO) {
            val userId = sessionMgr.getUserId()
            var added = 0
            var duplicates = 0
            for (block in rawBlocks) {
                val parsed = BankSmsParser.parse("", block, System.currentTimeMillis()) ?: continue
                val fp = BankSmsParser.computeFingerprint(parsed)
                val ok = db.insertPendingTransactionReturningEntity(
                    parsed = parsed,
                    sender = parsed.bankName,
                    transactionSource = LocalFinanceDbHelper.SOURCE_INCOMING_SMS,
                    smsFingerprint = fp,
                    userId = userId,
                    rawBody = block
                )
                if (ok != null) {
                    PostIngestProcessor.processTransaction(appContext, db, ok, userId)
                    added++
                } else duplicates++
            }
            if (added > 0) {
                if (isOnline()) flushPendingNow() else SyncWorker.enqueuePendingFlush(appContext)
            }
            ApiResult.Success(added to duplicates)
        }

    // ── TRANSACTIONS CRUD ────────────────────────────────────────────────────

    suspend fun createManualTransaction(
        amount: Double,
        direction: String,
        merchant: String,
        accountId: String?,
        categoryId: String?,
        paymentMode: String,
        notes: String?,
        transactionDateMs: Long
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val cleanAmt = MoneyMath.round2(amount)
        if (cleanAmt <= 0.0 || cleanAmt > 100_000_000.0) {
            return@withContext ApiResult.Error("Amount must be between ₹1 and ₹10,00,00,000")
        }
        if (direction != "CREDIT" && direction != "DEBIT") {
            return@withContext ApiResult.Error("Direction must be CREDIT or DEBIT")
        }

        val userId = sessionMgr.getUserId()
        val dateIso = BankSmsParser.toUtcIso(transactionDateMs)
        val ref = "MANUAL_${System.currentTimeMillis()}"
        val fpInput = "${userId}_${dateIso}_${cleanAmt}_${direction}_${ref}_${UUID.randomUUID()}"
        val fp = MessageDigest.getInstance("SHA-256")
            .digest(fpInput.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        var acc = accountId?.let { db.getAccountById(it) }
        val cat = db.getCategories().firstOrNull { it.id == categoryId }
        val cloudCategoryId = resolveCloudCategoryId(cat?.id)
        val txType = if (direction == "CREDIT") "INCOME" else "EXPENSE"

        val token = sessionMgr.getValidAccessToken(api)
        if (acc != null && acc.id.startsWith("local-acc-") && !token.isNullOrBlank() && userId.isNotBlank() && isOnline()) {
            val accSyncRes = api.upsertAccount(token, acc.copy(userId = userId))
            if (accSyncRes is ApiResult.Success) {
                db.remapLocalAccountId(acc.id, accSyncRes.data)
                acc = accSyncRes.data
            }
        }

        val entity = TransactionEntity(
            localId = UUID.randomUUID().toString(),
            remoteId = null,
            userId = userId,
            accountId = acc?.id,
            categoryId = cloudCategoryId ?: cat?.id,
            smsFingerprint = fp,
            amount = cleanAmt,
            direction = direction,
            transactionType = txType,
            bankName = acc?.bankName ?: "Manual",
            accountMask = acc?.accountNumberMask ?: "XX0000",
            merchant = merchant.trim().ifBlank { if (direction == "CREDIT") "Manual Income" else "Manual Expense" },
            referenceId = ref,
            paymentMode = paymentMode,
            isExcludedFromTotals = false,
            notes = notes?.trim()?.takeIf { it.isNotBlank() },
            transactionSource = LocalFinanceDbHelper.SOURCE_MANUAL,
            transactionDateIso = dateIso,
            transactionDateMs = transactionDateMs,
            syncStatus = LocalFinanceDbHelper.STATUS_PENDING,
            categoryName = cat?.name,
            categoryColor = cat?.color,
            accountDisplayName = acc?.let { "${it.bankName} (${it.accountNumberMask})" } ?: "Manual"
        )

        db.insertLocalTransaction(entity)
        PostIngestProcessor.processTransaction(appContext, db, entity, userId)

        var nextBal: Double? = null
        if (acc != null) {
            val computed = if (acc.accountType == "CREDIT_CARD") {
                if (direction == "DEBIT") MoneyMath.add(acc.currentBalance, cleanAmt)
                else maxOf(0.0, MoneyMath.subtract(acc.currentBalance, cleanAmt))
            } else {
                if (direction == "CREDIT") MoneyMath.add(acc.currentBalance, cleanAmt)
                else MoneyMath.subtract(acc.currentBalance, cleanAmt)
            }
            nextBal = computed
            db.upsertAccountLocal(acc.copy(currentBalance = computed, updatedAtMs = System.currentTimeMillis()))
        }

        if (!token.isNullOrBlank() && userId.isNotBlank() && isOnline()) {
            val cloudPayload = entity.copy(
                accountId = acc?.id?.takeIf { !it.startsWith("local-acc-") },
                categoryId = cloudCategoryId
            )
            val cloudRes = api.insertManualTransaction(token, cloudPayload)
            if (cloudRes is ApiResult.Success) {
                db.replaceLocalTransactionWithCloud(entity.localId, cloudRes.data)
                if (acc != null && nextBal != null && !acc.id.startsWith("local-acc-")) {
                    api.updateAccountBalance(token, userId, acc.id, nextBal, acc.balanceSource)
                }
            }
        }
        ApiResult.Success(Unit)
    }

    suspend fun updateTransaction(
        txn: TransactionEntity,
        categoryId: String?,
        notes: String?,
        merchant: String?,
        transactionType: String?,
        amount: Double? = null,
        direction: String? = null,
        accountId: String? = null,
        transactionDateMs: Long? = null
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val nextAmount = amount?.let { MoneyMath.round2(it) } ?: txn.amount
        if (nextAmount <= 0.0 || nextAmount > 100_000_000.0) {
            return@withContext ApiResult.Error("Amount must be between ₹1 and ₹10,00,00,000")
        }
        val nextDirection = direction ?: txn.direction
        val nextAccountId = accountId ?: txn.accountId
        val nextDateMs = transactionDateMs ?: txn.transactionDateMs
        val nextDateIso = if (transactionDateMs != null && transactionDateMs != txn.transactionDateMs) {
            BankSmsParser.toUtcIso(transactionDateMs)
        } else null
        val targetAcc = nextAccountId?.let { db.getAccountById(it) }
        val accountChanged = nextAccountId != txn.accountId
        val cat = db.getCategories().firstOrNull { it.id == categoryId }
        val cloudCategoryId = resolveCloudCategoryId(categoryId)

        db.updateTransactionLocal(
            localId = txn.localId,
            categoryId = cloudCategoryId ?: categoryId,
            categoryName = cat?.name,
            categoryColor = cat?.color,
            notes = notes,
            merchant = merchant,
            transactionType = transactionType,
            amount = nextAmount,
            direction = nextDirection,
            accountId = if (accountChanged) (nextAccountId ?: "") else null,
            bankName = if (accountChanged) targetAcc?.bankName else null,
            accountMask = if (accountChanged) targetAcc?.accountNumberMask else null,
            accountDisplayName = if (accountChanged && targetAcc != null) "${targetAcc.bankName} (${targetAcc.accountNumberMask})" else null,
            transactionDateMs = if (transactionDateMs != null && transactionDateMs != txn.transactionDateMs) nextDateMs else null,
            transactionDateIso = nextDateIso
        )

        // If account, amount, or direction changed, reconcile impacted account balance(s) accurately
        val oldAcc = txn.accountId?.let { db.getAccountById(it) }
        val changedFinancial = accountChanged || nextAmount != txn.amount || nextDirection != txn.direction
        val updatedAccounts = mutableListOf<Pair<AccountEntity, Double>>()

        if (changedFinancial) {
            if (accountChanged) {
                if (oldAcc != null) {
                    val reverted = if (oldAcc.accountType == "CREDIT_CARD") {
                        if (txn.direction == "DEBIT") maxOf(0.0, MoneyMath.subtract(oldAcc.currentBalance, txn.amount))
                        else MoneyMath.add(oldAcc.currentBalance, txn.amount)
                    } else {
                        if (txn.direction == "DEBIT") MoneyMath.add(oldAcc.currentBalance, txn.amount)
                        else MoneyMath.subtract(oldAcc.currentBalance, txn.amount)
                    }
                    db.upsertAccountLocal(oldAcc.copy(currentBalance = reverted, updatedAtMs = System.currentTimeMillis()))
                    updatedAccounts.add(oldAcc to reverted)
                }
                if (targetAcc != null) {
                    val applied = if (targetAcc.accountType == "CREDIT_CARD") {
                        if (nextDirection == "DEBIT") MoneyMath.add(targetAcc.currentBalance, nextAmount)
                        else maxOf(0.0, MoneyMath.subtract(targetAcc.currentBalance, nextAmount))
                    } else {
                        if (nextDirection == "CREDIT") MoneyMath.add(targetAcc.currentBalance, nextAmount)
                        else MoneyMath.subtract(targetAcc.currentBalance, nextAmount)
                    }
                    db.upsertAccountLocal(targetAcc.copy(currentBalance = applied, updatedAtMs = System.currentTimeMillis()))
                    updatedAccounts.add(targetAcc to applied)
                }
            } else if (oldAcc != null) {
                val afterRevert = if (oldAcc.accountType == "CREDIT_CARD") {
                    if (txn.direction == "DEBIT") maxOf(0.0, MoneyMath.subtract(oldAcc.currentBalance, txn.amount))
                    else MoneyMath.add(oldAcc.currentBalance, txn.amount)
                } else {
                    if (txn.direction == "DEBIT") MoneyMath.add(oldAcc.currentBalance, txn.amount)
                    else MoneyMath.subtract(oldAcc.currentBalance, txn.amount)
                }
                val afterApply = if (oldAcc.accountType == "CREDIT_CARD") {
                    if (nextDirection == "DEBIT") MoneyMath.add(afterRevert, nextAmount)
                    else maxOf(0.0, MoneyMath.subtract(afterRevert, nextAmount))
                } else {
                    if (nextDirection == "CREDIT") MoneyMath.add(afterRevert, nextAmount)
                    else MoneyMath.subtract(afterRevert, nextAmount)
                }
                db.upsertAccountLocal(oldAcc.copy(currentBalance = afterApply, updatedAtMs = System.currentTimeMillis()))
                updatedAccounts.add(oldAcc to afterApply)
            }
        }

        val token = sessionMgr.getValidAccessToken(api)
        val userId = sessionMgr.getUserId()
        if (txn.syncStatus == LocalFinanceDbHelper.STATUS_SYNCED && userId.isNotBlank()) {
            var remoteId = txn.remoteId
            if (!token.isNullOrBlank() && isOnline()) {
                if (remoteId.isNullOrBlank()) {
                    val found = api.findTransactionIdByFingerprint(token, userId, txn.smsFingerprint)
                    if (found is ApiResult.Success && !found.data.isNullOrBlank()) {
                        remoteId = found.data
                        db.markSyncStatus(txn.localId, LocalFinanceDbHelper.STATUS_SYNCED, null, remoteId = found.data)
                    }
                }
                val targetId = remoteId ?: txn.localId
                val res = api.updateTransaction(
                    accessToken = token,
                    userId = userId,
                    remoteId = targetId,
                    categoryId = cloudCategoryId,
                    notes = notes,
                    merchant = merchant,
                    transactionType = transactionType,
                    amount = nextAmount,
                    direction = nextDirection,
                    accountId = if (accountChanged) nextAccountId else null,
                    transactionDateIso = nextDateIso
                )
                for ((accItem, newBal) in updatedAccounts) {
                    if (!accItem.id.startsWith("local-acc-")) {
                        api.updateAccountBalance(token, userId, accItem.id, newBal, accItem.balanceSource)
                    }
                }
                if (res is ApiResult.Error) {
                    enqueueUpdateTx(targetId, cloudCategoryId, notes, merchant, transactionType, nextAmount, nextDirection)
                }
            } else {
                enqueueUpdateTx(remoteId ?: txn.localId, cloudCategoryId, notes, merchant, transactionType, nextAmount, nextDirection)
            }
        }
        ApiResult.Success(Unit)
    }

    private fun enqueueUpdateTx(
        targetId: String,
        categoryId: String?,
        notes: String?,
        merchant: String?,
        transactionType: String?,
        amount: Double,
        direction: String
    ) {
        val payload = JSONObject().apply {
            put("category_id", categoryId ?: JSONObject.NULL)
            put("notes", notes ?: JSONObject.NULL)
            if (!merchant.isNullOrBlank()) put("merchant", merchant)
            if (!transactionType.isNullOrBlank()) put("transaction_type", transactionType)
            put("amount", amount)
            put("direction", direction)
        }
        enqueueSyncOperation("TRANSACTION", targetId, "UPDATE", payload)
    }

    suspend fun deleteTransaction(txn: TransactionEntity): ApiResult<Unit> = withContext(Dispatchers.IO) {
        db.deleteTransactionLocal(txn.localId)

        val acc = txn.accountId?.let { db.getAccountById(it) }
        var revertedBal: Double? = null
        if (acc != null) {
            val computed = if (acc.accountType == "CREDIT_CARD") {
                if (txn.direction == "DEBIT") maxOf(0.0, MoneyMath.subtract(acc.currentBalance, txn.amount))
                else MoneyMath.add(acc.currentBalance, txn.amount)
            } else {
                if (txn.direction == "DEBIT") MoneyMath.add(acc.currentBalance, txn.amount)
                else MoneyMath.subtract(acc.currentBalance, txn.amount)
            }
            revertedBal = computed
            db.upsertAccountLocal(acc.copy(currentBalance = computed, updatedAtMs = System.currentTimeMillis()))
        }

        val token = sessionMgr.getValidAccessToken(api)
        val userId = sessionMgr.getUserId()
        if (txn.syncStatus == LocalFinanceDbHelper.STATUS_SYNCED && userId.isNotBlank()) {
            if (!token.isNullOrBlank() && isOnline()) {
                if (acc != null && revertedBal != null && !acc.id.startsWith("local-acc-")) {
                    api.updateAccountBalance(token, userId, acc.id, revertedBal, acc.balanceSource)
                }
                val remoteId = txn.remoteId ?: run {
                    val found = api.findTransactionIdByFingerprint(token, userId, txn.smsFingerprint)
                    (found as? ApiResult.Success)?.data ?: txn.localId
                }
                val delRes = api.deleteTransaction(token, userId, remoteId)
                if (delRes is ApiResult.Error && delRes.code != 404) {
                    enqueueSyncOperation("TRANSACTION", remoteId, "DELETE")
                }
            } else {
                val targetId = txn.remoteId ?: txn.localId
                enqueueSyncOperation("TRANSACTION", targetId, "DELETE")
            }
        }
        ApiResult.Success(Unit)
    }

    // ── ACCOUNTS CRUD ────────────────────────────────────────────────────────

    suspend fun addAccount(
        bankName: String,
        last4Digits: String,
        accountType: String,
        initialBalance: Double
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val cleanBank = bankName.trim()
        if (cleanBank.isEmpty()) return@withContext ApiResult.Error("Bank name is required")
        val digits = last4Digits.replace(Regex("[^0-9]"), "").takeLast(4)
        if (digits.length < 3) return@withContext ApiResult.Error("Enter at least last 3-4 digits")

        val userId = sessionMgr.getUserId()
        val mask = "XX$digits"
        val existingDup = db.getAccounts().firstOrNull {
            it.bankName.equals(cleanBank, ignoreCase = true) &&
                it.accountNumberMask.equals(mask, ignoreCase = true)
        }
        if (existingDup != null) {
            return@withContext ApiResult.Error("An account for $cleanBank ($mask) already exists")
        }

        val cleanBal = MoneyMath.round2(initialBalance)
        val draft = AccountEntity(
            id = "local-acc-${UUID.randomUUID()}",
            userId = userId,
            bankName = cleanBank,
            accountNumberMask = mask,
            accountType = accountType,
            initialBalance = cleanBal,
            openingBalance = cleanBal,
            currentBalance = cleanBal,
            balanceSource = "CALCULATED",
            accountStatus = "ACTIVE",
            pendingSync = true
        )

        val token = sessionMgr.getValidAccessToken(api)
        if (!token.isNullOrBlank() && userId.isNotBlank() && isOnline()) {
            when (val res = api.upsertAccount(token, draft)) {
                is ApiResult.Success -> db.upsertAccountLocal(res.data)
                is ApiResult.Error -> db.upsertAccountLocal(draft)
            }
        } else {
            db.upsertAccountLocal(draft)
        }
        ApiResult.Success(Unit)
    }

    suspend fun updateAccount(
        account: AccountEntity,
        bankName: String,
        accountName: String?,
        last4Digits: String,
        accountType: String,
        newBalance: Double
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val cleanBank = bankName.trim()
        if (cleanBank.isEmpty()) return@withContext ApiResult.Error("Bank name is required")
        val digits = last4Digits.replace(Regex("[^0-9]"), "").takeLast(4)
        if (digits.length < 3) return@withContext ApiResult.Error("Enter at least last 3-4 digits")
        val mask = "XX$digits"

        val dup = db.getAccounts().firstOrNull {
            it.id != account.id &&
                it.bankName.equals(cleanBank, ignoreCase = true) &&
                it.accountNumberMask.equals(mask, ignoreCase = true)
        }
        if (dup != null) {
            return@withContext ApiResult.Error("Another account for $cleanBank ($mask) already exists")
        }

        val updated = account.copy(
            bankName = cleanBank,
            accountName = accountName?.trim()?.takeIf { it.isNotBlank() },
            accountNumberMask = mask,
            accountType = accountType,
            currentBalance = MoneyMath.round2(newBalance),
            updatedAtMs = System.currentTimeMillis()
        )
        db.upsertAccountLocal(updated)

        val token = sessionMgr.getValidAccessToken(api)
        val userId = sessionMgr.getUserId()
        if (!updated.id.startsWith("local-acc-") && userId.isNotBlank()) {
            if (!token.isNullOrBlank() && isOnline()) {
                val res = api.updateAccount(token, updated)
                if (res is ApiResult.Error) {
                    enqueueSyncOperation("ACCOUNT", updated.id, "UPDATE")
                }
            } else {
                enqueueSyncOperation("ACCOUNT", updated.id, "UPDATE")
            }
        }
        ApiResult.Success(Unit)
    }

    suspend fun updateAccountBalance(account: AccountEntity, newBalance: Double): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            val cleanBal = MoneyMath.round2(newBalance)
            db.upsertAccountLocal(account.copy(currentBalance = cleanBal, updatedAtMs = System.currentTimeMillis()))
            val token = sessionMgr.getValidAccessToken(api)
            val userId = sessionMgr.getUserId()
            if (!account.id.startsWith("local-acc-") && userId.isNotBlank()) {
                if (!token.isNullOrBlank() && isOnline()) {
                    val res = api.updateAccountBalance(token, userId, account.id, cleanBal, account.balanceSource)
                    if (res is ApiResult.Error) {
                        enqueueSyncOperation("ACCOUNT", account.id, "UPDATE")
                    }
                } else {
                    enqueueSyncOperation("ACCOUNT", account.id, "UPDATE")
                }
            }
            ApiResult.Success(Unit)
        }

    suspend fun deleteAccount(account: AccountEntity): ApiResult<Unit> = withContext(Dispatchers.IO) {
        db.deleteAccountLocal(account.id)
        val token = sessionMgr.getValidAccessToken(api)
        val userId = sessionMgr.getUserId()
        if (!account.id.startsWith("local-acc-") && userId.isNotBlank()) {
            if (!token.isNullOrBlank() && isOnline()) {
                val res = api.deleteAccount(token, userId, account.id)
                if (res is ApiResult.Error && res.code != 404) {
                    enqueueSyncOperation("ACCOUNT", account.id, "DELETE")
                }
            } else {
                enqueueSyncOperation("ACCOUNT", account.id, "DELETE")
            }
        }
        ApiResult.Success(Unit)
    }

    // ── CATEGORIES, RULES, PROFILE, EXPORT, RESET ────────────────────────────

    suspend fun createCategory(name: String, color: String, isIncome: Boolean): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            val trimmed = name.trim()
            if (trimmed.length < 2) return@withContext ApiResult.Error("Category name must be at least 2 characters")
            if (db.getCategories().any { it.name.equals(trimmed, ignoreCase = true) }) {
                return@withContext ApiResult.Error("Category \"$trimmed\" already exists")
            }
            val token = sessionMgr.getValidAccessToken(api)
            if (!token.isNullOrBlank() && isOnline()) {
                when (val res = api.createCategory(token, trimmed, color, isIncome)) {
                    is ApiResult.Success -> {
                        db.upsertCategory(res.data)
                        return@withContext ApiResult.Success(Unit)
                    }
                    is ApiResult.Error -> return@withContext res
                }
            }
            db.upsertCategory(CategoryEntity("local-cat-${UUID.randomUUID()}", trimmed, "Tag", color, isIncome))
            ApiResult.Success(Unit)
        }

    suspend fun updateCategory(category: CategoryEntity, newName: String, newColor: String, newIsIncome: Boolean): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            val trimmed = newName.trim()
            if (trimmed.length < 2) return@withContext ApiResult.Error("Category name must be at least 2 characters")
            if (db.getCategories().any { it.id != category.id && it.name.equals(trimmed, ignoreCase = true) }) {
                return@withContext ApiResult.Error("Category \"$trimmed\" already exists")
            }
            db.updateCategoryLocal(category.id, trimmed, newColor, newIsIncome)
            ApiResult.Success(Unit)
        }

    suspend fun deleteCategory(category: CategoryEntity): ApiResult<Unit> = withContext(Dispatchers.IO) {
        if (category.name.equals("Uncategorized", ignoreCase = true)) {
            return@withContext ApiResult.Error("Default Uncategorized category cannot be removed")
        }
        db.deleteCategoryLocal(category.id)
        ApiResult.Success(Unit)
    }

    suspend fun createRule(keyword: String, categoryId: String, priority: Int = 10): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            val trimmedKw = keyword.trim()
            if (trimmedKw.length < 2) return@withContext ApiResult.Error("Keyword must be at least 2 characters")
            val cat = db.getCategories().firstOrNull { it.id == categoryId }
                ?: return@withContext ApiResult.Error("Please select a valid category")
            if (db.getRules().any { it.matchingRule.equals(trimmedKw, ignoreCase = true) }) {
                return@withContext ApiResult.Error("A rule for \"$trimmedKw\" already exists")
            }

            val token = sessionMgr.getValidAccessToken(api)
            val userId = sessionMgr.getUserId()
            val cloudCatId = resolveCloudCategoryId(categoryId)
            if (!token.isNullOrBlank() && userId.isNotBlank() && isOnline() && cloudCatId != null) {
                when (val res = api.createRule(token, userId, trimmedKw, cloudCatId, priority)) {
                    is ApiResult.Success -> {
                        db.upsertRule(res.data)
                        return@withContext ApiResult.Success(Unit)
                    }
                    is ApiResult.Error -> return@withContext res
                }
            }
            db.upsertRule(
                TransactionRuleEntity(
                    id = "local-rule-${UUID.randomUUID()}",
                    userId = userId,
                    matchingRule = trimmedKw,
                    categoryId = cloudCatId ?: categoryId,
                    categoryName = cat.name,
                    categoryColor = cat.color,
                    priority = priority,
                    enabled = true
                )
            )
            ApiResult.Success(Unit)
        }

    suspend fun updateRule(
        rule: TransactionRuleEntity,
        keyword: String,
        categoryId: String,
        priority: Int = rule.priority
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val trimmedKw = keyword.trim()
        if (trimmedKw.length < 2) return@withContext ApiResult.Error("Keyword must be at least 2 characters")
        val cat = db.getCategories().firstOrNull { it.id == categoryId }
            ?: return@withContext ApiResult.Error("Please select a valid category")
        if (db.getRules().any { it.id != rule.id && it.matchingRule.equals(trimmedKw, ignoreCase = true) }) {
            return@withContext ApiResult.Error("Another rule for \"$trimmedKw\" already exists")
        }

        val cloudCatId = resolveCloudCategoryId(categoryId) ?: categoryId
        val updated = rule.copy(
            matchingRule = trimmedKw,
            categoryId = cloudCatId,
            categoryName = cat.name,
            categoryColor = cat.color,
            priority = priority
        )
        db.upsertRule(updated)

        val token = sessionMgr.getValidAccessToken(api)
        val userId = sessionMgr.getUserId()
        if (!rule.id.startsWith("local-rule-") && userId.isNotBlank()) {
            if (!token.isNullOrBlank() && isOnline() && !cloudCatId.startsWith("local-cat-")) {
                val res = api.updateRule(token, userId, rule.id, trimmedKw, cloudCatId, priority, updated.enabled)
                if (res is ApiResult.Error) enqueueSyncOperation("RULE", rule.id, "UPDATE")
            } else {
                enqueueSyncOperation("RULE", rule.id, "UPDATE")
            }
        }
        ApiResult.Success(Unit)
    }

    suspend fun toggleRule(rule: TransactionRuleEntity, enabled: Boolean): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            db.upsertRule(rule.copy(enabled = enabled))
            val token = sessionMgr.getValidAccessToken(api)
            val userId = sessionMgr.getUserId()
            if (!rule.id.startsWith("local-rule-") && userId.isNotBlank()) {
                if (!token.isNullOrBlank() && isOnline()) {
                    val res = api.toggleRule(token, userId, rule.id, enabled)
                    if (res is ApiResult.Error) enqueueSyncOperation("RULE", rule.id, "UPDATE")
                } else {
                    enqueueSyncOperation("RULE", rule.id, "UPDATE")
                }
            }
            ApiResult.Success(Unit)
        }

    suspend fun deleteRule(rule: TransactionRuleEntity): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            db.deleteRuleLocal(rule.id)
            val token = sessionMgr.getValidAccessToken(api)
            val userId = sessionMgr.getUserId()
            if (!rule.id.startsWith("local-rule-") && userId.isNotBlank()) {
                if (!token.isNullOrBlank() && isOnline()) {
                    val res = api.deleteRule(token, userId, rule.id)
                    if (res is ApiResult.Error && res.code != 404) {
                        enqueueSyncOperation("RULE", rule.id, "DELETE")
                    }
                } else {
                    enqueueSyncOperation("RULE", rule.id, "DELETE")
                }
            }
            ApiResult.Success(Unit)
        }

    suspend fun updateProfileName(fullName: String): ApiResult<UserProfile> =
        withContext(Dispatchers.IO) {
            val clean = fullName.trim()
            if (clean.length < 2) return@withContext ApiResult.Error("Name must be at least 2 characters")
            val current = sessionMgr.getCachedProfile()
                ?: return@withContext ApiResult.Error("No profile loaded")
            val updated = current.copy(fullName = clean)
            val token = sessionMgr.getValidAccessToken(api)
            if (!token.isNullOrBlank() && isOnline()) {
                val res = api.upsertProfile(token, updated)
                if (res is ApiResult.Success) {
                    sessionMgr.saveProfile(res.data)
                }
                return@withContext res
            }
            sessionMgr.saveProfile(updated)
            ApiResult.Success(updated)
        }

    fun isBudgetAlertsEnabled(): Boolean = sessionMgr.isBudgetAlertsEnabled()

    fun setBudgetAlertsEnabled(enabled: Boolean) {
        sessionMgr.setBudgetAlertsEnabled(enabled)
    }

    suspend fun updateBiometricPreference(enabled: Boolean): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            sessionMgr.setBiometricEnabled(enabled)
            val current = sessionMgr.getCachedProfile()
            if (current != null) {
                val updated = current.copy(biometricEnabled = enabled)
                sessionMgr.saveProfile(updated)
                val token = sessionMgr.getValidAccessToken(api)
                if (!token.isNullOrBlank() && isOnline()) {
                    api.upsertProfile(token, updated)
                }
            }
            ApiResult.Success(Unit)
        }

    suspend fun regenerateDeviceSyncToken(): ApiResult<UserProfile> =
        withContext(Dispatchers.IO) {
            val current = sessionMgr.getCachedProfile()
                ?: return@withContext ApiResult.Error("No profile loaded")
            val bytes = ByteArray(24)
            java.security.SecureRandom().nextBytes(bytes)
            val newToken = bytes.joinToString("") { "%02x".format(it) }
            val updated = current.copy(deviceSyncToken = newToken)
            val token = sessionMgr.getValidAccessToken(api)
            if (token.isNullOrBlank() || !isOnline()) {
                return@withContext ApiResult.Error("Must be online to regenerate sync token")
            }
            api.upsertProfile(token, updated).also {
                if (it is ApiResult.Success) sessionMgr.saveProfile(it.data)
            }
        }

    suspend fun fetchDevices(): List<DeviceEntity> = withContext(Dispatchers.IO) {
        val token = sessionMgr.getValidAccessToken(api) ?: return@withContext emptyList()
        val userId = sessionMgr.getUserId()
        if (userId.isBlank() || !isOnline()) return@withContext emptyList()
        when (val res = api.fetchDevices(token, userId)) {
            is ApiResult.Success -> res.data
            is ApiResult.Error -> emptyList()
        }
    }

    suspend fun resetAllData(): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            WorkManager.getInstance(appContext).cancelUniqueWork("monzi_pending_flush")
        } catch (_: Exception) {
            // WorkManager may not be initialized in unit tests
        }

        val userId = sessionMgr.getUserId()
        if (isOnline() && userId.isNotBlank()) {
            val token = sessionMgr.getValidAccessToken(api)
                ?: return@withContext ApiResult.Error("Session expired. Please sign in again before resetting cloud data.", 401)
            val res = api.resetUserData(token, userId)
            if (res is ApiResult.Error) return@withContext res
            sessionMgr.setPendingCloudReset(false)
        } else if (userId.isNotBlank()) {
            sessionMgr.setPendingCloudReset(true)
        }

        db.clearAllUserFinanceData()
        ApiResult.Success(Unit)
    }

    // ── MONZI 2.1: RECONCILIATION, CREDIT CARDS, BUDGETS, DUPLICATES, TRANSFERS ──

    suspend fun addBalanceAdjustment(
        account: AccountEntity,
        adjustmentAmount: Double,
        reason: String
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val userId = sessionMgr.getUserId()
        val adj = AccountBalanceAdjustmentEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            accountId = account.id,
            adjustmentAmount = MoneyMath.round2(adjustmentAmount),
            reason = reason.trim().ifBlank { "Manual balance reconciliation adjustment" },
            approved = true
        )
        db.insertAdjustment(adj)

        val summary = ReconciliationEngine.reconcileAccountLedger(
            account = account,
            transactions = db.getAllTransactions(5000),
            adjustments = db.getAllAdjustments(),
            snapshots = db.getSnapshotsForAccount(account.id)
        )
        val updatedAcc = account.copy(
            currentBalance = summary.calculatedBalance,
            reconciliationStatus = summary.reconciliationStatus,
            updatedAtMs = System.currentTimeMillis()
        )
        db.upsertAccountLocal(updatedAcc)

        val token = sessionMgr.getValidAccessToken(api)
        if (!token.isNullOrBlank() && userId.isNotBlank() && isOnline() && !account.id.startsWith("local-acc-")) {
            api.upsertBalanceAdjustment(token, adj)
            api.updateAccountBalance(token, userId, account.id, updatedAcc.currentBalance, updatedAcc.balanceSource)
        }
        ApiResult.Success(Unit)
    }

    suspend fun addOrUpdateCreditCard(
        existingCard: AccountEntity?,
        bankName: String,
        accountName: String?,
        last4Digits: String,
        creditLimit: Double?,
        openingOutstanding: Double,
        statementDay: Int?,
        dueDay: Int?
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val cleanBank = bankName.trim()
        if (cleanBank.isEmpty()) return@withContext ApiResult.Error("Issuing bank name is required")
        val digits = last4Digits.replace(Regex("[^0-9]"), "").takeLast(4)
        if (digits.length < 3) return@withContext ApiResult.Error("Enter at least last 3-4 card digits")
        val mask = "XX$digits"

        if (existingCard == null) {
            val dup = db.getAccounts().firstOrNull {
                it.bankName.equals(cleanBank, ignoreCase = true) &&
                    it.accountNumberMask.equals(mask, ignoreCase = true)
            }
            if (dup != null) {
                return@withContext ApiResult.Error("Card $cleanBank ($mask) already exists")
            }
        }

        val userId = sessionMgr.getUserId()
        val roundedOpening = MoneyMath.round2(openingOutstanding)
        val nextCurrentBalance = if (existingCard != null) {
            val openingDelta = MoneyMath.subtract(roundedOpening, existingCard.openingBalance)
            maxOf(0.0, MoneyMath.add(existingCard.currentBalance, openingDelta))
        } else {
            roundedOpening
        }

        val draft = (existingCard ?: AccountEntity(
            id = "local-acc-${UUID.randomUUID()}",
            userId = userId,
            bankName = cleanBank,
            accountNumberMask = mask,
            accountType = "CREDIT_CARD",
            openingBalance = roundedOpening,
            currentBalance = roundedOpening,
            balanceSource = "CALCULATED",
            pendingSync = true
        )).copy(
            bankName = cleanBank,
            accountName = accountName?.trim()?.takeIf { it.isNotBlank() },
            accountNumberMask = mask,
            accountType = "CREDIT_CARD",
            creditLimit = creditLimit?.let { MoneyMath.round2(it) },
            openingBalance = roundedOpening,
            currentBalance = nextCurrentBalance,
            statementDay = statementDay?.coerceIn(1, 31),
            dueDay = dueDay?.coerceIn(1, 31),
            updatedAtMs = System.currentTimeMillis()
        )

        val token = sessionMgr.getValidAccessToken(api)
        if (!token.isNullOrBlank() && userId.isNotBlank() && isOnline()) {
            val res = api.upsertAccount(token, draft)
            if (res is ApiResult.Success) {
                db.remapLocalAccountId(draft.id, res.data)
            } else {
                db.upsertAccountLocal(draft)
            }
        } else {
            db.upsertAccountLocal(draft)
        }
        ApiResult.Success(Unit)
    }

    suspend fun upsertCreditCardStatement(
        card: AccountEntity,
        totalDue: Double,
        minimumDue: Double,
        dueDateMs: Long?,
        statementPeriod: String = IstTime.currentMonthKey()
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        if (totalDue < 0.0 || minimumDue < 0.0) {
            return@withContext ApiResult.Error("Statement amounts cannot be negative")
        }
        if (minimumDue > totalDue) {
            return@withContext ApiResult.Error("Minimum due cannot exceed total due")
        }
        val userId = sessionMgr.getUserId()
        val existing = db.getAllStatements().firstOrNull {
            it.accountId == card.id && it.statementPeriod == statementPeriod
        }
        val base = CreditCardStatementEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            userId = userId,
            accountId = card.id,
            statementPeriod = statementPeriod,
            statementDateMs = existing?.statementDateMs ?: System.currentTimeMillis(),
            totalDue = MoneyMath.round2(totalDue),
            minimumDue = MoneyMath.round2(minimumDue),
            dueDateMs = dueDateMs,
            paidAmount = existing?.paidAmount ?: 0.0,
            paymentStatus = "UPCOMING",
            updatedAtMs = System.currentTimeMillis()
        )
        val status = BudgetAndCardEngine.computeStatementStatus(base)
        val saved = base.copy(paymentStatus = status)
        db.upsertStatement(saved)
        val token = sessionMgr.getValidAccessToken(api)
        if (!token.isNullOrBlank() && userId.isNotBlank() && isOnline() && !card.id.startsWith("local-acc-")) {
            api.upsertCreditCardStatement(token, saved)
        }
        ApiResult.Success(Unit)
    }

    suspend fun recordCreditCardPayment(
        card: AccountEntity,
        sourceAccount: AccountEntity?,
        amount: Double,
        statement: CreditCardStatementEntity?,
        paymentDateMs: Long = System.currentTimeMillis()
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val userId = sessionMgr.getUserId()
        val payAmt = MoneyMath.round2(amount)
        if (payAmt <= 0.0) return@withContext ApiResult.Error("Amount must be greater than zero")

        val paymentTxId = UUID.randomUUID().toString()
        val dateIso = BankSmsParser.toUtcIso(paymentDateMs)
        val fpInput = "CCPAY_${card.id}_${paymentDateMs}_${payAmt}_${UUID.randomUUID()}"
        val fp = MessageDigest.getInstance("SHA-256")
            .digest(fpInput.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val ccPayTxn = TransactionEntity(
            localId = paymentTxId,
            userId = userId,
            accountId = sourceAccount?.id ?: card.id,
            categoryId = null,
            smsFingerprint = fp,
            amount = payAmt,
            direction = if (sourceAccount != null) "DEBIT" else "CREDIT",
            transactionType = "CC_BILL_PAYMENT",
            bankName = sourceAccount?.bankName ?: card.bankName,
            accountMask = sourceAccount?.accountNumberMask ?: card.accountNumberMask,
            merchant = "${card.bankName} Card Bill Payment",
            referenceId = "CCPAY_${paymentDateMs}",
            paymentMode = "NETBANKING",
            isSelfTransfer = true,
            isExcludedFromTotals = true,
            transactionSource = LocalFinanceDbHelper.SOURCE_MANUAL,
            transactionStatus = "COMPLETED",
            transactionDateIso = dateIso,
            transactionDateMs = paymentDateMs,
            syncStatus = LocalFinanceDbHelper.STATUS_PENDING,
            categoryName = "Bills & Utilities",
            categoryColor = "#8B5CF6",
            accountDisplayName = "${card.bankName} (${card.accountNumberMask})"
        )
        db.insertLocalTransaction(ccPayTxn)

        // When paid from a bank account, also record the matching CREDIT leg on the credit card ledger
        // so ReconciliationEngine keeps card debt accurate across full recalculations.
        if (sourceAccount != null) {
            val cardLegFpInput = "CCPAY_CARD_${card.id}_${paymentDateMs}_${payAmt}_${UUID.randomUUID()}"
            val cardLegFp = MessageDigest.getInstance("SHA-256")
                .digest(cardLegFpInput.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            val cardCreditTxn = TransactionEntity(
                localId = UUID.randomUUID().toString(),
                userId = userId,
                accountId = card.id,
                categoryId = null,
                smsFingerprint = cardLegFp,
                amount = payAmt,
                direction = "CREDIT",
                transactionType = "CC_BILL_PAYMENT",
                bankName = card.bankName,
                accountMask = card.accountNumberMask,
                merchant = "${card.bankName} Card Payment Received",
                referenceId = "CCPAY_${paymentDateMs}_CR",
                paymentMode = "NETBANKING",
                isSelfTransfer = true,
                isExcludedFromTotals = true,
                linkedTransactionId = paymentTxId,
                transactionSource = LocalFinanceDbHelper.SOURCE_MANUAL,
                transactionStatus = "COMPLETED",
                transactionDateIso = dateIso,
                transactionDateMs = paymentDateMs,
                syncStatus = LocalFinanceDbHelper.STATUS_PENDING,
                categoryName = "Bills & Utilities",
                categoryColor = "#8B5CF6",
                accountDisplayName = "${card.bankName} (${card.accountNumberMask})"
            )
            db.insertLocalTransaction(cardCreditTxn)
        }

        val nextCardOutstanding = maxOf(0.0, MoneyMath.subtract(card.currentBalance, payAmt))
        db.upsertAccountLocal(card.copy(currentBalance = nextCardOutstanding, updatedAtMs = System.currentTimeMillis()))

        var nextBankBal: Double? = null
        if (sourceAccount != null) {
            val b = MoneyMath.subtract(sourceAccount.currentBalance, payAmt)
            nextBankBal = b
            db.upsertAccountLocal(sourceAccount.copy(currentBalance = b, updatedAtMs = System.currentTimeMillis()))
        }

        var updatedStmt: CreditCardStatementEntity? = null
        if (statement != null) {
            val nextPaid = MoneyMath.add(statement.paidAmount, payAmt)
            val tmp = statement.copy(paidAmount = nextPaid, updatedAtMs = System.currentTimeMillis())
            updatedStmt = tmp.copy(paymentStatus = BudgetAndCardEngine.computeStatementStatus(tmp))
            db.upsertStatement(updatedStmt)
        }

        val payment = CreditCardPaymentEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            cardAccountId = card.id,
            sourceAccountId = sourceAccount?.id,
            statementId = statement?.id,
            transactionId = paymentTxId,
            amount = payAmt,
            paymentDateMs = paymentDateMs
        )
        db.insertCcPayment(payment)

        val token = sessionMgr.getValidAccessToken(api)
        if (!token.isNullOrBlank() && userId.isNotBlank() && isOnline()) {
            if (!card.id.startsWith("local-acc-")) {
                api.updateAccountBalance(token, userId, card.id, nextCardOutstanding, card.balanceSource)
                api.insertCreditCardPayment(token, payment)
                updatedStmt?.let { api.upsertCreditCardStatement(token, it) }
            }
            if (sourceAccount != null && nextBankBal != null && !sourceAccount.id.startsWith("local-acc-")) {
                api.updateAccountBalance(token, userId, sourceAccount.id, nextBankBal, sourceAccount.balanceSource)
            }
            try {
                SyncWorker.enqueuePendingFlush(appContext)
            } catch (_: Exception) {
            }
        }
        ApiResult.Success(Unit)
    }

    suspend fun saveBudget(
        existingId: String?,
        name: String,
        categoryId: String?,
        monthlyLimit: Double,
        alertThreshold: Int,
        carryForward: Boolean = false
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val cleanLimit = MoneyMath.round2(monthlyLimit)
        if (cleanLimit <= 0.0 || cleanLimit > 100_000_000.0) {
            return@withContext ApiResult.Error("Monthly budget limit must be greater than zero")
        }
        val userId = sessionMgr.getUserId()
        val cat = db.getCategories().firstOrNull { it.id == categoryId }
        val resolvedCatId = resolveCloudCategoryId(categoryId) ?: categoryId
        val monthKey = IstTime.currentMonthKey()

        if (existingId == null) {
            val dup = db.getAllBudgets().firstOrNull {
                it.status == "ACTIVE" &&
                    it.startMonth == monthKey &&
                    (it.categoryId == resolvedCatId || it.categoryId == categoryId || (it.categoryId == null && categoryId == null))
            }
            if (dup != null) {
                return@withContext ApiResult.Error("An active budget for ${cat?.name ?: "Overall Monthly"} already exists")
            }
        }

        val budget = BudgetEntity(
            id = existingId ?: UUID.randomUUID().toString(),
            userId = userId,
            name = name.trim().ifBlank { cat?.name ?: "Monthly Overall Budget" },
            categoryId = resolvedCatId,
            categoryName = cat?.name,
            categoryColor = cat?.color,
            monthlyLimit = cleanLimit,
            startMonth = monthKey,
            alertThreshold = alertThreshold.coerceIn(10, 100),
            carryForward = carryForward,
            status = "ACTIVE"
        )
        db.upsertBudget(budget)

        val eval = BudgetAndCardEngine.evaluateBudgets(
            budgets = db.getAllBudgets(),
            transactions = db.getAllTransactions(2000),
            refunds = db.getAllRefunds(),
            existingAlerts = db.getAllBudgetAlerts(),
            monthKey = monthKey,
            userId = userId
        )
        for (a in eval.triggeredAlerts) {
            db.insertBudgetAlertIfNew(a)
        }

        val token = sessionMgr.getValidAccessToken(api)
        if (userId.isNotBlank()) {
            if (!token.isNullOrBlank() && isOnline()) {
                val res = api.upsertBudget(token, budget)
                if (res is ApiResult.Error) enqueueSyncOperation("BUDGET", budget.id, "UPSERT")
            } else {
                enqueueSyncOperation("BUDGET", budget.id, "UPSERT")
            }
        }
        ApiResult.Success(Unit)
    }

    suspend fun deleteBudget(budgetId: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        db.deleteBudget(budgetId)
        val token = sessionMgr.getValidAccessToken(api)
        val userId = sessionMgr.getUserId()
        if (userId.isNotBlank()) {
            if (!token.isNullOrBlank() && isOnline()) {
                val res = api.deleteBudget(token, userId, budgetId)
                if (res is ApiResult.Error && res.code != 404) {
                    enqueueSyncOperation("BUDGET", budgetId, "DELETE")
                }
            } else {
                enqueueSyncOperation("BUDGET", budgetId, "DELETE")
            }
        }
        ApiResult.Success(Unit)
    }

    suspend fun acknowledgeBudgetAlert(alertId: String) = withContext(Dispatchers.IO) {
        db.acknowledgeBudgetAlert(alertId)
    }

    suspend fun confirmSeparateTransaction(txn: TransactionEntity) = withContext(Dispatchers.IO) {
        db.updateTransactionAutomationFlags(
            localId = txn.localId,
            duplicateStatus = "CONFIRMED_SEPARATE",
            isExcludedFromTotals = false
        )
    }

    suspend fun confirmTransferStatus(txn: TransactionEntity, isTransfer: Boolean) = withContext(Dispatchers.IO) {
        val nextType = if (isTransfer) "TRANSFER" else if (txn.direction == "CREDIT") "INCOME" else "EXPENSE"
        if (isTransfer) {
            db.updateTransactionAutomationFlags(
                localId = txn.localId,
                transferStatus = "CONFIRMED_TRANSFER",
                isSelfTransfer = true,
                isExcludedFromTotals = true,
                transactionType = nextType
            )
            db.getAllTransfers()
                .filter { it.debitTransactionId == txn.localId || it.creditTransactionId == txn.localId }
                .forEach { db.updateTransferStatus(it.id, "CONFIRMED_TRANSFER") }
        } else {
            db.updateTransactionAutomationFlags(
                localId = txn.localId,
                transferStatus = "NOT_A_TRANSFER",
                isSelfTransfer = false,
                isExcludedFromTotals = false,
                transactionType = nextType
            )
            db.getAllTransfers()
                .filter { it.debitTransactionId == txn.localId || it.creditTransactionId == txn.localId }
                .forEach { db.updateTransferStatus(it.id, "NOT_A_TRANSFER") }
        }

        val token = sessionMgr.getValidAccessToken(api)
        val userId = sessionMgr.getUserId()
        if (txn.syncStatus == LocalFinanceDbHelper.STATUS_SYNCED && userId.isNotBlank()) {
            val cloudCatId = resolveCloudCategoryId(txn.categoryId)
            if (!token.isNullOrBlank() && isOnline()) {
                val targetId = txn.remoteId ?: run {
                    val found = api.findTransactionIdByFingerprint(token, userId, txn.smsFingerprint)
                    (found as? ApiResult.Success)?.data ?: txn.localId
                }
                val res = api.updateTransaction(
                    accessToken = token,
                    userId = userId,
                    remoteId = targetId,
                    categoryId = cloudCatId,
                    notes = txn.notes,
                    merchant = txn.merchant,
                    transactionType = nextType
                )
                if (res is ApiResult.Error) {
                    enqueueUpdateTx(targetId, cloudCatId, txn.notes, txn.merchant, nextType, txn.amount, txn.direction)
                }
            } else {
                enqueueUpdateTx(txn.remoteId ?: txn.localId, cloudCatId, txn.notes, txn.merchant, nextType, txn.amount, txn.direction)
            }
        }
    }

    suspend fun retryFailedSyncItems() = withContext(Dispatchers.IO) {
        val pendingOrFailed = db.getAllTransactions(2000).filter {
            it.syncStatus == LocalFinanceDbHelper.STATUS_FAILED ||
                it.syncStatus == LocalFinanceDbHelper.STATUS_RETRY_SCHEDULED
        }
        for (tx in pendingOrFailed) {
            db.insertLocalTransaction(tx.copy(syncStatus = LocalFinanceDbHelper.STATUS_PENDING, retryCount = 0))
        }
        flushPendingNow()
    }

    suspend fun exportTransactionsCsv(
        startMs: Long? = null,
        endMs: Long? = null,
        accountId: String? = null
    ): File = withContext(Dispatchers.IO) {
        val txns = db.getAllTransactions(5000).filter { tx ->
            val inStart = startMs == null || tx.transactionDateMs >= startMs
            val inEnd = endMs == null || tx.transactionDateMs <= endMs
            val inAcc = accountId.isNullOrBlank() || tx.accountId == accountId
            inStart && inEnd && inAcc
        }
        val exportsDir = File(appContext.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, "monzi_transactions_${System.currentTimeMillis()}.csv")
        val sb = StringBuilder()
        sb.appendLine("Date,Merchant,Type,Direction,Amount (INR),Category,Payment Mode,Bank,Account Mask,Reference ID,Source,Duplicate Status,Notes")
        for (tx in txns) {
            val esc = { v: String? -> "\"${(v ?: "").replace("\"", "\"\"")}\"" }
            sb.appendLine(
                listOf(
                    esc(tx.transactionDateIso),
                    esc(tx.merchant),
                    tx.transactionType,
                    tx.direction,
                    MoneyMath.round2(tx.amount).toString(),
                    esc(tx.categoryName ?: "Uncategorized"),
                    esc(tx.paymentMode),
                    esc(tx.bankName),
                    esc(tx.accountMask),
                    esc(tx.referenceId),
                    esc(tx.transactionSource),
                    esc(tx.duplicateStatus),
                    esc(tx.notes)
                ).joinToString(",")
            )
        }
        file.writeText(sb.toString(), StandardCharsets.UTF_8)
        file
    }
}
