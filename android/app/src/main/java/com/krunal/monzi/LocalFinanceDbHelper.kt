package com.krunal.monzi

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.krunal.monzi.data.AccountBalanceAdjustmentEntity
import com.krunal.monzi.data.AccountBalanceSnapshotEntity
import com.krunal.monzi.data.AccountEntity
import com.krunal.monzi.data.AutomationLogEntity
import com.krunal.monzi.data.BudgetAlertEntity
import com.krunal.monzi.data.BudgetEntity
import com.krunal.monzi.data.CategoryEntity
import com.krunal.monzi.data.CreditCardPaymentEntity
import com.krunal.monzi.data.CreditCardStatementEntity
import com.krunal.monzi.data.RefundRecordEntity
import com.krunal.monzi.data.SmsImportRunEntity
import com.krunal.monzi.data.SmsLogEntity
import com.krunal.monzi.data.SmsParserFailureEntity
import com.krunal.monzi.data.SmsSyncStateRecord
import com.krunal.monzi.data.SyncQueueItemEntity
import com.krunal.monzi.data.TransactionEntity
import com.krunal.monzi.data.TransactionRuleEntity
import com.krunal.monzi.data.TransferRecordEntity
import com.krunal.monzi.data.local.MonziDatabase
import com.krunal.monzi.data.local.RoomBalanceAdjustment
import com.krunal.monzi.data.local.RoomBalanceSnapshot
import com.krunal.monzi.data.local.RoomBudget
import com.krunal.monzi.data.local.RoomBudgetAlert
import com.krunal.monzi.data.local.RoomCreditCardPayment
import com.krunal.monzi.data.local.RoomCreditCardStatement
import com.krunal.monzi.data.local.RoomRefund
import com.krunal.monzi.data.local.RoomSmsImportRun
import com.krunal.monzi.data.local.RoomSmsParserFailure
import com.krunal.monzi.data.local.RoomSyncQueueItem
import com.krunal.monzi.data.local.RoomTransfer
import com.krunal.monzi.util.MoneyMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

class LocalFinanceDbHelper private constructor(context: Context) {

    private val roomDb = MonziDatabase.getInstance(context.applicationContext)
    private val dao = roomDb.dao()

    private val writableDatabase: SupportSQLiteDatabase
        get() = roomDb.openHelper.writableDatabase

    private val readableDatabase: SupportSQLiteDatabase
        get() = roomDb.openHelper.readableDatabase

    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes.asStateFlow()

    init {
        ensureBaselineSeeded(writableDatabase)
    }

    private fun notifyChanged() {
        _changes.value = System.nanoTime()
    }

    private fun ensureBaselineSeeded(db: SupportSQLiteDatabase) {
        seedDefaultCategories(db)
        ensureSyncStateRow(db)
    }

    private fun seedDefaultCategories(db: SupportSQLiteDatabase) {
        val defaults = listOf(
            CategoryEntity("local-cat-salary", "Salary", "Briefcase", "#16A34A", true),
            CategoryEntity("local-cat-business", "Business Income", "Building2", "#10B981", true),
            CategoryEntity("local-cat-interest", "Interest", "Percent", "#84CC16", true),
            CategoryEntity("local-cat-cashback", "Cashback", "Gift", "#06B6D4", true),
            CategoryEntity("local-cat-refund", "Refund", "RotateCcw", "#6366F1", true),
            CategoryEntity("local-cat-other-in", "Other Income", "Wallet", "#059669", true),
            CategoryEntity("local-cat-food", "Food & Dining", "Utensils", "#F59E0B", false),
            CategoryEntity("local-cat-shopping", "Shopping", "ShoppingCart", "#EC4899", false),
            CategoryEntity("local-cat-travel", "Travel & Transport", "Car", "#3B82F6", false),
            CategoryEntity("local-cat-bills", "Bills & Utilities", "Zap", "#8B5CF6", false),
            CategoryEntity("local-cat-entertainment", "Entertainment", "Film", "#6366F1", false),
            CategoryEntity("local-cat-healthcare", "Healthcare", "HeartPulse", "#EF4444", false),
            CategoryEntity("local-cat-education", "Education", "GraduationCap", "#0EA5E9", false),
            CategoryEntity("local-cat-emi", "EMI & Loan Payments", "Landmark", "#F97316", false),
            CategoryEntity("local-cat-subs", "Subscriptions", "Repeat", "#A855F7", false),
            CategoryEntity("local-cat-lifestyle", "Lifestyle", "Sparkles", "#EC4899", false),
            CategoryEntity("local-cat-invest", "Investments", "TrendingUp", "#06B6D4", false),
            CategoryEntity("local-cat-groceries", "Groceries", "ShoppingBag", "#10B981", false),
            CategoryEntity("local-cat-charges", "Bank Charges", "AlertCircle", "#EF4444", false),
            CategoryEntity("local-cat-other-exp", "Other Expenses", "CreditCard", "#94A3B8", false),
            CategoryEntity("local-cat-uncategorized", "Uncategorized", "HelpCircle", "#64748B", false)
        )
        for (cat in defaults) {
            val cv = ContentValues().apply {
                put("id", cat.id)
                put("name", cat.name)
                put("icon", cat.icon)
                put("color", cat.color)
                put("is_income", if (cat.isIncome) 1 else 0)
            }
            db.insert(TABLE_LOCAL_CATEGORIES, SQLiteDatabase.CONFLICT_IGNORE, cv)
        }
    }

    // ── CATEGORIES ───────────────────────────────────────────────────────────

    @Synchronized
    fun replaceCategoriesFromCloud(categories: List<CategoryEntity>) {
        if (categories.isEmpty()) return
        val db = writableDatabase
        val cloudNames = categories.map { it.name.trim().lowercase(Locale.US) }.toSet()
        val existingLocalCustom = getCategories().filter {
            it.id.startsWith("local-cat-") && !cloudNames.contains(it.name.trim().lowercase(Locale.US))
        }
        db.beginTransaction()
        try {
            db.delete(TABLE_LOCAL_CATEGORIES, null, null)
            for (cat in categories) {
                val cv = ContentValues().apply {
                    put("id", cat.id)
                    put("name", cat.name)
                    put("icon", cat.icon)
                    put("color", cat.color)
                    put("is_income", if (cat.isIncome) 1 else 0)
                }
                db.insert(TABLE_LOCAL_CATEGORIES, SQLiteDatabase.CONFLICT_REPLACE, cv)
            }
            seedDefaultCategories(db)
            for (localCat in existingLocalCustom) {
                val cv = ContentValues().apply {
                    put("id", localCat.id)
                    put("name", localCat.name)
                    put("icon", localCat.icon)
                    put("color", localCat.color)
                    put("is_income", if (localCat.isIncome) 1 else 0)
                }
                db.insert(TABLE_LOCAL_CATEGORIES, SQLiteDatabase.CONFLICT_IGNORE, cv)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
    }

    @Synchronized
    fun updateCategoryLocal(id: String, name: String, color: String, isIncome: Boolean) {
        val cv = ContentValues().apply {
            put("name", name.trim())
            put("color", color)
            put("is_income", if (isIncome) 1 else 0)
        }
        writableDatabase.update(TABLE_LOCAL_CATEGORIES, SQLiteDatabase.CONFLICT_REPLACE, cv, "id = ?", arrayOf(id))
        notifyChanged()
    }

    @Synchronized
    fun deleteCategoryLocal(id: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_LOCAL_CATEGORIES, "id = ?", arrayOf(id))
            db.delete(TABLE_LOCAL_RULES, "category_id = ?", arrayOf(id))
            val txCv = ContentValues().apply {
                putNull("category_id")
                put("category_name", "Uncategorized")
                put("category_color", "#64748B")
            }
            db.update(TABLE_LOCAL_TRANSACTIONS, SQLiteDatabase.CONFLICT_REPLACE, txCv, "category_id = ?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
    }

    @Synchronized
    fun upsertCategory(cat: CategoryEntity) {
        val cv = ContentValues().apply {
            put("id", cat.id)
            put("name", cat.name)
            put("icon", cat.icon)
            put("color", cat.color)
            put("is_income", if (cat.isIncome) 1 else 0)
        }
        writableDatabase.insert(TABLE_LOCAL_CATEGORIES, SQLiteDatabase.CONFLICT_REPLACE, cv)
        notifyChanged()
    }

    @Synchronized
    fun getCategories(): List<CategoryEntity> {
        val list = ArrayList<CategoryEntity>()
        readableDatabase.query("SELECT * FROM $TABLE_LOCAL_CATEGORIES ORDER BY name ASC").use { c ->
            while (c.moveToNext()) {
                list.add(
                    CategoryEntity(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        icon = c.getString(c.getColumnIndexOrThrow("icon")) ?: "Tag",
                        color = c.getString(c.getColumnIndexOrThrow("color")) ?: "#2563EB",
                        isIncome = c.getInt(c.getColumnIndexOrThrow("is_income")) == 1
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun resolveCategoryForSms(parsed: ParsedSms, rawBody: String = ""): CategoryEntity? {
        val rules = getRules().filter { it.enabled }.sortedBy { it.priority }
        val haystack = "${parsed.merchant.orEmpty()} ${parsed.bankName} $rawBody".lowercase(Locale.US)
        val categories = getCategories()
        val catById = categories.associateBy { it.id }

        for (rule in rules) {
            val kw = rule.matchingRule.trim().lowercase(Locale.US)
            if (kw.isNotEmpty() && haystack.contains(kw)) {
                catById[rule.categoryId]?.let { return it }
            }
        }

        val targetName = parsed.transactionCategory.lowercase(Locale.US)
        return categories.firstOrNull { it.name.lowercase(Locale.US) == targetName }
            ?: categories.firstOrNull {
                it.name.lowercase(Locale.US).contains(targetName) ||
                    targetName.contains(it.name.lowercase(Locale.US))
            }
    }

    // ── RULES ────────────────────────────────────────────────────────────────

    @Synchronized
    fun replaceRulesFromCloud(rules: List<TransactionRuleEntity>) {
        val pendingLocalRules = getRules().filter { it.id.startsWith("local-rule-") }
        val pendingDeletedRuleIds = dao.getAllSyncQueueItems()
            .filter { it.entityType == "RULE" && it.operation == "DELETE" }
            .map { it.entityId }
            .toSet()

        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_LOCAL_RULES, null, null)
            for (r in rules) {
                if (!pendingDeletedRuleIds.contains(r.id)) {
                    insertRuleInternal(db, r)
                }
            }
            for (localRule in pendingLocalRules) {
                val dupInCloud = rules.any {
                    it.matchingRule.equals(localRule.matchingRule, ignoreCase = true) &&
                        it.categoryId == localRule.categoryId
                }
                if (!dupInCloud && !pendingDeletedRuleIds.contains(localRule.id)) {
                    insertRuleInternal(db, localRule)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
    }

    @Synchronized
    fun upsertRule(rule: TransactionRuleEntity) {
        insertRuleInternal(writableDatabase, rule)
        notifyChanged()
    }

    @Synchronized
    fun deleteRuleLocal(id: String) {
        writableDatabase.delete(TABLE_LOCAL_RULES, "id = ?", arrayOf(id))
        notifyChanged()
    }

    private fun insertRuleInternal(db: SupportSQLiteDatabase, r: TransactionRuleEntity) {
        val cv = ContentValues().apply {
            put("id", r.id)
            put("user_id", r.userId)
            put("matching_rule", r.matchingRule)
            put("category_id", r.categoryId)
            put("category_name", r.categoryName)
            put("category_color", r.categoryColor)
            put("priority", r.priority)
            put("enabled", if (r.enabled) 1 else 0)
            put("created_at_iso", r.createdAtIso)
        }
        db.insert(TABLE_LOCAL_RULES, SQLiteDatabase.CONFLICT_REPLACE, cv)
    }

    @Synchronized
    fun getRules(): List<TransactionRuleEntity> {
        val list = ArrayList<TransactionRuleEntity>()
        readableDatabase.query("SELECT * FROM $TABLE_LOCAL_RULES ORDER BY priority ASC").use { c ->
            while (c.moveToNext()) {
                list.add(
                    TransactionRuleEntity(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        userId = c.getString(c.getColumnIndexOrThrow("user_id")) ?: "",
                        matchingRule = c.getString(c.getColumnIndexOrThrow("matching_rule")),
                        categoryId = c.getString(c.getColumnIndexOrThrow("category_id")),
                        categoryName = c.getString(c.getColumnIndexOrThrow("category_name")),
                        categoryColor = c.getString(c.getColumnIndexOrThrow("category_color")),
                        priority = c.getInt(c.getColumnIndexOrThrow("priority")),
                        enabled = c.getInt(c.getColumnIndexOrThrow("enabled")) == 1,
                        createdAtIso = c.getString(c.getColumnIndexOrThrow("created_at_iso")) ?: ""
                    )
                )
            }
        }
        return list
    }

    // ── ACCOUNTS ─────────────────────────────────────────────────────────────

    @Synchronized
    fun replaceAccountsFromCloud(accounts: List<AccountEntity>) {
        val pendingDeletedIds = dao.getAllSyncQueueItems()
            .filter { it.entityType == "ACCOUNT" && it.operation == "DELETE" }
            .map { it.entityId }
            .toSet()
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_LOCAL_ACCOUNTS, "pending_sync = 0", null)
            for (acc in accounts) {
                if (!pendingDeletedIds.contains(acc.id)) {
                    insertAccountInternal(db, acc)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
    }

    @Synchronized
    fun getPendingSyncAccounts(): List<AccountEntity> {
        val list = ArrayList<AccountEntity>()
        readableDatabase.query("SELECT * FROM $TABLE_LOCAL_ACCOUNTS WHERE pending_sync = 1 AND deleted = 0").use { c ->
            while (c.moveToNext()) {
                list.add(readAccountRow(c))
            }
        }
        return list
    }

    @Synchronized
    fun remapLocalAccountId(oldId: String, newAccount: AccountEntity) {
        if (oldId == newAccount.id) {
            upsertAccountLocal(newAccount)
            return
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_LOCAL_ACCOUNTS, "id = ?", arrayOf(oldId))
            insertAccountInternal(db, newAccount.copy(pendingSync = false))
            db.execSQL("UPDATE $TABLE_LOCAL_TRANSACTIONS SET account_id = ? WHERE account_id = ?", arrayOf<Any>(newAccount.id, oldId))
            db.execSQL("UPDATE account_balance_snapshots SET account_id = ? WHERE account_id = ?", arrayOf<Any>(newAccount.id, oldId))
            db.execSQL("UPDATE account_balance_adjustments SET account_id = ? WHERE account_id = ?", arrayOf<Any>(newAccount.id, oldId))
            db.execSQL("UPDATE credit_card_statements SET account_id = ? WHERE account_id = ?", arrayOf<Any>(newAccount.id, oldId))
            db.execSQL("UPDATE credit_card_payments SET card_account_id = ? WHERE card_account_id = ?", arrayOf<Any>(newAccount.id, oldId))
            db.execSQL("UPDATE credit_card_payments SET source_account_id = ? WHERE source_account_id = ?", arrayOf<Any>(newAccount.id, oldId))
            db.execSQL("UPDATE transfers SET source_account_id = ? WHERE source_account_id = ?", arrayOf<Any>(newAccount.id, oldId))
            db.execSQL("UPDATE transfers SET destination_account_id = ? WHERE destination_account_id = ?", arrayOf<Any>(newAccount.id, oldId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
    }

    @Synchronized
    fun upsertAccountLocal(acc: AccountEntity) {
        insertAccountInternal(writableDatabase, acc)
        notifyChanged()
    }

    @Synchronized
    fun deleteAccountLocal(accountId: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_LOCAL_ACCOUNTS, "id = ?", arrayOf(accountId))
            db.delete(TABLE_LOCAL_TRANSACTIONS, "account_id = ?", arrayOf(accountId))
            dao.deleteSnapshotsForAccount(accountId)
            dao.deleteAdjustmentsForAccount(accountId)
            dao.deleteStatementsForAccount(accountId)
            dao.deleteCcPaymentsForCard(accountId)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
    }

    private fun insertAccountInternal(db: SupportSQLiteDatabase, acc: AccountEntity) {
        val cv = ContentValues().apply {
            put("id", acc.id)
            put("user_id", acc.userId)
            put("bank_name", acc.bankName)
            put("account_number_mask", acc.accountNumberMask)
            put("account_name", acc.accountName)
            put("account_type", acc.accountType)
            put("initial_balance", MoneyMath.round2(acc.initialBalance))
            put("opening_balance", MoneyMath.round2(acc.openingBalance))
            put("current_balance", MoneyMath.round2(acc.currentBalance))
            if (acc.availableBalance != null) put("available_balance", MoneyMath.round2(acc.availableBalance))
            else putNull("available_balance")
            put("balance_source", acc.balanceSource)
            put("reconciliation_status", acc.reconciliationStatus)
            if (acc.creditLimit != null) put("credit_limit", MoneyMath.round2(acc.creditLimit))
            else putNull("credit_limit")
            if (acc.statementDay != null) put("statement_day", acc.statementDay) else putNull("statement_day")
            if (acc.dueDay != null) put("due_day", acc.dueDay) else putNull("due_day")
            put("billing_cycle", acc.billingCycle)
            put("account_status", acc.accountStatus)
            put("last_synced_at_iso", acc.lastSyncedAtIso)
            put("pending_sync", if (acc.pendingSync) 1 else 0)
            put("deleted", 0)
            put("updated_at_ms", acc.updatedAtMs)
        }
        db.insert(TABLE_LOCAL_ACCOUNTS, SQLiteDatabase.CONFLICT_REPLACE, cv)
    }

    @Synchronized
    fun getAccounts(): List<AccountEntity> {
        val list = ArrayList<AccountEntity>()
        readableDatabase.query("SELECT * FROM $TABLE_LOCAL_ACCOUNTS WHERE deleted = 0 ORDER BY current_balance DESC").use { c ->
            while (c.moveToNext()) {
                list.add(readAccountRow(c))
            }
        }
        return list
    }

    @Synchronized
    fun getAccountById(id: String): AccountEntity? {
        readableDatabase.query(
            "SELECT * FROM $TABLE_LOCAL_ACCOUNTS WHERE id = ? AND deleted = 0",
            arrayOf<Any>(id)
        ).use { c ->
            return if (c.moveToFirst()) readAccountRow(c) else null
        }
    }

    private fun readAccountRow(c: Cursor): AccountEntity {
        val availIdx = c.getColumnIndexOrThrow("available_balance")
        val limitIdx = c.getColumnIndexOrThrow("credit_limit")
        val stmtDayIdx = c.getColumnIndexOrThrow("statement_day")
        val dueDayIdx = c.getColumnIndexOrThrow("due_day")
        return AccountEntity(
            id = c.getString(c.getColumnIndexOrThrow("id")),
            userId = c.getString(c.getColumnIndexOrThrow("user_id")) ?: "",
            bankName = c.getString(c.getColumnIndexOrThrow("bank_name")),
            accountNumberMask = c.getString(c.getColumnIndexOrThrow("account_number_mask")),
            accountName = c.getString(c.getColumnIndexOrThrow("account_name")),
            accountType = c.getString(c.getColumnIndexOrThrow("account_type")) ?: "SAVINGS",
            initialBalance = c.getDouble(c.getColumnIndexOrThrow("initial_balance")),
            openingBalance = c.getDouble(c.getColumnIndexOrThrow("opening_balance")),
            currentBalance = c.getDouble(c.getColumnIndexOrThrow("current_balance")),
            availableBalance = if (c.isNull(availIdx)) null else c.getDouble(availIdx),
            balanceSource = c.getString(c.getColumnIndexOrThrow("balance_source")) ?: "CALCULATED",
            reconciliationStatus = c.getString(c.getColumnIndexOrThrow("reconciliation_status")) ?: "CALCULATED_ONLY",
            creditLimit = if (c.isNull(limitIdx)) null else c.getDouble(limitIdx),
            statementDay = if (c.isNull(stmtDayIdx)) null else c.getInt(stmtDayIdx),
            dueDay = if (c.isNull(dueDayIdx)) null else c.getInt(dueDayIdx),
            billingCycle = c.getString(c.getColumnIndexOrThrow("billing_cycle")),
            accountStatus = c.getString(c.getColumnIndexOrThrow("account_status")) ?: "ACTIVE",
            lastSyncedAtIso = c.getString(c.getColumnIndexOrThrow("last_synced_at_iso")),
            pendingSync = c.getInt(c.getColumnIndexOrThrow("pending_sync")) == 1,
            updatedAtMs = c.getLong(c.getColumnIndexOrThrow("updated_at_ms"))
        )
    }

    @Synchronized
    fun applySmsToLocalAccount(parsed: ParsedSms, userId: String): AccountEntity {
        val db = writableDatabase
        val existing = getAccounts().firstOrNull {
            it.bankName.equals(parsed.bankName, ignoreCase = true) &&
                it.accountNumberMask.equals(parsed.accountMask, ignoreCase = true)
        }
        val isCreditCard = parsed.isCardTransaction ||
            parsed.transactionType == "CC_PURCHASE" ||
            existing?.accountType == "CREDIT_CARD" ||
            Regex("credit card|card ending", RegexOption.IGNORE_CASE)
                .containsMatchIn("${parsed.bankName} ${parsed.merchant.orEmpty()}")

        val balanceSource = if (parsed.parsedAvailableBalance != null) "BANK_REPORTED" else "CALCULATED"

        if (existing != null) {
            val lastMs = BankSmsParser.parseIsoToMillis(existing.lastSyncedAtIso)
            val shouldUpdate = existing.lastSyncedAtIso.isNullOrBlank() || parsed.transactionDateMs >= lastMs
            if (shouldUpdate) {
                val nextBal = if (isCreditCard) {
                    if (parsed.direction == "DEBIT") MoneyMath.add(existing.currentBalance, parsed.amount)
                    else maxOf(0.0, MoneyMath.subtract(existing.currentBalance, parsed.amount))
                } else {
                    parsed.parsedAvailableBalance
                        ?: if (parsed.direction == "CREDIT") MoneyMath.add(existing.currentBalance, parsed.amount)
                        else MoneyMath.subtract(existing.currentBalance, parsed.amount)
                }
                val updated = existing.copy(
                    currentBalance = MoneyMath.round2(nextBal),
                    availableBalance = parsed.parsedAvailableBalance ?: existing.availableBalance,
                    balanceSource = balanceSource,
                    lastSyncedAtIso = parsed.transactionDateIso,
                    updatedAtMs = System.currentTimeMillis()
                )
                insertAccountInternal(db, updated)
                return updated
            }
            return existing
        } else {
            val type = when {
                isCreditCard -> "CREDIT_CARD"
                Regex("phonepe|google pay|paytm|amazon pay", RegexOption.IGNORE_CASE)
                    .containsMatchIn(parsed.bankName) -> "WALLET"
                else -> "SAVINGS"
            }
            val initBal = if (type == "CREDIT_CARD") {
                if (parsed.direction == "DEBIT") parsed.amount else 0.0
            } else {
                parsed.parsedAvailableBalance
                    ?: if (parsed.direction == "CREDIT") parsed.amount else 0.0
            }
            val created = AccountEntity(
                id = "local-acc-${UUID.randomUUID()}",
                userId = userId,
                bankName = parsed.bankName,
                accountNumberMask = parsed.accountMask,
                accountType = type,
                initialBalance = 0.0,
                openingBalance = 0.0,
                currentBalance = MoneyMath.round2(initBal),
                availableBalance = parsed.parsedAvailableBalance,
                balanceSource = balanceSource,
                reconciliationStatus = if (parsed.parsedAvailableBalance != null && type != "CREDIT_CARD") {
                    "BANK_SNAPSHOT_AVAILABLE"
                } else "CALCULATED_ONLY",
                accountStatus = "ACTIVE",
                lastSyncedAtIso = parsed.transactionDateIso,
                pendingSync = true,
                updatedAtMs = System.currentTimeMillis()
            )
            insertAccountInternal(db, created)
            return created
        }
    }

    // ── TRANSACTIONS ─────────────────────────────────────────────────────────

    @Synchronized
    fun insertPendingTransaction(
        parsed: ParsedSms,
        sender: String = "",
        transactionSource: String = SOURCE_INCOMING_SMS,
        smsFingerprint: String = BankSmsParser.computeFingerprint(parsed),
        userId: String = "",
        rawBody: String = ""
    ): Boolean {
        return insertPendingTransactionReturningEntity(
            parsed = parsed,
            sender = sender,
            transactionSource = transactionSource,
            smsFingerprint = smsFingerprint,
            userId = userId,
            rawBody = rawBody
        ) != null
    }

    @Synchronized
    fun insertPendingTransactionReturningEntity(
        parsed: ParsedSms,
        sender: String = "",
        transactionSource: String = SOURCE_INCOMING_SMS,
        smsFingerprint: String = BankSmsParser.computeFingerprint(parsed),
        userId: String = "",
        rawBody: String = ""
    ): TransactionEntity? {
        val db = writableDatabase

        if (!parsed.referenceId.isNullOrBlank() && parsed.referenceId.trim().length >= 6) {
            db.query(
                "SELECT local_id FROM $TABLE_LOCAL_TRANSACTIONS WHERE reference_id = ? AND amount = ? AND direction = ? AND deleted = 0 LIMIT 1",
                arrayOf<Any>(parsed.referenceId.trim(), MoneyMath.round2(parsed.amount), parsed.direction)
            ).use { c ->
                if (c.moveToFirst()) return null
            }
        }

        val resolvedCategory = resolveCategoryForSms(parsed, rawBody)
        val localAccount = applySmsToLocalAccount(parsed, userId)
        val now = System.currentTimeMillis()
        val localId = UUID.randomUUID().toString()
        val roundedAmount = MoneyMath.round2(parsed.amount)

        val txn = TransactionEntity(
            localId = localId,
            remoteId = null,
            userId = userId,
            accountId = localAccount.id,
            categoryId = resolvedCategory?.id,
            smsFingerprint = smsFingerprint,
            amount = roundedAmount,
            direction = parsed.direction,
            transactionType = parsed.transactionType,
            bankName = parsed.bankName,
            accountMask = parsed.accountMask,
            merchant = parsed.merchant,
            referenceId = parsed.referenceId,
            paymentMode = parsed.paymentMode,
            parsedAvailableBalance = parsed.parsedAvailableBalance?.let { MoneyMath.round2(it) },
            isSelfTransfer = parsed.isSelfTransfer,
            isRefund = parsed.isRefund,
            isExcludedFromTotals = parsed.isExcludedFromTotals,
            linkedTransactionId = null,
            notes = null,
            currency = "INR",
            transactionSource = transactionSource,
            transactionStatus = "COMPLETED",
            confidenceScore = parsed.confidenceScore,
            duplicateStatus = "UNIQUE",
            transferStatus = if (parsed.isSelfTransfer) "CANDIDATE" else null,
            refundStatus = if (parsed.isRefund) "REFUND_DETECTED" else null,
            transactionDateIso = parsed.transactionDateIso,
            transactionDateMs = parsed.transactionDateMs,
            recordedAtMs = now,
            sender = sender.trim().ifBlank { null },
            syncStatus = STATUS_PENDING,
            retryCount = 0,
            lastAttemptAt = null,
            errorCode = null,
            createdAt = now,
            categoryName = resolvedCategory?.name ?: parsed.transactionCategory,
            categoryColor = resolvedCategory?.color ?: "#94A3B8",
            accountDisplayName = "${parsed.bankName} (${parsed.accountMask})"
        )

        val cv = buildTransactionContentValues(txn)
        val rowId = db.insert(TABLE_LOCAL_TRANSACTIONS, SQLiteDatabase.CONFLICT_IGNORE, cv)
        if (rowId != -1L) {
            insertSmsLogInternal(
                db,
                SmsLogEntity(
                    id = UUID.randomUUID().toString(),
                    deviceId = "android-native",
                    sender = sender.ifBlank { parsed.bankName },
                    status = "PROCESSED",
                    reason = "Extracted & saved locally",
                    extractedType = parsed.transactionType,
                    extractedAmount = roundedAmount,
                    extractedBank = parsed.bankName,
                    extractedDateIso = parsed.transactionDateIso,
                    referenceId = parsed.referenceId,
                    smsFingerprint = smsFingerprint,
                    createdAtMs = now
                )
            )
            notifyChanged()
            return txn
        }
        return null
    }

    @Synchronized
    fun insertLocalTransaction(txn: TransactionEntity) {
        insertTransactionInternal(writableDatabase, txn)
        notifyChanged()
    }

    @Synchronized
    fun replaceLocalTransactionWithCloud(oldLocalId: String, cloudTxn: TransactionEntity) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_LOCAL_TRANSACTIONS, "local_id = ?", arrayOf(oldLocalId))
            insertTransactionInternal(db, cloudTxn)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
    }

    @Synchronized
    fun replaceSyncedTransactionsFromCloud(cloudTxns: List<TransactionEntity>) {
        val pendingDeletedIds = dao.getAllSyncQueueItems()
            .filter { it.entityType == "TRANSACTION" && it.operation == "DELETE" }
            .map { it.entityId }
            .toSet()
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_LOCAL_TRANSACTIONS, "sync_status = ?", arrayOf(STATUS_SYNCED))
            for (tx in cloudTxns) {
                if (pendingDeletedIds.contains(tx.localId) || (tx.remoteId != null && pendingDeletedIds.contains(tx.remoteId))) {
                    continue
                }
                val existsPending = db.query(
                    "SELECT local_id FROM $TABLE_LOCAL_TRANSACTIONS WHERE sms_fingerprint = ? LIMIT 1",
                    arrayOf<Any>(tx.smsFingerprint)
                ).use { it.moveToFirst() }
                if (!existsPending) {
                    insertTransactionInternal(db, tx)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
    }

    private fun buildTransactionContentValues(txn: TransactionEntity): ContentValues {
        return ContentValues().apply {
            put("local_id", txn.localId)
            put("remote_id", txn.remoteId)
            put("user_id", txn.userId)
            put("account_id", txn.accountId)
            put("category_id", txn.categoryId)
            put("sms_fingerprint", txn.smsFingerprint)
            put("amount", MoneyMath.round2(txn.amount))
            put("direction", txn.direction)
            put("transaction_type", txn.transactionType)
            put("bank_name", txn.bankName)
            put("account_mask", txn.accountMask)
            if (!txn.merchant.isNullOrBlank()) put("merchant", txn.merchant) else putNull("merchant")
            if (!txn.referenceId.isNullOrBlank()) put("reference_id", txn.referenceId) else putNull("reference_id")
            put("payment_mode", txn.paymentMode)
            if (txn.parsedAvailableBalance != null) put("parsed_available_balance", MoneyMath.round2(txn.parsedAvailableBalance))
            else putNull("parsed_available_balance")
            put("is_self_transfer", if (txn.isSelfTransfer) 1 else 0)
            put("is_refund", if (txn.isRefund) 1 else 0)
            put("is_excluded_from_totals", if (txn.isExcludedFromTotals) 1 else 0)
            if (!txn.linkedTransactionId.isNullOrBlank()) put("linked_transaction_id", txn.linkedTransactionId)
            else putNull("linked_transaction_id")
            if (!txn.notes.isNullOrBlank()) put("notes", txn.notes) else putNull("notes")
            put("currency", txn.currency)
            put("transaction_source", txn.transactionSource)
            put("transaction_status", txn.transactionStatus)
            put("confidence_score", txn.confidenceScore)
            put("duplicate_status", txn.duplicateStatus)
            if (!txn.transferStatus.isNullOrBlank()) put("transfer_status", txn.transferStatus) else putNull("transfer_status")
            if (!txn.refundStatus.isNullOrBlank()) put("refund_status", txn.refundStatus) else putNull("refund_status")
            put("transaction_date_iso", txn.transactionDateIso)
            put("transaction_date_ms", txn.transactionDateMs)
            put("recorded_at_ms", if (txn.recordedAtMs > 0L) txn.recordedAtMs else txn.createdAt)
            if (!txn.sender.isNullOrBlank()) put("sender", txn.sender) else putNull("sender")
            put("sync_status", txn.syncStatus)
            put("retry_count", txn.retryCount)
            if (txn.lastAttemptAt != null) put("last_attempt_at", txn.lastAttemptAt) else putNull("last_attempt_at")
            if (!txn.errorCode.isNullOrBlank()) put("error_code", txn.errorCode) else putNull("error_code")
            put("deleted", 0)
            put("category_name", txn.categoryName)
            put("category_color", txn.categoryColor)
            put("account_display_name", txn.accountDisplayName ?: "${txn.bankName} (${txn.accountMask})")
            put("created_at", txn.createdAt)
        }
    }

    private fun insertTransactionInternal(db: SupportSQLiteDatabase, txn: TransactionEntity) {
        db.insert(TABLE_LOCAL_TRANSACTIONS, SQLiteDatabase.CONFLICT_REPLACE, buildTransactionContentValues(txn))
    }

    @Synchronized
    fun getAllTransactions(limit: Int = 2000): List<TransactionEntity> {
        val results = ArrayList<TransactionEntity>()
        val safeLimit = limit.coerceIn(1, 5000)
        readableDatabase.query(
            "SELECT * FROM $TABLE_LOCAL_TRANSACTIONS WHERE deleted = 0 ORDER BY transaction_date_ms DESC, created_at DESC LIMIT $safeLimit"
        ).use { c ->
            while (c.moveToNext()) {
                results.add(readTransactionRow(c))
            }
        }
        return results
    }

    @Synchronized
    fun getTransactionsInRange(startMs: Long, endMs: Long): List<TransactionEntity> {
        val results = ArrayList<TransactionEntity>()
        readableDatabase.query(
            "SELECT * FROM $TABLE_LOCAL_TRANSACTIONS WHERE deleted = 0 AND transaction_date_ms >= ? AND transaction_date_ms <= ? ORDER BY transaction_date_ms ASC",
            arrayOf(startMs.toString(), endMs.toString())
        ).use { c ->
            while (c.moveToNext()) {
                results.add(readTransactionRow(c))
            }
        }
        return results
    }

    @Synchronized
    fun updateTransactionLocal(
        localId: String,
        categoryId: String?,
        categoryName: String?,
        categoryColor: String?,
        notes: String?,
        merchant: String?,
        transactionType: String?,
        amount: Double? = null,
        direction: String? = null,
        accountId: String? = null,
        bankName: String? = null,
        accountMask: String? = null,
        accountDisplayName: String? = null,
        transactionDateMs: Long? = null,
        transactionDateIso: String? = null
    ) {
        val cv = ContentValues().apply {
            if (categoryId.isNullOrBlank()) putNull("category_id") else put("category_id", categoryId)
            put("category_name", categoryName)
            put("category_color", categoryColor)
            if (notes.isNullOrBlank()) putNull("notes") else put("notes", notes)
            if (!merchant.isNullOrBlank()) put("merchant", merchant)
            if (!transactionType.isNullOrBlank()) {
                put("transaction_type", transactionType)
                val isTransfer = transactionType == "TRANSFER"
                val isRefund = transactionType == "REFUND" || transactionType == "REVERSAL"
                val isExcluded = isTransfer || transactionType == "CC_BILL_PAYMENT"
                put("is_self_transfer", if (isTransfer) 1 else 0)
                put("is_refund", if (isRefund) 1 else 0)
                put("transfer_status", if (isTransfer) "CONFIRMED" else "NONE")
                put("refund_status", if (isRefund) "CONFIRMED" else "NONE")
                put("is_excluded_from_totals", if (isExcluded) 1 else 0)
            }
            if (amount != null && amount > 0.0) {
                put("amount", MoneyMath.round2(amount))
            }
            if (!direction.isNullOrBlank()) {
                put("direction", direction)
            }
            if (accountId != null) {
                if (accountId.isBlank()) putNull("account_id") else put("account_id", accountId)
            }
            if (!bankName.isNullOrBlank()) put("bank_name", bankName)
            if (!accountMask.isNullOrBlank()) put("account_mask", accountMask)
            if (!accountDisplayName.isNullOrBlank()) put("account_display_name", accountDisplayName)
            if (transactionDateMs != null && transactionDateMs > 0L) {
                put("transaction_date_ms", transactionDateMs)
            }
            if (!transactionDateIso.isNullOrBlank()) {
                put("transaction_date_iso", transactionDateIso)
            }
        }
        writableDatabase.update(TABLE_LOCAL_TRANSACTIONS, SQLiteDatabase.CONFLICT_REPLACE, cv, "local_id = ?", arrayOf(localId))
        notifyChanged()
    }

    @Synchronized
    fun updateTransactionAutomationFlags(
        localId: String,
        duplicateStatus: String? = null,
        transferStatus: String? = null,
        refundStatus: String? = null,
        linkedTransactionId: String? = null,
        isSelfTransfer: Boolean? = null,
        isRefund: Boolean? = null,
        isExcludedFromTotals: Boolean? = null,
        transactionType: String? = null
    ) {
        val cv = ContentValues().apply {
            if (duplicateStatus != null) put("duplicate_status", duplicateStatus)
            if (transferStatus != null) put("transfer_status", transferStatus)
            if (refundStatus != null) put("refund_status", refundStatus)
            if (linkedTransactionId != null) put("linked_transaction_id", linkedTransactionId)
            if (isSelfTransfer != null) put("is_self_transfer", if (isSelfTransfer) 1 else 0)
            if (isRefund != null) put("is_refund", if (isRefund) 1 else 0)
            if (isExcludedFromTotals != null) put("is_excluded_from_totals", if (isExcludedFromTotals) 1 else 0)
            if (transactionType != null) put("transaction_type", transactionType)
        }
        if (cv.size() > 0) {
            writableDatabase.update(TABLE_LOCAL_TRANSACTIONS, SQLiteDatabase.CONFLICT_REPLACE, cv, "local_id = ?", arrayOf(localId))
            notifyChanged()
        }
    }

    @Synchronized
    fun deleteTransactionLocal(localId: String) {
        writableDatabase.delete(TABLE_LOCAL_TRANSACTIONS, "local_id = ?", arrayOf(localId))
        notifyChanged()
    }

    @Synchronized
    fun getPendingTransactions(limit: Int = 50): List<TransactionEntity> {
        val safeLimit = limit.coerceIn(1, 500)
        val results = ArrayList<TransactionEntity>()
        readableDatabase.query(
            """
            SELECT * FROM $TABLE_LOCAL_TRANSACTIONS
            WHERE deleted = 0 AND sync_status IN (?, ?, ?) AND retry_count < ? AND duplicate_status != 'EXACT_DUPLICATE'
            ORDER BY created_at ASC LIMIT $safeLimit
            """.trimIndent(),
            arrayOf<Any>(STATUS_PENDING, STATUS_FAILED, STATUS_RETRY_SCHEDULED, MAX_RETRIES)
        ).use { c ->
            while (c.moveToNext()) {
                results.add(readTransactionRow(c))
            }
        }
        return results
    }

    private fun readTransactionRow(c: Cursor): TransactionEntity {
        val dateIso = c.getString(c.getColumnIndexOrThrow("transaction_date_iso")) ?: ""
        val dateMs = c.getLong(c.getColumnIndexOrThrow("transaction_date_ms"))
            .let { if (it > 0L) it else BankSmsParser.parseIsoToMillis(dateIso) }
        val balIdx = c.getColumnIndexOrThrow("parsed_available_balance")
        val lastAttIdx = c.getColumnIndexOrThrow("last_attempt_at")
        val recIdx = c.getColumnIndex("recorded_at_ms")
        val confIdx = c.getColumnIndex("confidence_score")
        val dupIdx = c.getColumnIndex("duplicate_status")
        val trfIdx = c.getColumnIndex("transfer_status")
        val refIdx = c.getColumnIndex("refund_status")
        val linkIdx = c.getColumnIndex("linked_transaction_id")
        val createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
        return TransactionEntity(
            localId = c.getString(c.getColumnIndexOrThrow("local_id")),
            remoteId = c.getString(c.getColumnIndexOrThrow("remote_id")),
            userId = c.getString(c.getColumnIndexOrThrow("user_id")) ?: "",
            accountId = c.getString(c.getColumnIndexOrThrow("account_id")),
            categoryId = c.getString(c.getColumnIndexOrThrow("category_id")),
            smsFingerprint = c.getString(c.getColumnIndexOrThrow("sms_fingerprint")) ?: "",
            amount = c.getDouble(c.getColumnIndexOrThrow("amount")),
            direction = c.getString(c.getColumnIndexOrThrow("direction")) ?: "DEBIT",
            transactionType = c.getString(c.getColumnIndexOrThrow("transaction_type")) ?: "EXPENSE",
            bankName = c.getString(c.getColumnIndexOrThrow("bank_name")) ?: "Bank",
            accountMask = c.getString(c.getColumnIndexOrThrow("account_mask")) ?: "XX0000",
            merchant = c.getString(c.getColumnIndexOrThrow("merchant")),
            referenceId = c.getString(c.getColumnIndexOrThrow("reference_id")),
            paymentMode = c.getString(c.getColumnIndexOrThrow("payment_mode")) ?: "UPI",
            parsedAvailableBalance = if (c.isNull(balIdx)) null else c.getDouble(balIdx),
            isSelfTransfer = c.getInt(c.getColumnIndexOrThrow("is_self_transfer")) == 1,
            isRefund = c.getInt(c.getColumnIndexOrThrow("is_refund")) == 1,
            isExcludedFromTotals = c.getInt(c.getColumnIndexOrThrow("is_excluded_from_totals")) == 1,
            linkedTransactionId = if (linkIdx >= 0) c.getString(linkIdx) else null,
            notes = c.getString(c.getColumnIndexOrThrow("notes")),
            currency = c.getString(c.getColumnIndexOrThrow("currency")) ?: "INR",
            transactionSource = c.getString(c.getColumnIndexOrThrow("transaction_source")) ?: SOURCE_INCOMING_SMS,
            transactionStatus = c.getString(c.getColumnIndexOrThrow("transaction_status")) ?: "COMPLETED",
            confidenceScore = if (confIdx >= 0) c.getDouble(confIdx) else 1.0,
            duplicateStatus = if (dupIdx >= 0) (c.getString(dupIdx) ?: "UNIQUE") else "UNIQUE",
            transferStatus = if (trfIdx >= 0) c.getString(trfIdx) else null,
            refundStatus = if (refIdx >= 0) c.getString(refIdx) else null,
            transactionDateIso = dateIso,
            transactionDateMs = dateMs,
            recordedAtMs = if (recIdx >= 0 && c.getLong(recIdx) > 0L) c.getLong(recIdx) else createdAt,
            sender = c.getString(c.getColumnIndexOrThrow("sender")),
            syncStatus = c.getString(c.getColumnIndexOrThrow("sync_status")) ?: STATUS_PENDING,
            retryCount = c.getInt(c.getColumnIndexOrThrow("retry_count")),
            lastAttemptAt = if (c.isNull(lastAttIdx)) null else c.getLong(lastAttIdx),
            errorCode = c.getString(c.getColumnIndexOrThrow("error_code")),
            createdAt = createdAt,
            categoryName = c.getString(c.getColumnIndexOrThrow("category_name")),
            categoryColor = c.getString(c.getColumnIndexOrThrow("category_color")),
            accountDisplayName = c.getString(c.getColumnIndexOrThrow("account_display_name"))
        )
    }

    @Synchronized
    fun getPendingCount(): Int {
        readableDatabase.query(
            "SELECT COUNT(*) FROM $TABLE_LOCAL_TRANSACTIONS WHERE deleted = 0 AND sync_status IN (?, ?, ?) AND retry_count < ? AND duplicate_status != 'EXACT_DUPLICATE'",
            arrayOf<Any>(STATUS_PENDING, STATUS_FAILED, STATUS_RETRY_SCHEDULED, MAX_RETRIES)
        ).use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    @Synchronized
    fun markSyncStatus(localId: String, status: String, errorCode: String? = null, remoteId: String? = null) {
        val now = System.currentTimeMillis()
        if (!remoteId.isNullOrBlank()) {
            writableDatabase.execSQL(
                """
                UPDATE $TABLE_LOCAL_TRANSACTIONS
                SET sync_status = ?,
                    remote_id = ?,
                    retry_count = retry_count + 1,
                    last_attempt_at = ?,
                    error_code = ?
                WHERE local_id = ?
                """.trimIndent(),
                arrayOf<Any?>(status, remoteId, now, errorCode, localId)
            )
        } else {
            writableDatabase.execSQL(
                """
                UPDATE $TABLE_LOCAL_TRANSACTIONS
                SET sync_status = ?,
                    retry_count = retry_count + 1,
                    last_attempt_at = ?,
                    error_code = ?
                WHERE local_id = ?
                """.trimIndent(),
                arrayOf<Any?>(status, now, errorCode, localId)
            )
        }
        notifyChanged()
    }

    // ── SMS LOGS & AUTOMATION LOGS ───────────────────────────────────────────

    @Synchronized
    fun logSmsProcessing(log: SmsLogEntity) {
        insertSmsLogInternal(writableDatabase, log)
        notifyChanged()
    }

    @Synchronized
    fun replaceSmsLogsFromCloud(logs: List<SmsLogEntity>) {
        if (logs.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_LOCAL_SMS_LOGS, null, null)
            for (l in logs) insertSmsLogInternal(db, l)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
    }

    private fun insertSmsLogInternal(db: SupportSQLiteDatabase, log: SmsLogEntity) {
        val cv = ContentValues().apply {
            put("id", log.id)
            put("device_id", log.deviceId)
            put("sender", log.sender)
            put("status", log.status)
            put("reason", log.reason)
            put("extracted_type", log.extractedType)
            if (log.extractedAmount != null) put("extracted_amount", log.extractedAmount) else putNull("extracted_amount")
            put("extracted_bank", log.extractedBank)
            put("extracted_date_iso", log.extractedDateIso)
            put("reference_id", log.referenceId)
            put("sms_fingerprint", log.smsFingerprint)
            put("created_at_ms", log.createdAtMs)
        }
        db.insert(TABLE_LOCAL_SMS_LOGS, SQLiteDatabase.CONFLICT_REPLACE, cv)
    }

    @Synchronized
    fun getSmsLogs(limit: Int = 100): List<SmsLogEntity> {
        val list = ArrayList<SmsLogEntity>()
        val safeLimit = limit.coerceIn(1, 500)
        readableDatabase.query("SELECT * FROM $TABLE_LOCAL_SMS_LOGS ORDER BY created_at_ms DESC LIMIT $safeLimit").use { c ->
            while (c.moveToNext()) {
                val amtIdx = c.getColumnIndexOrThrow("extracted_amount")
                list.add(
                    SmsLogEntity(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        deviceId = c.getString(c.getColumnIndexOrThrow("device_id")),
                        sender = c.getString(c.getColumnIndexOrThrow("sender")),
                        status = c.getString(c.getColumnIndexOrThrow("status")),
                        reason = c.getString(c.getColumnIndexOrThrow("reason")),
                        extractedType = c.getString(c.getColumnIndexOrThrow("extracted_type")),
                        extractedAmount = if (c.isNull(amtIdx)) null else c.getDouble(amtIdx),
                        extractedBank = c.getString(c.getColumnIndexOrThrow("extracted_bank")),
                        extractedDateIso = c.getString(c.getColumnIndexOrThrow("extracted_date_iso")),
                        referenceId = c.getString(c.getColumnIndexOrThrow("reference_id")),
                        smsFingerprint = c.getString(c.getColumnIndexOrThrow("sms_fingerprint")),
                        createdAtMs = c.getLong(c.getColumnIndexOrThrow("created_at_ms"))
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun getSyncState(): SmsSyncStateRecord {
        val db = writableDatabase
        ensureSyncStateRow(db)
        db.query("SELECT * FROM $TABLE_SMS_SYNC_STATE WHERE id = ?", arrayOf<Any>(SYNC_STATE_ROW_ID)).use { c ->
            if (c.moveToFirst()) {
                return SmsSyncStateRecord(
                    id = c.getInt(c.getColumnIndexOrThrow("id")),
                    historicalScanStatus = c.getString(c.getColumnIndexOrThrow("historical_scan_status"))
                        ?: SCAN_PENDING,
                    lastProcessedSmsTs = c.getLong(c.getColumnIndexOrThrow("last_processed_sms_ts")),
                    scannedCount = c.getInt(c.getColumnIndexOrThrow("scanned_count")),
                    detectedCount = c.getInt(c.getColumnIndexOrThrow("detected_count")),
                    monitoringStatus = c.getString(c.getColumnIndexOrThrow("monitoring_status")) ?: "INACTIVE",
                    lastSyncAt = c.getLong(c.getColumnIndexOrThrow("last_sync_at")),
                    updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"))
                )
            }
        }
        return SmsSyncStateRecord()
    }

    @Synchronized
    fun updateSyncState(
        historicalScanStatus: String? = null,
        lastProcessedSmsTs: Long? = null,
        scannedCount: Int? = null,
        detectedCount: Int? = null,
        monitoringStatus: String? = null,
        lastSyncAt: Long? = null
    ) {
        val db = writableDatabase
        ensureSyncStateRow(db)
        val values = ContentValues().apply {
            if (historicalScanStatus != null) put("historical_scan_status", historicalScanStatus)
            if (lastProcessedSmsTs != null) put("last_processed_sms_ts", lastProcessedSmsTs)
            if (scannedCount != null) put("scanned_count", scannedCount)
            if (detectedCount != null) put("detected_count", detectedCount)
            if (monitoringStatus != null) put("monitoring_status", monitoringStatus)
            if (lastSyncAt != null) put("last_sync_at", lastSyncAt)
            put("updated_at", System.currentTimeMillis())
        }
        db.update(
            TABLE_SMS_SYNC_STATE,
            SQLiteDatabase.CONFLICT_REPLACE,
            values,
            "id = ?",
            arrayOf(SYNC_STATE_ROW_ID.toString())
        )
        notifyChanged()
    }

    @Synchronized
    fun logAutomation(
        processType: String,
        processStatus: String,
        errorCode: String? = null,
        details: String? = null
    ) {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("process_type", processType)
            put("process_status", processStatus)
            if (errorCode != null) put("error_code", errorCode) else putNull("error_code")
            if (details != null) put("details", details) else putNull("details")
            put("created_at", now)
        }
        db.insert(TABLE_AUTOMATION_LOGS, SQLiteDatabase.CONFLICT_REPLACE, values)
        db.execSQL(
            """
            DELETE FROM $TABLE_AUTOMATION_LOGS
            WHERE id NOT IN (
                SELECT id FROM $TABLE_AUTOMATION_LOGS ORDER BY id DESC LIMIT 300
            )
            """.trimIndent()
        )
        notifyChanged()
    }

    @Synchronized
    fun getAutomationLogs(limit: Int = 80): List<AutomationLogEntity> {
        val list = ArrayList<AutomationLogEntity>()
        val safeLimit = limit.coerceIn(1, 300)
        readableDatabase.query("SELECT * FROM $TABLE_AUTOMATION_LOGS ORDER BY id DESC LIMIT $safeLimit").use { c ->
            while (c.moveToNext()) {
                list.add(
                    AutomationLogEntity(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        processType = c.getString(c.getColumnIndexOrThrow("process_type")),
                        processStatus = c.getString(c.getColumnIndexOrThrow("process_status")),
                        errorCode = c.getString(c.getColumnIndexOrThrow("error_code")),
                        details = c.getString(c.getColumnIndexOrThrow("details")),
                        createdAtMs = c.getLong(c.getColumnIndexOrThrow("created_at"))
                    )
                )
            }
        }
        return list
    }

    // ── MONZI 2.1 ROOM REPOSITORIES ──────────────────────────────────────────

    @Synchronized
    fun insertSnapshot(item: AccountBalanceSnapshotEntity) {
        dao.insertSnapshot(
            RoomBalanceSnapshot(
                id = item.id,
                userId = item.userId,
                accountId = item.accountId,
                reportedBalance = MoneyMath.round2(item.reportedBalance),
                calculatedBalanceAtSnapshot = MoneyMath.round2(item.calculatedBalanceAtSnapshot),
                discrepancyAmount = MoneyMath.round2(item.discrepancyAmount),
                sourceTransactionId = item.sourceTransactionId,
                snapshotAtMs = item.snapshotAtMs,
                createdAtMs = item.createdAtMs
            )
        )
        notifyChanged()
    }

    @Synchronized
    fun getSnapshotsForAccount(accountId: String): List<AccountBalanceSnapshotEntity> =
        dao.getSnapshotsForAccount(accountId).map {
            AccountBalanceSnapshotEntity(
                id = it.id,
                userId = it.userId,
                accountId = it.accountId,
                reportedBalance = it.reportedBalance,
                calculatedBalanceAtSnapshot = it.calculatedBalanceAtSnapshot,
                discrepancyAmount = it.discrepancyAmount,
                sourceTransactionId = it.sourceTransactionId,
                snapshotAtMs = it.snapshotAtMs,
                createdAtMs = it.createdAtMs
            )
        }

    @Synchronized
    fun getAllSnapshots(): List<AccountBalanceSnapshotEntity> =
        dao.getAllSnapshots().map {
            AccountBalanceSnapshotEntity(
                id = it.id,
                userId = it.userId,
                accountId = it.accountId,
                reportedBalance = it.reportedBalance,
                calculatedBalanceAtSnapshot = it.calculatedBalanceAtSnapshot,
                discrepancyAmount = it.discrepancyAmount,
                sourceTransactionId = it.sourceTransactionId,
                snapshotAtMs = it.snapshotAtMs,
                createdAtMs = it.createdAtMs
            )
        }

    @Synchronized
    fun insertAdjustment(item: AccountBalanceAdjustmentEntity) {
        dao.insertAdjustment(
            RoomBalanceAdjustment(
                id = item.id,
                userId = item.userId,
                accountId = item.accountId,
                adjustmentAmount = MoneyMath.round2(item.adjustmentAmount),
                reason = item.reason,
                approved = if (item.approved) 1 else 0,
                createdAtMs = item.createdAtMs
            )
        )
        notifyChanged()
    }

    @Synchronized
    fun getAllAdjustments(): List<AccountBalanceAdjustmentEntity> =
        dao.getAllAdjustments().map {
            AccountBalanceAdjustmentEntity(
                id = it.id,
                userId = it.userId,
                accountId = it.accountId,
                adjustmentAmount = it.adjustmentAmount,
                reason = it.reason,
                approved = it.approved == 1,
                createdAtMs = it.createdAtMs
            )
        }

    @Synchronized
    fun upsertTransfer(item: TransferRecordEntity) {
        dao.insertTransfer(
            RoomTransfer(
                id = item.id,
                userId = item.userId,
                sourceAccountId = item.sourceAccountId,
                destinationAccountId = item.destinationAccountId,
                debitTransactionId = item.debitTransactionId,
                creditTransactionId = item.creditTransactionId,
                amount = MoneyMath.round2(item.amount),
                transferType = item.transferType,
                status = item.status,
                referenceId = item.referenceId,
                transferDateMs = item.transferDateMs,
                createdAtMs = item.createdAtMs
            )
        )
        notifyChanged()
    }

    @Synchronized
    fun getAllTransfers(): List<TransferRecordEntity> =
        dao.getAllTransfers().map {
            TransferRecordEntity(
                id = it.id,
                userId = it.userId,
                sourceAccountId = it.sourceAccountId,
                destinationAccountId = it.destinationAccountId,
                debitTransactionId = it.debitTransactionId,
                creditTransactionId = it.creditTransactionId,
                amount = it.amount,
                transferType = it.transferType,
                status = it.status,
                referenceId = it.referenceId,
                transferDateMs = it.transferDateMs,
                createdAtMs = it.createdAtMs
            )
        }

    @Synchronized
    fun updateTransferStatus(id: String, status: String) {
        dao.updateTransferStatus(id, status)
        notifyChanged()
    }

    @Synchronized
    fun upsertRefund(item: RefundRecordEntity) {
        dao.insertRefund(
            RoomRefund(
                id = item.id,
                userId = item.userId,
                refundTransactionId = item.refundTransactionId,
                originalTransactionId = item.originalTransactionId,
                refundType = item.refundType,
                refundAmount = MoneyMath.round2(item.refundAmount),
                status = item.status,
                createdAtMs = item.createdAtMs
            )
        )
        notifyChanged()
    }

    @Synchronized
    fun getAllRefunds(): List<RefundRecordEntity> =
        dao.getAllRefunds().map {
            RefundRecordEntity(
                id = it.id,
                userId = it.userId,
                refundTransactionId = it.refundTransactionId,
                originalTransactionId = it.originalTransactionId,
                refundType = it.refundType,
                refundAmount = it.refundAmount,
                status = it.status,
                createdAtMs = it.createdAtMs
            )
        }

    @Synchronized
    fun upsertStatement(item: CreditCardStatementEntity) {
        dao.insertStatement(
            RoomCreditCardStatement(
                id = item.id,
                userId = item.userId,
                accountId = item.accountId,
                statementPeriod = item.statementPeriod,
                statementDateMs = item.statementDateMs,
                totalDue = MoneyMath.round2(item.totalDue),
                minimumDue = MoneyMath.round2(item.minimumDue),
                dueDateMs = item.dueDateMs,
                paidAmount = MoneyMath.round2(item.paidAmount),
                paymentStatus = item.paymentStatus,
                updatedAtMs = item.updatedAtMs
            )
        )
        notifyChanged()
    }

    @Synchronized
    fun getAllStatements(): List<CreditCardStatementEntity> =
        dao.getAllStatements().map {
            CreditCardStatementEntity(
                id = it.id,
                userId = it.userId,
                accountId = it.accountId,
                statementPeriod = it.statementPeriod,
                statementDateMs = it.statementDateMs,
                totalDue = it.totalDue,
                minimumDue = it.minimumDue,
                dueDateMs = it.dueDateMs,
                paidAmount = it.paidAmount,
                paymentStatus = it.paymentStatus,
                updatedAtMs = it.updatedAtMs
            )
        }

    @Synchronized
    fun insertCcPayment(item: CreditCardPaymentEntity) {
        dao.insertCcPayment(
            RoomCreditCardPayment(
                id = item.id,
                userId = item.userId,
                cardAccountId = item.cardAccountId,
                sourceAccountId = item.sourceAccountId,
                statementId = item.statementId,
                transactionId = item.transactionId,
                amount = MoneyMath.round2(item.amount),
                paymentDateMs = item.paymentDateMs,
                createdAtMs = item.createdAtMs
            )
        )
        notifyChanged()
    }

    @Synchronized
    fun getAllCcPayments(): List<CreditCardPaymentEntity> =
        dao.getAllCcPayments().map {
            CreditCardPaymentEntity(
                id = it.id,
                userId = it.userId,
                cardAccountId = it.cardAccountId,
                sourceAccountId = it.sourceAccountId,
                statementId = it.statementId,
                transactionId = it.transactionId,
                amount = it.amount,
                paymentDateMs = it.paymentDateMs,
                createdAtMs = it.createdAtMs
            )
        }

    @Synchronized
    fun replaceBudgetsFromCloud(cloudBudgets: List<BudgetEntity>) {
        val pendingUpserts = dao.getAllSyncQueueItems()
            .filter { it.entityType == "BUDGET" && it.operation == "UPSERT" }
            .map { it.entityId }
            .toSet()
        val pendingDeletes = dao.getAllSyncQueueItems()
            .filter { it.entityType == "BUDGET" && it.operation == "DELETE" }
            .map { it.entityId }
            .toSet()
        val localPending = getAllBudgets().filter { pendingUpserts.contains(it.id) }
        dao.clearBudgets()
        for (b in cloudBudgets) {
            if (!pendingDeletes.contains(b.id) && !pendingUpserts.contains(b.id)) {
                upsertBudgetInternal(b)
            }
        }
        for (lb in localPending) {
            upsertBudgetInternal(lb)
        }
        notifyChanged()
    }

    @Synchronized
    fun replaceStatementsFromCloud(cloudStatements: List<CreditCardStatementEntity>) {
        if (cloudStatements.isEmpty()) return
        for (s in cloudStatements) {
            dao.insertStatement(
                RoomCreditCardStatement(
                    id = s.id,
                    userId = s.userId,
                    accountId = s.accountId,
                    statementPeriod = s.statementPeriod,
                    statementDateMs = s.statementDateMs,
                    totalDue = MoneyMath.round2(s.totalDue),
                    minimumDue = MoneyMath.round2(s.minimumDue),
                    dueDateMs = s.dueDateMs,
                    paidAmount = MoneyMath.round2(s.paidAmount),
                    paymentStatus = s.paymentStatus,
                    updatedAtMs = s.updatedAtMs
                )
            )
        }
        notifyChanged()
    }

    @Synchronized
    fun replaceCcPaymentsFromCloud(payments: List<CreditCardPaymentEntity>) {
        if (payments.isEmpty()) return
        for (p in payments) {
            dao.insertCcPayment(
                RoomCreditCardPayment(
                    id = p.id,
                    userId = p.userId,
                    cardAccountId = p.cardAccountId,
                    sourceAccountId = p.sourceAccountId,
                    statementId = p.statementId,
                    transactionId = p.transactionId,
                    amount = MoneyMath.round2(p.amount),
                    paymentDateMs = p.paymentDateMs,
                    createdAtMs = p.createdAtMs
                )
            )
        }
        notifyChanged()
    }

    @Synchronized
    fun replaceAdjustmentsFromCloud(adjustments: List<AccountBalanceAdjustmentEntity>) {
        if (adjustments.isEmpty()) return
        for (a in adjustments) {
            dao.insertAdjustment(
                RoomBalanceAdjustment(
                    id = a.id,
                    userId = a.userId,
                    accountId = a.accountId,
                    adjustmentAmount = MoneyMath.round2(a.adjustmentAmount),
                    reason = a.reason,
                    approved = if (a.approved) 1 else 0,
                    createdAtMs = a.createdAtMs
                )
            )
        }
        notifyChanged()
    }

    @Synchronized
    fun replaceSnapshotsFromCloud(snapshots: List<AccountBalanceSnapshotEntity>) {
        if (snapshots.isEmpty()) return
        for (s in snapshots) {
            dao.insertSnapshot(
                RoomBalanceSnapshot(
                    id = s.id,
                    userId = s.userId,
                    accountId = s.accountId,
                    reportedBalance = MoneyMath.round2(s.reportedBalance),
                    calculatedBalanceAtSnapshot = MoneyMath.round2(s.calculatedBalanceAtSnapshot),
                    discrepancyAmount = MoneyMath.round2(s.discrepancyAmount),
                    sourceTransactionId = s.sourceTransactionId,
                    snapshotAtMs = s.snapshotAtMs,
                    createdAtMs = s.createdAtMs
                )
            )
        }
        notifyChanged()
    }

    private fun upsertBudgetInternal(item: BudgetEntity) {
        dao.insertBudget(
            RoomBudget(
                id = item.id,
                userId = item.userId,
                name = item.name,
                categoryId = item.categoryId,
                categoryName = item.categoryName,
                categoryColor = item.categoryColor,
                monthlyLimit = MoneyMath.round2(item.monthlyLimit),
                startMonth = item.startMonth,
                alertThreshold = item.alertThreshold,
                carryForward = if (item.carryForward) 1 else 0,
                status = item.status,
                createdAtMs = item.createdAtMs
            )
        )
    }

    @Synchronized
    fun upsertBudget(item: BudgetEntity) {
        upsertBudgetInternal(item)
        notifyChanged()
    }

    @Synchronized
    fun getAllBudgets(): List<BudgetEntity> =
        dao.getAllBudgets().map {
            BudgetEntity(
                id = it.id,
                userId = it.userId,
                name = it.name,
                categoryId = it.categoryId,
                categoryName = it.categoryName,
                categoryColor = it.categoryColor,
                monthlyLimit = it.monthlyLimit,
                startMonth = it.startMonth,
                alertThreshold = it.alertThreshold,
                carryForward = it.carryForward == 1,
                status = it.status,
                createdAtMs = it.createdAtMs
            )
        }

    @Synchronized
    fun deleteBudget(id: String) {
        dao.deleteBudget(id)
        notifyChanged()
    }

    @Synchronized
    fun insertBudgetAlertIfNew(item: BudgetAlertEntity): Boolean {
        val insertedId = dao.insertBudgetAlert(
            RoomBudgetAlert(
                id = item.id,
                userId = item.userId,
                budgetId = item.budgetId,
                monthKey = item.monthKey,
                thresholdPct = item.thresholdPct,
                usagePct = MoneyMath.round2(item.usagePct),
                spentAmount = MoneyMath.round2(item.spentAmount),
                message = item.message,
                acknowledged = if (item.acknowledged) 1 else 0,
                triggeredAtMs = item.triggeredAtMs
            )
        )
        if (insertedId != -1L) {
            notifyChanged()
            return true
        }
        return false
    }

    @Synchronized
    fun getAllBudgetAlerts(): List<BudgetAlertEntity> =
        dao.getAllBudgetAlerts().map {
            BudgetAlertEntity(
                id = it.id,
                userId = it.userId,
                budgetId = it.budgetId,
                monthKey = it.monthKey,
                thresholdPct = it.thresholdPct,
                usagePct = it.usagePct,
                spentAmount = it.spentAmount,
                message = it.message,
                acknowledged = it.acknowledged == 1,
                triggeredAtMs = it.triggeredAtMs
            )
        }

    @Synchronized
    fun acknowledgeBudgetAlert(id: String) {
        dao.acknowledgeBudgetAlert(id)
        notifyChanged()
    }

    @Synchronized
    fun upsertImportRun(item: SmsImportRunEntity) {
        dao.upsertImportRun(
            RoomSmsImportRun(
                id = item.id,
                deviceId = item.deviceId,
                status = item.status,
                totalExamined = item.totalExamined,
                financialDetected = item.financialDetected,
                importedCount = item.importedCount,
                duplicateSkipped = item.duplicateSkipped,
                ignoredCount = item.ignoredCount,
                reviewCount = item.reviewCount,
                failureCount = item.failureCount,
                checkpointTs = item.checkpointTs,
                startedAtMs = item.startedAtMs,
                completedAtMs = item.completedAtMs
            )
        )
        notifyChanged()
    }

    @Synchronized
    fun getRecentImportRuns(): List<SmsImportRunEntity> =
        dao.getRecentImportRuns().map {
            SmsImportRunEntity(
                id = it.id,
                deviceId = it.deviceId,
                status = it.status,
                totalExamined = it.totalExamined,
                financialDetected = it.financialDetected,
                importedCount = it.importedCount,
                duplicateSkipped = it.duplicateSkipped,
                ignoredCount = it.ignoredCount,
                reviewCount = it.reviewCount,
                failureCount = it.failureCount,
                checkpointTs = it.checkpointTs,
                startedAtMs = it.startedAtMs,
                completedAtMs = it.completedAtMs
            )
        }

    @Synchronized
    fun insertParserFailure(item: SmsParserFailureEntity) {
        dao.insertParserFailure(
            RoomSmsParserFailure(
                id = item.id,
                deviceId = item.deviceId,
                sender = item.sender,
                errorReason = item.errorReason,
                occurredAtMs = item.occurredAtMs
            )
        )
        notifyChanged()
    }

    @Synchronized
    fun getParserFailures(): List<SmsParserFailureEntity> =
        dao.getParserFailures().map {
            SmsParserFailureEntity(
                id = it.id,
                deviceId = it.deviceId,
                sender = it.sender,
                errorReason = it.errorReason,
                occurredAtMs = it.occurredAtMs
            )
        }

    @Synchronized
    fun upsertSyncQueueItem(item: SyncQueueItemEntity) {
        dao.upsertSyncQueueItem(
            RoomSyncQueueItem(
                id = item.id,
                entityType = item.entityType,
                entityId = item.entityId,
                operation = item.operation,
                payloadJson = item.payloadJson,
                syncStatus = item.syncStatus,
                retryCount = item.retryCount,
                lastError = item.lastError,
                lastAttemptAtMs = item.lastAttemptAtMs,
                createdAtMs = item.createdAtMs
            )
        )
        notifyChanged()
    }

    @Synchronized
    fun getAllSyncQueueItems(): List<SyncQueueItemEntity> =
        dao.getAllSyncQueueItems().map {
            SyncQueueItemEntity(
                id = it.id,
                entityType = it.entityType,
                entityId = it.entityId,
                operation = it.operation,
                payloadJson = it.payloadJson,
                syncStatus = it.syncStatus,
                retryCount = it.retryCount,
                lastError = it.lastError,
                lastAttemptAtMs = it.lastAttemptAtMs,
                createdAtMs = it.createdAtMs
            )
        }

    @Synchronized
    fun getActionableSyncQueueItems(): List<SyncQueueItemEntity> =
        dao.getActionableSyncQueueItems().map {
            SyncQueueItemEntity(
                id = it.id,
                entityType = it.entityType,
                entityId = it.entityId,
                operation = it.operation,
                payloadJson = it.payloadJson,
                syncStatus = it.syncStatus,
                retryCount = it.retryCount,
                lastError = it.lastError,
                lastAttemptAtMs = it.lastAttemptAtMs,
                createdAtMs = it.createdAtMs
            )
        }

    @Synchronized
    fun deleteSyncQueueItem(id: String) {
        dao.deleteSyncQueueItem(id)
        notifyChanged()
    }

    @Synchronized
    fun clearAllUserFinanceData() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_LOCAL_TRANSACTIONS, null, null)
            db.delete(TABLE_LOCAL_ACCOUNTS, null, null)
            db.delete(TABLE_LOCAL_SMS_LOGS, null, null)
            db.delete(TABLE_LOCAL_RULES, null, null)
            db.delete(TABLE_AUTOMATION_LOGS, null, null)
            dao.clearSnapshots()
            dao.clearAdjustments()
            dao.clearTransfers()
            dao.clearRefunds()
            dao.clearStatements()
            dao.clearCcPayments()
            dao.clearBudgets()
            dao.clearBudgetAlerts()
            dao.clearImportRuns()
            dao.clearParserFailures()
            dao.clearSyncQueue()

            val resetState = ContentValues().apply {
                put("historical_scan_status", SCAN_PENDING)
                put("last_processed_sms_ts", 0L)
                put("scanned_count", 0)
                put("detected_count", 0)
                put("last_sync_at", 0L)
                put("updated_at", System.currentTimeMillis())
            }
            db.update(
                TABLE_SMS_SYNC_STATE,
                SQLiteDatabase.CONFLICT_REPLACE,
                resetState,
                "id = ?",
                arrayOf(SYNC_STATE_ROW_ID.toString())
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
    }

    private fun ensureSyncStateRow(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("id", SYNC_STATE_ROW_ID)
            put("historical_scan_status", SCAN_PENDING)
            put("last_processed_sms_ts", 0L)
            put("scanned_count", 0)
            put("detected_count", 0)
            put("monitoring_status", "INACTIVE")
            put("last_sync_at", 0L)
            put("updated_at", now)
        }
        db.insert(TABLE_SMS_SYNC_STATE, SQLiteDatabase.CONFLICT_IGNORE, values)
    }

    companion object {
        const val TABLE_LOCAL_TRANSACTIONS = "local_transactions"
        const val TABLE_LOCAL_ACCOUNTS = "local_accounts"
        const val TABLE_LOCAL_CATEGORIES = "local_categories"
        const val TABLE_LOCAL_RULES = "local_rules"
        const val TABLE_LOCAL_SMS_LOGS = "local_sms_logs"
        const val TABLE_SMS_SYNC_STATE = "sms_sync_state"
        const val TABLE_AUTOMATION_LOGS = "automation_logs"

        const val SYNC_STATE_ROW_ID = 1
        const val MAX_RETRIES = 10

        const val STATUS_PENDING = "PENDING"
        const val STATUS_SYNCED = "SYNCED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_RETRY_SCHEDULED = "RETRY_SCHEDULED"

        const val SCAN_PENDING = "NOT_STARTED"
        const val SCAN_IN_PROGRESS = "RUNNING"
        const val SCAN_COMPLETED = "COMPLETED"

        const val SOURCE_INCOMING_SMS = "INCOMING_SMS"
        const val SOURCE_HISTORICAL_SMS = "HISTORICAL_SMS"
        const val SOURCE_MANUAL = "MANUAL"

        @Volatile
        private var instance: LocalFinanceDbHelper? = null

        fun getInstance(context: Context): LocalFinanceDbHelper {
            return instance ?: synchronized(this) {
                instance ?: LocalFinanceDbHelper(context.applicationContext).also { instance = it }
            }
        }

        internal fun resetForTests() {
            synchronized(this) {
                instance = null
                MonziDatabase.resetForTests()
            }
        }
    }
}
