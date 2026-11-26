package com.krunal.monzi.data.local

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// ── ROOM ENTITIES (Preserving Monzi 2.0 table names + adding Monzi 2.1 tables) ──

@Entity(
    tableName = "local_transactions",
    indices = [
        Index(value = ["sms_fingerprint"], unique = true),
        Index(value = ["sync_status", "retry_count", "created_at"]),
        Index(value = ["deleted", "transaction_date_ms"])
    ]
)
data class RoomTransaction(
    @PrimaryKey @ColumnInfo(name = "local_id") val localId: String,
    @ColumnInfo(name = "remote_id") val remoteId: String? = null,
    @ColumnInfo(name = "user_id") val userId: String = "",
    @ColumnInfo(name = "account_id") val accountId: String? = null,
    @ColumnInfo(name = "category_id") val categoryId: String? = null,
    @ColumnInfo(name = "sms_fingerprint") val smsFingerprint: String?,
    val amount: Double,
    val direction: String,
    @ColumnInfo(name = "transaction_type") val transactionType: String,
    @ColumnInfo(name = "bank_name") val bankName: String,
    @ColumnInfo(name = "account_mask") val accountMask: String,
    val merchant: String? = null,
    @ColumnInfo(name = "reference_id") val referenceId: String? = null,
    @ColumnInfo(name = "payment_mode") val paymentMode: String,
    @ColumnInfo(name = "parsed_available_balance") val parsedAvailableBalance: Double? = null,
    @ColumnInfo(name = "is_self_transfer") val isSelfTransfer: Int = 0,
    @ColumnInfo(name = "is_refund") val isRefund: Int = 0,
    @ColumnInfo(name = "is_excluded_from_totals") val isExcludedFromTotals: Int = 0,
    @ColumnInfo(name = "linked_transaction_id") val linkedTransactionId: String? = null,
    val notes: String? = null,
    val currency: String = "INR",
    @ColumnInfo(name = "transaction_source") val transactionSource: String,
    @ColumnInfo(name = "transaction_status") val transactionStatus: String = "COMPLETED",
    @ColumnInfo(name = "confidence_score") val confidenceScore: Double = 1.0,
    @ColumnInfo(name = "duplicate_status") val duplicateStatus: String = "UNIQUE",
    @ColumnInfo(name = "transfer_status") val transferStatus: String? = null,
    @ColumnInfo(name = "refund_status") val refundStatus: String? = null,
    @ColumnInfo(name = "transaction_date_iso") val transactionDateIso: String,
    @ColumnInfo(name = "transaction_date_ms") val transactionDateMs: Long = 0L,
    @ColumnInfo(name = "recorded_at_ms") val recordedAtMs: Long = 0L,
    val sender: String? = null,
    @ColumnInfo(name = "sync_status") val syncStatus: String = "PENDING",
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
    @ColumnInfo(name = "last_attempt_at") val lastAttemptAt: Long? = null,
    @ColumnInfo(name = "error_code") val errorCode: String? = null,
    val deleted: Int = 0,
    @ColumnInfo(name = "category_name") val categoryName: String? = null,
    @ColumnInfo(name = "category_color") val categoryColor: String? = null,
    @ColumnInfo(name = "account_display_name") val accountDisplayName: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(tableName = "local_accounts")
data class RoomAccount(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String = "",
    @ColumnInfo(name = "bank_name") val bankName: String,
    @ColumnInfo(name = "account_number_mask") val accountNumberMask: String,
    @ColumnInfo(name = "account_name") val accountName: String? = null,
    @ColumnInfo(name = "account_type") val accountType: String = "SAVINGS",
    @ColumnInfo(name = "initial_balance") val initialBalance: Double = 0.0,
    @ColumnInfo(name = "opening_balance") val openingBalance: Double = 0.0,
    @ColumnInfo(name = "current_balance") val currentBalance: Double = 0.0,
    @ColumnInfo(name = "available_balance") val availableBalance: Double? = null,
    @ColumnInfo(name = "balance_source") val balanceSource: String = "CALCULATED",
    @ColumnInfo(name = "reconciliation_status") val reconciliationStatus: String = "CALCULATED_ONLY",
    @ColumnInfo(name = "credit_limit") val creditLimit: Double? = null,
    @ColumnInfo(name = "statement_day") val statementDay: Int? = null,
    @ColumnInfo(name = "due_day") val dueDay: Int? = null,
    @ColumnInfo(name = "billing_cycle") val billingCycle: String? = null,
    @ColumnInfo(name = "account_status") val accountStatus: String = "ACTIVE",
    @ColumnInfo(name = "last_synced_at_iso") val lastSyncedAtIso: String? = null,
    @ColumnInfo(name = "pending_sync") val pendingSync: Int = 0,
    val deleted: Int = 0,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long
)

@Entity(
    tableName = "local_categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class RoomCategory(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String = "Tag",
    val color: String = "#2563EB",
    @ColumnInfo(name = "is_income") val isIncome: Int = 0
)

@Entity(tableName = "local_rules")
data class RoomRule(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String = "",
    @ColumnInfo(name = "matching_rule") val matchingRule: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "category_name") val categoryName: String? = null,
    @ColumnInfo(name = "category_color") val categoryColor: String? = null,
    val priority: Int = 10,
    val enabled: Int = 1,
    @ColumnInfo(name = "created_at_iso") val createdAtIso: String = ""
)

@Entity(tableName = "local_sms_logs")
data class RoomSmsLog(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "device_id") val deviceId: String? = null,
    val sender: String? = null,
    val status: String,
    val reason: String? = null,
    @ColumnInfo(name = "extracted_type") val extractedType: String? = null,
    @ColumnInfo(name = "extracted_amount") val extractedAmount: Double? = null,
    @ColumnInfo(name = "extracted_bank") val extractedBank: String? = null,
    @ColumnInfo(name = "extracted_date_iso") val extractedDateIso: String? = null,
    @ColumnInfo(name = "reference_id") val referenceId: String? = null,
    @ColumnInfo(name = "sms_fingerprint") val smsFingerprint: String? = null,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long
)

@Entity(tableName = "sms_sync_state")
data class RoomSmsSyncState(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "historical_scan_status") val historicalScanStatus: String = "NOT_STARTED",
    @ColumnInfo(name = "last_processed_sms_ts") val lastProcessedSmsTs: Long = 0L,
    @ColumnInfo(name = "scanned_count") val scannedCount: Int = 0,
    @ColumnInfo(name = "detected_count") val detectedCount: Int = 0,
    @ColumnInfo(name = "monitoring_status") val monitoringStatus: String = "INACTIVE",
    @ColumnInfo(name = "last_sync_at") val lastSyncAt: Long = 0L,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0L
)

@Entity(tableName = "automation_logs")
data class RoomAutomationLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "process_type") val processType: String,
    @ColumnInfo(name = "process_status") val processStatus: String,
    @ColumnInfo(name = "error_code") val errorCode: String? = null,
    val details: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

// ── MONZI 2.1 ROOM ENTITIES ──

@Entity(tableName = "account_balance_snapshots")
data class RoomBalanceSnapshot(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String = "",
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "reported_balance") val reportedBalance: Double,
    @ColumnInfo(name = "calculated_balance_at_snapshot") val calculatedBalanceAtSnapshot: Double,
    @ColumnInfo(name = "discrepancy_amount") val discrepancyAmount: Double,
    @ColumnInfo(name = "source_transaction_id") val sourceTransactionId: String? = null,
    @ColumnInfo(name = "snapshot_at_ms") val snapshotAtMs: Long,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long
)

@Entity(tableName = "account_balance_adjustments")
data class RoomBalanceAdjustment(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String = "",
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "adjustment_amount") val adjustmentAmount: Double,
    val reason: String,
    val approved: Int = 1,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long
)

@Entity(tableName = "transfers")
data class RoomTransfer(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String = "",
    @ColumnInfo(name = "source_account_id") val sourceAccountId: String?,
    @ColumnInfo(name = "destination_account_id") val destinationAccountId: String?,
    @ColumnInfo(name = "debit_transaction_id") val debitTransactionId: String?,
    @ColumnInfo(name = "credit_transaction_id") val creditTransactionId: String?,
    val amount: Double,
    @ColumnInfo(name = "transfer_type") val transferType: String,
    val status: String,
    @ColumnInfo(name = "reference_id") val referenceId: String? = null,
    @ColumnInfo(name = "transfer_date_ms") val transferDateMs: Long,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long
)

@Entity(tableName = "refunds")
data class RoomRefund(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String = "",
    @ColumnInfo(name = "refund_transaction_id") val refundTransactionId: String,
    @ColumnInfo(name = "original_transaction_id") val originalTransactionId: String?,
    @ColumnInfo(name = "refund_type") val refundType: String,
    @ColumnInfo(name = "refund_amount") val refundAmount: Double,
    val status: String,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long
)

@Entity(tableName = "credit_card_statements")
data class RoomCreditCardStatement(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String = "",
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "statement_period") val statementPeriod: String,
    @ColumnInfo(name = "statement_date_ms") val statementDateMs: Long,
    @ColumnInfo(name = "total_due") val totalDue: Double,
    @ColumnInfo(name = "minimum_due") val minimumDue: Double,
    @ColumnInfo(name = "due_date_ms") val dueDateMs: Long?,
    @ColumnInfo(name = "paid_amount") val paidAmount: Double = 0.0,
    @ColumnInfo(name = "payment_status") val paymentStatus: String = "UPCOMING",
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long
)

@Entity(tableName = "credit_card_payments")
data class RoomCreditCardPayment(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String = "",
    @ColumnInfo(name = "card_account_id") val cardAccountId: String,
    @ColumnInfo(name = "source_account_id") val sourceAccountId: String?,
    @ColumnInfo(name = "statement_id") val statementId: String?,
    @ColumnInfo(name = "transaction_id") val transactionId: String?,
    val amount: Double,
    @ColumnInfo(name = "payment_date_ms") val paymentDateMs: Long,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long
)

@Entity(tableName = "budgets")
data class RoomBudget(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String = "",
    val name: String,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "category_name") val categoryName: String?,
    @ColumnInfo(name = "category_color") val categoryColor: String?,
    @ColumnInfo(name = "monthly_limit") val monthlyLimit: Double,
    @ColumnInfo(name = "start_month") val startMonth: String,
    @ColumnInfo(name = "alert_threshold") val alertThreshold: Int = 80,
    @ColumnInfo(name = "carry_forward") val carryForward: Int = 0,
    val status: String = "ACTIVE",
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long
)

@Entity(
    tableName = "budget_alerts",
    indices = [Index(value = ["budget_id", "month_key", "threshold_pct"], unique = true)]
)
data class RoomBudgetAlert(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String = "",
    @ColumnInfo(name = "budget_id") val budgetId: String,
    @ColumnInfo(name = "month_key") val monthKey: String,
    @ColumnInfo(name = "threshold_pct") val thresholdPct: Int,
    @ColumnInfo(name = "usage_pct") val usagePct: Double,
    @ColumnInfo(name = "spent_amount") val spentAmount: Double,
    val message: String,
    val acknowledged: Int = 0,
    @ColumnInfo(name = "triggered_at_ms") val triggeredAtMs: Long
)

@Entity(tableName = "sms_import_runs")
data class RoomSmsImportRun(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "device_id") val deviceId: String,
    val status: String,
    @ColumnInfo(name = "total_examined") val totalExamined: Int = 0,
    @ColumnInfo(name = "financial_detected") val financialDetected: Int = 0,
    @ColumnInfo(name = "imported_count") val importedCount: Int = 0,
    @ColumnInfo(name = "duplicate_skipped") val duplicateSkipped: Int = 0,
    @ColumnInfo(name = "ignored_count") val ignoredCount: Int = 0,
    @ColumnInfo(name = "review_count") val reviewCount: Int = 0,
    @ColumnInfo(name = "failure_count") val failureCount: Int = 0,
    @ColumnInfo(name = "checkpoint_ts") val checkpointTs: Long = 0L,
    @ColumnInfo(name = "started_at_ms") val startedAtMs: Long,
    @ColumnInfo(name = "completed_at_ms") val completedAtMs: Long? = null
)

@Entity(tableName = "sms_parser_failures")
data class RoomSmsParserFailure(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "device_id") val deviceId: String,
    val sender: String? = null,
    @ColumnInfo(name = "error_reason") val errorReason: String,
    @ColumnInfo(name = "occurred_at_ms") val occurredAtMs: Long
)

@Entity(tableName = "sync_queue")
data class RoomSyncQueueItem(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    val operation: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "sync_status") val syncStatus: String,
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    @ColumnInfo(name = "last_attempt_at_ms") val lastAttemptAtMs: Long? = null,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long
)

@Dao
interface MonziDao {
    // Snapshots
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSnapshot(item: RoomBalanceSnapshot)

    @Query("SELECT * FROM account_balance_snapshots WHERE account_id = :accountId ORDER BY snapshot_at_ms DESC")
    fun getSnapshotsForAccount(accountId: String): List<RoomBalanceSnapshot>

    @Query("SELECT * FROM account_balance_snapshots ORDER BY snapshot_at_ms DESC")
    fun getAllSnapshots(): List<RoomBalanceSnapshot>

    // Adjustments
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAdjustment(item: RoomBalanceAdjustment)

    @Query("SELECT * FROM account_balance_adjustments WHERE account_id = :accountId ORDER BY created_at_ms DESC")
    fun getAdjustmentsForAccount(accountId: String): List<RoomBalanceAdjustment>

    @Query("SELECT * FROM account_balance_adjustments ORDER BY created_at_ms DESC")
    fun getAllAdjustments(): List<RoomBalanceAdjustment>

    // Transfers
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTransfer(item: RoomTransfer)

    @Query("SELECT * FROM transfers ORDER BY transfer_date_ms DESC")
    fun getAllTransfers(): List<RoomTransfer>

    @Query("UPDATE transfers SET status = :status WHERE id = :id")
    fun updateTransferStatus(id: String, status: String)

    // Refunds
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRefund(item: RoomRefund)

    @Query("SELECT * FROM refunds ORDER BY created_at_ms DESC")
    fun getAllRefunds(): List<RoomRefund>

    // Credit Card Statements & Payments
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertStatement(item: RoomCreditCardStatement)

    @Query("SELECT * FROM credit_card_statements ORDER BY statement_date_ms DESC")
    fun getAllStatements(): List<RoomCreditCardStatement>

    @Query("SELECT * FROM credit_card_statements WHERE account_id = :accountId ORDER BY statement_date_ms DESC")
    fun getStatementsForCard(accountId: String): List<RoomCreditCardStatement>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCcPayment(item: RoomCreditCardPayment)

    @Query("SELECT * FROM credit_card_payments ORDER BY payment_date_ms DESC")
    fun getAllCcPayments(): List<RoomCreditCardPayment>

    // Budgets & Alerts
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertBudget(item: RoomBudget)

    @Query("SELECT * FROM budgets ORDER BY created_at_ms DESC")
    fun getAllBudgets(): List<RoomBudget>

    @Query("DELETE FROM budgets WHERE id = :id")
    fun deleteBudget(id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertBudgetAlert(item: RoomBudgetAlert): Long

    @Query("SELECT * FROM budget_alerts ORDER BY triggered_at_ms DESC")
    fun getAllBudgetAlerts(): List<RoomBudgetAlert>

    @Query("UPDATE budget_alerts SET acknowledged = 1 WHERE id = :id")
    fun acknowledgeBudgetAlert(id: String)

    // SMS Import Runs & Parser Failures
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertImportRun(item: RoomSmsImportRun)

    @Query("SELECT * FROM sms_import_runs ORDER BY started_at_ms DESC LIMIT 20")
    fun getRecentImportRuns(): List<RoomSmsImportRun>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertParserFailure(item: RoomSmsParserFailure)

    @Query("SELECT * FROM sms_parser_failures ORDER BY occurred_at_ms DESC LIMIT 50")
    fun getParserFailures(): List<RoomSmsParserFailure>

    // Persistent Sync Queue
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSyncQueueItem(item: RoomSyncQueueItem)

    @Query("SELECT * FROM sync_queue ORDER BY created_at_ms DESC LIMIT 100")
    fun getAllSyncQueueItems(): List<RoomSyncQueueItem>

    @Query("SELECT * FROM sync_queue WHERE sync_status IN ('PENDING', 'RETRY', 'FAILED') ORDER BY created_at_ms ASC LIMIT 50")
    fun getActionableSyncQueueItems(): List<RoomSyncQueueItem>

    @Query("DELETE FROM account_balance_snapshots")
    fun clearSnapshots()

    @Query("DELETE FROM account_balance_adjustments")
    fun clearAdjustments()

    @Query("DELETE FROM transfers")
    fun clearTransfers()

    @Query("DELETE FROM refunds")
    fun clearRefunds()

    @Query("DELETE FROM credit_card_statements")
    fun clearStatements()

    @Query("DELETE FROM credit_card_payments")
    fun clearCcPayments()

    @Query("DELETE FROM budgets")
    fun clearBudgets()

    @Query("DELETE FROM budget_alerts")
    fun clearBudgetAlerts()

    @Query("DELETE FROM sms_import_runs")
    fun clearImportRuns()

    @Query("DELETE FROM sms_parser_failures")
    fun clearParserFailures()

    @Query("DELETE FROM sync_queue")
    fun clearSyncQueue()

    @Query("DELETE FROM sync_queue WHERE id = :id")
    fun deleteSyncQueueItem(id: String)

    @Query("DELETE FROM account_balance_snapshots WHERE account_id = :accountId")
    fun deleteSnapshotsForAccount(accountId: String)

    @Query("DELETE FROM account_balance_adjustments WHERE account_id = :accountId")
    fun deleteAdjustmentsForAccount(accountId: String)

    @Query("DELETE FROM credit_card_statements WHERE account_id = :accountId")
    fun deleteStatementsForAccount(accountId: String)

    @Query("DELETE FROM credit_card_payments WHERE card_account_id = :accountId")
    fun deleteCcPaymentsForCard(accountId: String)
}

@Database(
    entities = [
        RoomTransaction::class,
        RoomAccount::class,
        RoomCategory::class,
        RoomRule::class,
        RoomSmsLog::class,
        RoomSmsSyncState::class,
        RoomAutomationLog::class,
        RoomBalanceSnapshot::class,
        RoomBalanceAdjustment::class,
        RoomTransfer::class,
        RoomRefund::class,
        RoomCreditCardStatement::class,
        RoomCreditCardPayment::class,
        RoomBudget::class,
        RoomBudgetAlert::class,
        RoomSmsImportRun::class,
        RoomSmsParserFailure::class,
        RoomSyncQueueItem::class
    ],
    version = 3,
    exportSchema = false
)
abstract class MonziDatabase : RoomDatabase() {
    abstract fun dao(): MonziDao

    companion object {
        @Volatile
        private var instance: MonziDatabase? = null

        private fun createMonzi21Tables(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS account_balance_snapshots (
                    id TEXT NOT NULL PRIMARY KEY,
                    user_id TEXT NOT NULL DEFAULT '',
                    account_id TEXT NOT NULL,
                    reported_balance REAL NOT NULL,
                    calculated_balance_at_snapshot REAL NOT NULL,
                    discrepancy_amount REAL NOT NULL,
                    source_transaction_id TEXT,
                    snapshot_at_ms INTEGER NOT NULL,
                    created_at_ms INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS account_balance_adjustments (
                    id TEXT NOT NULL PRIMARY KEY,
                    user_id TEXT NOT NULL DEFAULT '',
                    account_id TEXT NOT NULL,
                    adjustment_amount REAL NOT NULL,
                    reason TEXT NOT NULL,
                    approved INTEGER NOT NULL DEFAULT 1,
                    created_at_ms INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS transfers (
                    id TEXT NOT NULL PRIMARY KEY,
                    user_id TEXT NOT NULL DEFAULT '',
                    source_account_id TEXT,
                    destination_account_id TEXT,
                    debit_transaction_id TEXT,
                    credit_transaction_id TEXT,
                    amount REAL NOT NULL,
                    transfer_type TEXT NOT NULL,
                    status TEXT NOT NULL,
                    reference_id TEXT,
                    transfer_date_ms INTEGER NOT NULL,
                    created_at_ms INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS refunds (
                    id TEXT NOT NULL PRIMARY KEY,
                    user_id TEXT NOT NULL DEFAULT '',
                    refund_transaction_id TEXT NOT NULL,
                    original_transaction_id TEXT,
                    refund_type TEXT NOT NULL,
                    refund_amount REAL NOT NULL,
                    status TEXT NOT NULL,
                    created_at_ms INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS credit_card_statements (
                    id TEXT NOT NULL PRIMARY KEY,
                    user_id TEXT NOT NULL DEFAULT '',
                    account_id TEXT NOT NULL,
                    statement_period TEXT NOT NULL,
                    statement_date_ms INTEGER NOT NULL,
                    total_due REAL NOT NULL,
                    minimum_due REAL NOT NULL,
                    due_date_ms INTEGER,
                    paid_amount REAL NOT NULL DEFAULT 0,
                    payment_status TEXT NOT NULL DEFAULT 'UPCOMING',
                    updated_at_ms INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS credit_card_payments (
                    id TEXT NOT NULL PRIMARY KEY,
                    user_id TEXT NOT NULL DEFAULT '',
                    card_account_id TEXT NOT NULL,
                    source_account_id TEXT,
                    statement_id TEXT,
                    transaction_id TEXT,
                    amount REAL NOT NULL,
                    payment_date_ms INTEGER NOT NULL,
                    created_at_ms INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS budgets (
                    id TEXT NOT NULL PRIMARY KEY,
                    user_id TEXT NOT NULL DEFAULT '',
                    name TEXT NOT NULL,
                    category_id TEXT,
                    category_name TEXT,
                    category_color TEXT,
                    monthly_limit REAL NOT NULL,
                    start_month TEXT NOT NULL,
                    alert_threshold INTEGER NOT NULL DEFAULT 80,
                    carry_forward INTEGER NOT NULL DEFAULT 0,
                    status TEXT NOT NULL DEFAULT 'ACTIVE',
                    created_at_ms INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS budget_alerts (
                    id TEXT NOT NULL PRIMARY KEY,
                    user_id TEXT NOT NULL DEFAULT '',
                    budget_id TEXT NOT NULL,
                    month_key TEXT NOT NULL,
                    threshold_pct INTEGER NOT NULL,
                    usage_pct REAL NOT NULL,
                    spent_amount REAL NOT NULL,
                    message TEXT NOT NULL,
                    acknowledged INTEGER NOT NULL DEFAULT 0,
                    triggered_at_ms INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_budget_alerts_budget_id_month_key_threshold_pct ON budget_alerts (budget_id, month_key, threshold_pct)"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sms_import_runs (
                    id TEXT NOT NULL PRIMARY KEY,
                    device_id TEXT NOT NULL,
                    status TEXT NOT NULL,
                    total_examined INTEGER NOT NULL DEFAULT 0,
                    financial_detected INTEGER NOT NULL DEFAULT 0,
                    imported_count INTEGER NOT NULL DEFAULT 0,
                    duplicate_skipped INTEGER NOT NULL DEFAULT 0,
                    ignored_count INTEGER NOT NULL DEFAULT 0,
                    review_count INTEGER NOT NULL DEFAULT 0,
                    failure_count INTEGER NOT NULL DEFAULT 0,
                    checkpoint_ts INTEGER NOT NULL DEFAULT 0,
                    started_at_ms INTEGER NOT NULL,
                    completed_at_ms INTEGER
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sms_parser_failures (
                    id TEXT NOT NULL PRIMARY KEY,
                    device_id TEXT NOT NULL,
                    sender TEXT,
                    error_reason TEXT NOT NULL,
                    occurred_at_ms INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sync_queue (
                    id TEXT NOT NULL PRIMARY KEY,
                    entity_type TEXT NOT NULL,
                    entity_id TEXT NOT NULL,
                    operation TEXT NOT NULL,
                    payload_json TEXT NOT NULL,
                    sync_status TEXT NOT NULL,
                    retry_count INTEGER NOT NULL DEFAULT 0,
                    last_error TEXT,
                    last_attempt_at_ms INTEGER,
                    created_at_ms INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }

        private fun safeAlter(db: SupportSQLiteDatabase, sql: String) {
            try {
                db.execSQL(sql)
            } catch (_: Exception) {
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                safeAlter(db, "ALTER TABLE local_transactions ADD COLUMN linked_transaction_id TEXT")
                safeAlter(db, "ALTER TABLE local_transactions ADD COLUMN confidence_score REAL NOT NULL DEFAULT 1.0")
                safeAlter(db, "ALTER TABLE local_transactions ADD COLUMN duplicate_status TEXT NOT NULL DEFAULT 'UNIQUE'")
                safeAlter(db, "ALTER TABLE local_transactions ADD COLUMN transfer_status TEXT")
                safeAlter(db, "ALTER TABLE local_transactions ADD COLUMN refund_status TEXT")
                safeAlter(db, "ALTER TABLE local_transactions ADD COLUMN recorded_at_ms INTEGER NOT NULL DEFAULT 0")

                // Rebuild v2 tables so NOT NULL primary keys and Room-named indices match Room TableInfo cleanly
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_transactions_room (
                        local_id TEXT NOT NULL PRIMARY KEY,
                        remote_id TEXT,
                        user_id TEXT NOT NULL,
                        account_id TEXT,
                        category_id TEXT,
                        sms_fingerprint TEXT,
                        amount REAL NOT NULL,
                        direction TEXT NOT NULL,
                        transaction_type TEXT NOT NULL,
                        bank_name TEXT NOT NULL,
                        account_mask TEXT NOT NULL,
                        merchant TEXT,
                        reference_id TEXT,
                        payment_mode TEXT NOT NULL,
                        parsed_available_balance REAL,
                        is_self_transfer INTEGER NOT NULL,
                        is_refund INTEGER NOT NULL,
                        is_excluded_from_totals INTEGER NOT NULL,
                        linked_transaction_id TEXT,
                        notes TEXT,
                        currency TEXT NOT NULL,
                        transaction_source TEXT NOT NULL,
                        transaction_status TEXT NOT NULL,
                        confidence_score REAL NOT NULL,
                        duplicate_status TEXT NOT NULL,
                        transfer_status TEXT,
                        refund_status TEXT,
                        transaction_date_iso TEXT NOT NULL,
                        transaction_date_ms INTEGER NOT NULL,
                        recorded_at_ms INTEGER NOT NULL,
                        sender TEXT,
                        sync_status TEXT NOT NULL,
                        retry_count INTEGER NOT NULL,
                        last_attempt_at INTEGER,
                        error_code TEXT,
                        deleted INTEGER NOT NULL,
                        category_name TEXT,
                        category_color TEXT,
                        account_display_name TEXT,
                        created_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO local_transactions_room (
                        local_id, remote_id, user_id, account_id, category_id, sms_fingerprint,
                        amount, direction, transaction_type, bank_name, account_mask, merchant,
                        reference_id, payment_mode, parsed_available_balance, is_self_transfer,
                        is_refund, is_excluded_from_totals, linked_transaction_id, notes, currency,
                        transaction_source, transaction_status, confidence_score, duplicate_status,
                        transfer_status, refund_status, transaction_date_iso, transaction_date_ms,
                        recorded_at_ms, sender, sync_status, retry_count, last_attempt_at,
                        error_code, deleted, category_name, category_color, account_display_name, created_at
                    )
                    SELECT
                        local_id, remote_id, COALESCE(user_id, ''), account_id, category_id, sms_fingerprint,
                        amount, direction, transaction_type, bank_name, account_mask, merchant,
                        reference_id, payment_mode, parsed_available_balance, COALESCE(is_self_transfer, 0),
                        COALESCE(is_refund, 0), COALESCE(is_excluded_from_totals, 0), linked_transaction_id,
                        notes, COALESCE(currency, 'INR'), transaction_source, COALESCE(transaction_status, 'COMPLETED'),
                        COALESCE(confidence_score, 1.0), COALESCE(duplicate_status, 'UNIQUE'),
                        transfer_status, refund_status, transaction_date_iso, COALESCE(transaction_date_ms, 0),
                        CASE WHEN recorded_at_ms > 0 THEN recorded_at_ms ELSE created_at END,
                        sender, COALESCE(sync_status, 'PENDING'), COALESCE(retry_count, 0), last_attempt_at,
                        error_code, COALESCE(deleted, 0), category_name, category_color, account_display_name, created_at
                    FROM local_transactions
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE local_transactions")
                db.execSQL("ALTER TABLE local_transactions_room RENAME TO local_transactions")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_local_transactions_sms_fingerprint ON local_transactions (sms_fingerprint)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_local_transactions_sync_status_retry_count_created_at ON local_transactions (sync_status, retry_count, created_at)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_local_transactions_deleted_transaction_date_ms ON local_transactions (deleted, transaction_date_ms)")

                safeAlter(db, "ALTER TABLE local_accounts ADD COLUMN account_name TEXT")
                safeAlter(db, "ALTER TABLE local_accounts ADD COLUMN available_balance REAL")
                safeAlter(db, "ALTER TABLE local_accounts ADD COLUMN reconciliation_status TEXT NOT NULL DEFAULT 'CALCULATED_ONLY'")
                safeAlter(db, "ALTER TABLE local_accounts ADD COLUMN credit_limit REAL")
                safeAlter(db, "ALTER TABLE local_accounts ADD COLUMN statement_day INTEGER")
                safeAlter(db, "ALTER TABLE local_accounts ADD COLUMN due_day INTEGER")
                safeAlter(db, "ALTER TABLE local_accounts ADD COLUMN billing_cycle TEXT")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_accounts_room (
                        id TEXT NOT NULL PRIMARY KEY,
                        user_id TEXT NOT NULL,
                        bank_name TEXT NOT NULL,
                        account_number_mask TEXT NOT NULL,
                        account_name TEXT,
                        account_type TEXT NOT NULL,
                        initial_balance REAL NOT NULL,
                        opening_balance REAL NOT NULL,
                        current_balance REAL NOT NULL,
                        available_balance REAL,
                        balance_source TEXT NOT NULL,
                        reconciliation_status TEXT NOT NULL,
                        credit_limit REAL,
                        statement_day INTEGER,
                        due_day INTEGER,
                        billing_cycle TEXT,
                        account_status TEXT NOT NULL,
                        last_synced_at_iso TEXT,
                        pending_sync INTEGER NOT NULL,
                        deleted INTEGER NOT NULL,
                        updated_at_ms INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO local_accounts_room
                    SELECT
                        id, COALESCE(user_id, ''), bank_name, account_number_mask, account_name,
                        COALESCE(account_type, 'SAVINGS'), COALESCE(initial_balance, 0.0),
                        COALESCE(opening_balance, 0.0), COALESCE(current_balance, 0.0), available_balance,
                        COALESCE(balance_source, 'CALCULATED'), COALESCE(reconciliation_status, 'CALCULATED_ONLY'),
                        credit_limit, statement_day, due_day, billing_cycle,
                        COALESCE(account_status, 'ACTIVE'), last_synced_at_iso, COALESCE(pending_sync, 0),
                        COALESCE(deleted, 0), updated_at_ms
                    FROM local_accounts
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE local_accounts")
                db.execSQL("ALTER TABLE local_accounts_room RENAME TO local_accounts")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_categories_room (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        icon TEXT NOT NULL,
                        color TEXT NOT NULL,
                        is_income INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("INSERT OR IGNORE INTO local_categories_room SELECT id, name, icon, color, is_income FROM local_categories")
                db.execSQL("DROP TABLE local_categories")
                db.execSQL("ALTER TABLE local_categories_room RENAME TO local_categories")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_local_categories_name ON local_categories (name)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_rules_room (
                        id TEXT NOT NULL PRIMARY KEY,
                        user_id TEXT NOT NULL,
                        matching_rule TEXT NOT NULL,
                        category_id TEXT NOT NULL,
                        category_name TEXT,
                        category_color TEXT,
                        priority INTEGER NOT NULL,
                        enabled INTEGER NOT NULL,
                        created_at_iso TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("INSERT OR IGNORE INTO local_rules_room SELECT id, user_id, matching_rule, category_id, category_name, category_color, priority, enabled, created_at_iso FROM local_rules")
                db.execSQL("DROP TABLE local_rules")
                db.execSQL("ALTER TABLE local_rules_room RENAME TO local_rules")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_sms_logs_room (
                        id TEXT NOT NULL PRIMARY KEY,
                        device_id TEXT,
                        sender TEXT,
                        status TEXT NOT NULL,
                        reason TEXT,
                        extracted_type TEXT,
                        extracted_amount REAL,
                        extracted_bank TEXT,
                        extracted_date_iso TEXT,
                        reference_id TEXT,
                        sms_fingerprint TEXT,
                        created_at_ms INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("INSERT OR IGNORE INTO local_sms_logs_room SELECT id, device_id, sender, status, reason, extracted_type, extracted_amount, extracted_bank, extracted_date_iso, reference_id, sms_fingerprint, created_at_ms FROM local_sms_logs")
                db.execSQL("DROP TABLE local_sms_logs")
                db.execSQL("ALTER TABLE local_sms_logs_room RENAME TO local_sms_logs")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sms_sync_state_room (
                        id INTEGER NOT NULL PRIMARY KEY,
                        historical_scan_status TEXT NOT NULL,
                        last_processed_sms_ts INTEGER NOT NULL,
                        scanned_count INTEGER NOT NULL,
                        detected_count INTEGER NOT NULL,
                        monitoring_status TEXT NOT NULL,
                        last_sync_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("INSERT OR IGNORE INTO sms_sync_state_room SELECT id, historical_scan_status, last_processed_sms_ts, scanned_count, detected_count, monitoring_status, last_sync_at, updated_at FROM sms_sync_state")
                db.execSQL("DROP TABLE sms_sync_state")
                db.execSQL("ALTER TABLE sms_sync_state_room RENAME TO sms_sync_state")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS automation_logs_room (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        process_type TEXT NOT NULL,
                        process_status TEXT NOT NULL,
                        error_code TEXT,
                        details TEXT,
                        created_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("INSERT OR IGNORE INTO automation_logs_room (id, process_type, process_status, error_code, details, created_at) SELECT id, process_type, process_status, error_code, details, created_at FROM automation_logs")
                db.execSQL("DROP TABLE automation_logs")
                db.execSQL("ALTER TABLE automation_logs_room RENAME TO automation_logs")

                createMonzi21Tables(db)
                db.execSQL("INSERT OR IGNORE INTO local_categories (id, name, icon, color, is_income) VALUES ('local-cat-uncategorized', 'Uncategorized', 'HelpCircle', '#64748B', 0)")
            }
        }

        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                safeAlter(db, "ALTER TABLE local_transactions ADD COLUMN remote_id TEXT")
                safeAlter(db, "ALTER TABLE local_transactions ADD COLUMN user_id TEXT NOT NULL DEFAULT ''")
                safeAlter(db, "ALTER TABLE local_transactions ADD COLUMN account_id TEXT")
                safeAlter(db, "ALTER TABLE local_transactions ADD COLUMN category_id TEXT")
                safeAlter(db, "ALTER TABLE local_transactions ADD COLUMN is_excluded_from_totals INTEGER NOT NULL DEFAULT 0")
                safeAlter(db, "ALTER TABLE local_transactions ADD COLUMN notes TEXT")
                safeAlter(db, "ALTER TABLE local_transactions ADD COLUMN currency TEXT NOT NULL DEFAULT 'INR'")
                safeAlter(db, "ALTER TABLE local_transactions ADD COLUMN transaction_status TEXT NOT NULL DEFAULT 'COMPLETED'")
                safeAlter(db, "ALTER TABLE local_transactions ADD COLUMN transaction_date_ms INTEGER NOT NULL DEFAULT 0")
                safeAlter(db, "ALTER TABLE local_transactions ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                safeAlter(db, "ALTER TABLE local_transactions ADD COLUMN category_name TEXT")
                safeAlter(db, "ALTER TABLE local_transactions ADD COLUMN category_color TEXT")
                safeAlter(db, "ALTER TABLE local_transactions ADD COLUMN account_display_name TEXT")
                MIGRATION_2_3.migrate(db)
            }
        }

        fun getInstance(context: Context): MonziDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MonziDatabase::class.java,
                    "monzi_local_finance.db"
                )
                    .addMigrations(MIGRATION_1_3, MIGRATION_2_3)
                    .allowMainThreadQueries()
                    .build()
                    .also { instance = it }
            }
        }

        internal fun resetForTests() {
            synchronized(this) {
                try {
                    instance?.close()
                } catch (_: Exception) {
                }
                instance = null
            }
        }
    }
}
