package com.krunal.monzi.data

data class UserSession(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSec: Long,
    val emailConfirmed: Boolean
)

data class UserProfile(
    val id: String,
    val fullName: String,
    val email: String,
    val currency: String = "INR",
    val smsPermission: String = "prompt",
    val biometricEnabled: Boolean = false,
    val deviceSyncToken: String = ""
)

data class AccountEntity(
    val id: String,
    val userId: String,
    val bankName: String,
    val accountNumberMask: String,
    val accountType: String = "SAVINGS", // SAVINGS, CURRENT, CREDIT_CARD, WALLET
    val accountName: String? = null,
    val initialBalance: Double = 0.0,
    val openingBalance: Double = 0.0,
    val currentBalance: Double = 0.0,
    val availableBalance: Double? = null,
    val balanceSource: String = "CALCULATED", // BANK_REPORTED, CALCULATED, ESTIMATED, UNAVAILABLE
    val reconciliationStatus: String = "CALCULATED_ONLY", // RECONCILED, CALCULATED_ONLY, BANK_SNAPSHOT_AVAILABLE, DISCREPANCY_DETECTED, NEEDS_ACCOUNT_MAPPING
    val creditLimit: Double? = null,
    val statementDay: Int? = null,
    val dueDay: Int? = null,
    val billingCycle: String? = null,
    val accountStatus: String = "ACTIVE",
    val lastSyncedAtIso: String? = null,
    val pendingSync: Boolean = false,
    val updatedAtMs: Long = System.currentTimeMillis()
)

data class CategoryEntity(
    val id: String,
    val name: String,
    val icon: String = "Tag",
    val color: String = "#2563EB",
    val isIncome: Boolean = false
)

data class TransactionEntity(
    val localId: String,
    val remoteId: String? = null,
    val userId: String = "",
    val accountId: String? = null,
    val categoryId: String? = null,
    val smsFingerprint: String,
    val amount: Double,
    val direction: String, // CREDIT, DEBIT
    val transactionType: String, // EXPENSE, INCOME, TRANSFER, REFUND, REVERSAL, CC_PURCHASE, CC_BILL_PAYMENT, INTEREST, BANK_CHARGES, ATM_WITHDRAWAL, CASH_DEPOSIT, INVESTMENT, OTHER, UNCLASSIFIED
    val bankName: String = "Bank",
    val accountMask: String = "XX0000",
    val merchant: String? = null,
    val referenceId: String? = null,
    val paymentMode: String = "UPI",
    val parsedAvailableBalance: Double? = null,
    val isSelfTransfer: Boolean = false,
    val isRefund: Boolean = false,
    val isExcludedFromTotals: Boolean = false,
    val linkedTransactionId: String? = null,
    val notes: String? = null,
    val currency: String = "INR",
    val transactionSource: String = "INCOMING_SMS", // INCOMING_SMS, HISTORICAL_SMS, MANUAL, REFUND_REVERSAL
    val transactionStatus: String = "COMPLETED", // COMPLETED, FAILED, PENDING, REVERSED
    val confidenceScore: Double = 1.0,
    val duplicateStatus: String = "UNIQUE", // UNIQUE, EXACT_DUPLICATE, POSSIBLE_DUPLICATE, CONFIRMED_SEPARATE
    val transferStatus: String? = null, // CANDIDATE, MATCHED, UNMATCHED, CONFIRMED, NOT_TRANSFER
    val refundStatus: String? = null, // REFUND_DETECTED, MATCHED_TO_ORIGINAL, UNMATCHED_REFUND, PARTIAL_REFUND, FULL_REFUND
    val transactionDateIso: String,
    val transactionDateMs: Long,
    val recordedAtMs: Long = System.currentTimeMillis(),
    val sender: String? = null,
    val syncStatus: String = "PENDING", // PENDING, PROCESSING, SYNCED, RETRY, FAILED, CONFLICT
    val retryCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val errorCode: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    // Resolved display fields
    val categoryName: String? = null,
    val categoryColor: String? = null,
    val accountDisplayName: String? = null
)

data class TransactionRuleEntity(
    val id: String,
    val userId: String,
    val matchingRule: String,
    val categoryId: String,
    val categoryName: String? = null,
    val categoryColor: String? = null,
    val priority: Int = 10,
    val enabled: Boolean = true,
    val createdAtIso: String = ""
)

data class SmsLogEntity(
    val id: String,
    val deviceId: String? = null,
    val sender: String? = null,
    val status: String, // PROCESSED, DUPLICATE, IGNORED, FAILED
    val reason: String? = null,
    val extractedType: String? = null,
    val extractedAmount: Double? = null,
    val extractedBank: String? = null,
    val extractedDateIso: String? = null,
    val referenceId: String? = null,
    val smsFingerprint: String? = null,
    val createdAtMs: Long = System.currentTimeMillis()
)

data class AutomationLogEntity(
    val id: Long = 0L,
    val processType: String,
    val processStatus: String,
    val errorCode: String? = null,
    val details: String? = null,
    val createdAtMs: Long = System.currentTimeMillis()
)

data class SmsSyncStateRecord(
    val id: Int = 1,
    val historicalScanStatus: String = "NOT_STARTED",
    val lastProcessedSmsTs: Long = 0L,
    val scannedCount: Int = 0,
    val detectedCount: Int = 0,
    val monitoringStatus: String = "INACTIVE",
    val lastSyncAt: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)

data class DeviceEntity(
    val id: String,
    val deviceIdentifier: String,
    val platform: String,
    val appVersion: String? = null,
    val lastSeenAtIso: String = ""
)

// ── MONZI 2.1 NEW DOMAIN MODELS ──────────────────────────────────────────────

data class AccountBalanceSnapshotEntity(
    val id: String,
    val userId: String,
    val accountId: String,
    val reportedBalance: Double,
    val calculatedBalanceAtSnapshot: Double,
    val discrepancyAmount: Double,
    val sourceTransactionId: String? = null,
    val snapshotAtMs: Long,
    val createdAtMs: Long = System.currentTimeMillis()
)

data class AccountBalanceAdjustmentEntity(
    val id: String,
    val userId: String,
    val accountId: String,
    val adjustmentAmount: Double,
    val reason: String,
    val approved: Boolean = true,
    val createdAtMs: Long = System.currentTimeMillis()
)

data class TransferRecordEntity(
    val id: String,
    val userId: String,
    val sourceAccountId: String?,
    val destinationAccountId: String?,
    val debitTransactionId: String?,
    val creditTransactionId: String?,
    val amount: Double,
    val transferType: String, // BANK_TO_BANK, BANK_TO_WALLET, WALLET_TO_BANK, BANK_TO_CARD, UPI_SELF, NEFT_IMPS_RTGS_SELF
    val status: String, // TRANSFER_CANDIDATE, MATCHED_TRANSFER, UNMATCHED_TRANSFER, CONFIRMED_TRANSFER, NOT_A_TRANSFER
    val referenceId: String? = null,
    val transferDateMs: Long,
    val createdAtMs: Long = System.currentTimeMillis()
)

data class RefundRecordEntity(
    val id: String,
    val userId: String,
    val refundTransactionId: String,
    val originalTransactionId: String?,
    val refundType: String, // FULL_REFUND, PARTIAL_REFUND, REVERSAL, CHARGEBACK, UNMATCHED_REFUND
    val refundAmount: Double,
    val status: String, // REFUND_DETECTED, MATCHED_TO_ORIGINAL, UNMATCHED_REFUND, PARTIAL_REFUND, FULL_REFUND
    val createdAtMs: Long = System.currentTimeMillis()
)

data class CreditCardStatementEntity(
    val id: String,
    val userId: String,
    val accountId: String,
    val statementPeriod: String,
    val statementDateMs: Long,
    val totalDue: Double,
    val minimumDue: Double,
    val dueDateMs: Long?,
    val paidAmount: Double = 0.0,
    val paymentStatus: String = "UPCOMING", // UPCOMING, DUE, PARTIALLY_PAID, PAID, OVERDUE, UNKNOWN
    val updatedAtMs: Long = System.currentTimeMillis()
)

data class CreditCardPaymentEntity(
    val id: String,
    val userId: String,
    val cardAccountId: String,
    val sourceAccountId: String?,
    val statementId: String?,
    val transactionId: String?,
    val amount: Double,
    val paymentDateMs: Long,
    val createdAtMs: Long = System.currentTimeMillis()
)

data class BudgetEntity(
    val id: String,
    val userId: String,
    val name: String,
    val categoryId: String?, // null = Overall Monthly Budget
    val categoryName: String? = null,
    val categoryColor: String? = null,
    val monthlyLimit: Double,
    val startMonth: String, // YYYY-MM
    val alertThreshold: Int = 80,
    val carryForward: Boolean = false,
    val status: String = "ACTIVE", // ACTIVE, PAUSED, ARCHIVED
    val createdAtMs: Long = System.currentTimeMillis()
)

data class BudgetAlertEntity(
    val id: String,
    val userId: String,
    val budgetId: String,
    val monthKey: String,
    val thresholdPct: Int,
    val usagePct: Double,
    val spentAmount: Double,
    val message: String,
    val acknowledged: Boolean = false,
    val triggeredAtMs: Long = System.currentTimeMillis()
)

data class SmsImportRunEntity(
    val id: String,
    val deviceId: String,
    val status: String, // NOT_STARTED, QUEUED, RUNNING, PAUSED, COMPLETED, PARTIAL, FAILED
    val totalExamined: Int = 0,
    val financialDetected: Int = 0,
    val importedCount: Int = 0,
    val duplicateSkipped: Int = 0,
    val ignoredCount: Int = 0,
    val reviewCount: Int = 0,
    val failureCount: Int = 0,
    val checkpointTs: Long = 0L,
    val startedAtMs: Long = System.currentTimeMillis(),
    val completedAtMs: Long? = null
)

data class SmsParserFailureEntity(
    val id: String,
    val deviceId: String,
    val sender: String?,
    val errorReason: String,
    val occurredAtMs: Long = System.currentTimeMillis()
)

data class SyncQueueItemEntity(
    val id: String,
    val entityType: String, // TRANSACTION, ACCOUNT, BUDGET, RULE
    val entityId: String,
    val operation: String, // UPSERT, DELETE
    val payloadJson: String,
    val syncStatus: String, // PENDING, PROCESSING, SYNCED, RETRY, FAILED, CONFLICT
    val retryCount: Int = 0,
    val lastError: String? = null,
    val lastAttemptAtMs: Long? = null,
    val createdAtMs: Long = System.currentTimeMillis()
)
