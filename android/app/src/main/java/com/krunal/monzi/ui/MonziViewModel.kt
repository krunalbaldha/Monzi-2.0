package com.krunal.monzi.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.krunal.monzi.InboxScanResult
import com.krunal.monzi.SmsInboxScanner
import com.krunal.monzi.data.AccountBalanceAdjustmentEntity
import com.krunal.monzi.data.AccountBalanceSnapshotEntity
import com.krunal.monzi.data.AccountEntity
import com.krunal.monzi.data.AnalyticsPeriod
import com.krunal.monzi.data.ApiResult
import com.krunal.monzi.data.AutomationLogEntity
import com.krunal.monzi.data.BudgetAlertEntity
import com.krunal.monzi.data.BudgetEntity
import com.krunal.monzi.data.CategoryEntity
import com.krunal.monzi.data.CreditCardPaymentEntity
import com.krunal.monzi.data.CreditCardStatementEntity
import com.krunal.monzi.data.DashboardStats
import com.krunal.monzi.data.DeviceEntity
import com.krunal.monzi.data.FinanceAnalyticsEngine
import com.krunal.monzi.data.MonziRepository
import com.krunal.monzi.data.PeriodAnalytics
import com.krunal.monzi.data.RefundRecordEntity
import com.krunal.monzi.data.SmsImportRunEntity
import com.krunal.monzi.data.SmsLogEntity
import com.krunal.monzi.data.SmsParserFailureEntity
import com.krunal.monzi.data.SmsSyncStateRecord
import com.krunal.monzi.data.SyncQueueItemEntity
import com.krunal.monzi.data.TransactionEntity
import com.krunal.monzi.data.TransactionRuleEntity
import com.krunal.monzi.data.TransferRecordEntity
import com.krunal.monzi.data.UserProfile
import com.krunal.monzi.data.engines.BudgetAndCardEngine
import com.krunal.monzi.data.engines.BudgetEvaluationResult
import com.krunal.monzi.data.engines.CreditCardSummary
import com.krunal.monzi.data.engines.ReconciledAccountSummary
import com.krunal.monzi.data.engines.ReconciliationEngine
import com.krunal.monzi.util.IstTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AuthGateState {
    LOADING,
    UNAUTHENTICATED,
    LOCKED,
    AUTHENTICATED
}

data class MonziUiState(
    val authGate: AuthGateState = AuthGateState.LOADING,
    val profile: UserProfile? = null,
    val emailVerified: Boolean = false,
    val isOnline: Boolean = true,
    val isSyncing: Boolean = false,
    val isScanningInbox: Boolean = false,
    val hasSmsPermissions: Boolean = false,
    val pendingSyncCount: Int = 0,
    val syncState: SmsSyncStateRecord = SmsSyncStateRecord(),
    val accounts: List<AccountEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val rules: List<TransactionRuleEntity> = emptyList(),
    val smsLogs: List<SmsLogEntity> = emptyList(),
    val automationLogs: List<AutomationLogEntity> = emptyList(),
    val devices: List<DeviceEntity> = emptyList(),
    val dashboardStats: DashboardStats = FinanceAnalyticsEngine.computeDashboardStats(emptyList(), emptyList()),
    val selectedPeriod: AnalyticsPeriod = AnalyticsPeriod.THIS_MONTH,
    val customStartMs: Long? = null,
    val customEndMs: Long? = null,
    val periodAnalytics: PeriodAnalytics = FinanceAnalyticsEngine.computePeriodAnalytics(
        AnalyticsPeriod.THIS_MONTH,
        emptyList(),
        emptyList()
    ),
    // Monzi 2.1 reactive state
    val reconciledAccounts: List<ReconciledAccountSummary> = emptyList(),
    val snapshots: List<AccountBalanceSnapshotEntity> = emptyList(),
    val adjustments: List<AccountBalanceAdjustmentEntity> = emptyList(),
    val transfers: List<TransferRecordEntity> = emptyList(),
    val refunds: List<RefundRecordEntity> = emptyList(),
    val creditCardSummaries: List<CreditCardSummary> = emptyList(),
    val statements: List<CreditCardStatementEntity> = emptyList(),
    val ccPayments: List<CreditCardPaymentEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val budgetAlerts: List<BudgetAlertEntity> = emptyList(),
    val budgetEvaluation: BudgetEvaluationResult = BudgetEvaluationResult(
        summaries = emptyList(),
        overallLimit = 0.0,
        overallNetSpent = 0.0,
        overallUtilizationPct = 0.0,
        triggeredAlerts = emptyList(),
        smartInsights = emptyList()
    ),
    val selectedBudgetMonth: String = IstTime.currentMonthKey(),
    val importRuns: List<SmsImportRunEntity> = emptyList(),
    val parserFailures: List<SmsParserFailureEntity> = emptyList(),
    val syncQueueItems: List<SyncQueueItemEntity> = emptyList(),
    val budgetAlertsEnabled: Boolean = true,
    val themeMode: String = "SYSTEM",
    val lastScanSummary: com.krunal.monzi.InboxScanResult? = null,
    val lastReprocessSummary: com.krunal.monzi.ReprocessResult? = null
)

class MonziViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = MonziRepository(application)

    private val _uiState = MutableStateFlow(MonziUiState())
    val uiState: StateFlow<MonziUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        observeLocalDatabase()
        bootstrapSession()
    }

    private fun emitMessage(msg: String) {
        _messages.tryEmit(msg)
    }

    private fun observeLocalDatabase() {
        viewModelScope.launch {
            repo.dbChanges.collect {
                reloadFromLocalDb()
            }
        }
    }

    fun reloadFromLocalDb() {
        viewModelScope.launch(Dispatchers.IO) {
            val accounts = repo.db.getAccounts()
            val transactions = repo.db.getAllTransactions(2000)
            val categories = repo.db.getCategories()
            val rules = repo.db.getRules()
            val smsLogs = repo.db.getSmsLogs(100)
            val automationLogs = repo.db.getAutomationLogs(80)
            val syncState = repo.db.getSyncState()
            val pendingCount = repo.db.getPendingCount()
            val hasSms = SmsInboxScanner.hasSmsPermissions(repo.appContext)
            val online = repo.isOnline()
            val curState = _uiState.value
            val snapshots = repo.db.getAllSnapshots()
            val adjustments = repo.db.getAllAdjustments()
            val transfers = repo.db.getAllTransfers()
            val refunds = repo.db.getAllRefunds()
            val statements = repo.db.getAllStatements()
            val ccPayments = repo.db.getAllCcPayments()
            val budgets = repo.db.getAllBudgets()
            val budgetAlerts = repo.db.getAllBudgetAlerts()
            val importRuns = repo.db.getRecentImportRuns()
            val parserFailures = repo.db.getParserFailures()
            val syncQueueItems = repo.db.getAllSyncQueueItems()

            val reconciled = accounts.map { acc ->
                ReconciliationEngine.reconcileAccountLedger(acc, transactions, adjustments, snapshots)
            }
            val ccSummaries = BudgetAndCardEngine.buildCreditCardSummaries(
                accounts = accounts,
                transactions = transactions,
                statements = statements,
                payments = ccPayments,
                adjustments = adjustments,
                snapshots = snapshots
            )
            val budgetEval = BudgetAndCardEngine.evaluateBudgets(
                budgets = budgets,
                transactions = transactions,
                refunds = refunds,
                existingAlerts = budgetAlerts,
                monthKey = curState.selectedBudgetMonth,
                userId = repo.sessionMgr.getUserId()
            )
            val dash = FinanceAnalyticsEngine.computeDashboardStats(accounts, transactions)
            val periodStats = FinanceAnalyticsEngine.computePeriodAnalytics(
                period = curState.selectedPeriod,
                accounts = accounts,
                transactions = transactions,
                customStartMs = curState.customStartMs,
                customEndMs = curState.customEndMs
            )

            _uiState.value = curState.copy(
                profile = repo.sessionMgr.getCachedProfile() ?: curState.profile,
                emailVerified = repo.sessionMgr.getSession()?.emailConfirmed ?: curState.emailVerified,
                isOnline = online,
                hasSmsPermissions = hasSms,
                pendingSyncCount = pendingCount,
                syncState = syncState,
                accounts = accounts,
                transactions = transactions,
                categories = categories,
                rules = rules,
                smsLogs = smsLogs,
                automationLogs = automationLogs,
                dashboardStats = dash,
                periodAnalytics = periodStats,
                reconciledAccounts = reconciled,
                snapshots = snapshots,
                adjustments = adjustments,
                transfers = transfers,
                refunds = refunds,
                creditCardSummaries = ccSummaries,
                statements = statements,
                ccPayments = ccPayments,
                budgets = budgets,
                budgetAlerts = budgetAlerts,
                budgetEvaluation = budgetEval,
                importRuns = importRuns,
                parserFailures = parserFailures,
                syncQueueItems = syncQueueItems,
                budgetAlertsEnabled = repo.isBudgetAlertsEnabled(),
                themeMode = repo.sessionMgr.getThemeMode()
            )
        }
    }

    private fun bootstrapSession() {
        viewModelScope.launch(Dispatchers.IO) {
            val session = repo.sessionMgr.getSession()
            val cachedProfile = repo.sessionMgr.getCachedProfile()
            val hasSms = SmsInboxScanner.hasSmsPermissions(repo.appContext)

            if (session == null) {
                _uiState.value = _uiState.value.copy(
                    authGate = AuthGateState.UNAUTHENTICATED,
                    hasSmsPermissions = hasSms,
                    isOnline = repo.isOnline()
                )
                return@launch
            }

            val biometricEnabled = repo.sessionMgr.isBiometricEnabled() &&
                BiometricHelper.isBiometricAvailable(repo.appContext)
            val locked = repo.sessionMgr.isSessionLocked() || biometricEnabled

            _uiState.value = _uiState.value.copy(
                authGate = if (locked) AuthGateState.LOCKED else AuthGateState.AUTHENTICATED,
                profile = cachedProfile,
                emailVerified = session.emailConfirmed,
                hasSmsPermissions = hasSms,
                isOnline = repo.isOnline()
            )
            reloadFromLocalDb()

            if (!locked && repo.isOnline()) {
                refreshCloudData(silent = true)
            }
        }
    }

    fun onPermissionOrConnectivityChanged() {
        val hasSms = SmsInboxScanner.hasSmsPermissions(repo.appContext)
        val wasGranted = repo.sessionMgr.wasSmsPermissionGranted()
        repo.sessionMgr.setSmsPermissionGranted(hasSms)
        if (hasSms) {
            repo.db.updateSyncState(monitoringStatus = "ACTIVE")
        } else {
            repo.db.updateSyncState(monitoringStatus = if (wasGranted) "PERMISSION_REVOKED" else "INACTIVE")
        }
        reloadFromLocalDb()
    }

    fun getRememberedEmail(): String = repo.sessionMgr.getRememberedEmail()

    fun isBiometricCapable(): Boolean = BiometricHelper.isBiometricAvailable(repo.appContext)

    // ── AUTH ACTIONS ─────────────────────────────────────────────────────────

    fun signIn(
        email: String,
        password: String,
        rememberEmail: Boolean,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            val res = repo.signIn(email, password, rememberEmail)
            when (res) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        authGate = AuthGateState.AUTHENTICATED,
                        profile = res.data,
                        emailVerified = repo.sessionMgr.getSession()?.emailConfirmed ?: true
                    )
                    reloadFromLocalDb()
                    loadDevices()
                    onResult(true, null)
                }
                is ApiResult.Error -> onResult(false, res.message)
            }
        }
    }

    fun signUp(
        fullName: String,
        email: String,
        password: String,
        onResult: (success: Boolean, needsVerification: Boolean, error: String?) -> Unit
    ) {
        viewModelScope.launch {
            when (val res = repo.signUp(fullName, email, password)) {
                is ApiResult.Success -> {
                    if (!res.data) {
                        _uiState.value = _uiState.value.copy(
                            authGate = AuthGateState.AUTHENTICATED,
                            profile = repo.sessionMgr.getCachedProfile()
                        )
                        reloadFromLocalDb()
                    }
                    onResult(true, res.data, null)
                }
                is ApiResult.Error -> onResult(false, false, res.message)
            }
        }
    }

    fun resendVerification(email: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            when (val res = repo.resendVerification(email)) {
                is ApiResult.Success -> onDone("Verification email sent to $email")
                is ApiResult.Error -> onDone(res.message)
            }
        }
    }

    fun recoverPassword(email: String, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            when (val res = repo.recoverPassword(email)) {
                is ApiResult.Success -> onDone(true, "Password reset instructions sent to $email")
                is ApiResult.Error -> onDone(false, res.message)
            }
        }
    }

    fun unlockWithBiometricSuccess() {
        repo.sessionMgr.setSessionLocked(false)
        _uiState.value = _uiState.value.copy(authGate = AuthGateState.AUTHENTICATED)
        refreshCloudData(silent = true)
    }

    fun unlockWithPassword(password: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            when (val res = repo.verifyPasswordForUnlock(password)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(authGate = AuthGateState.AUTHENTICATED)
                    refreshCloudData(silent = true)
                    onResult(true, null)
                }
                is ApiResult.Error -> onResult(false, res.message)
            }
        }
    }

    fun lockSession() {
        repo.sessionMgr.setSessionLocked(true)
        _uiState.value = _uiState.value.copy(authGate = AuthGateState.LOCKED)
    }

    fun signOut() {
        viewModelScope.launch {
            repo.signOut()
            _uiState.value = MonziUiState(
                authGate = AuthGateState.UNAUTHENTICATED,
                hasSmsPermissions = SmsInboxScanner.hasSmsPermissions(repo.appContext),
                isOnline = repo.isOnline()
            )
        }
    }

    // ── SYNC & SMS ───────────────────────────────────────────────────────────

    fun refreshCloudData(silent: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, isOnline = repo.isOnline())
            val res = repo.syncAllFromCloud()
            val devices = repo.fetchDevices()
            reloadFromLocalDb()
            _uiState.value = _uiState.value.copy(isSyncing = false, devices = devices)
            if (!silent) {
                when (res) {
                    is ApiResult.Success -> emitMessage("Cloud sync completed")
                    is ApiResult.Error -> emitMessage(res.message)
                }
            }
        }
    }

    fun flushPendingQueueNow() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true)
            val count = repo.flushPendingNow()
            reloadFromLocalDb()
            _uiState.value = _uiState.value.copy(isSyncing = false)
            emitMessage(if (count > 0) "Synced $count queued transaction(s)" else "Queue up to date")
        }
    }

    fun previewSmsInbox(
        startDateMs: Long? = null,
        endDateMs: Long? = null,
        onResult: (Int) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val count = repo.previewSmsCount(startDateMs, endDateMs)
            withContext(Dispatchers.Main) {
                onResult(count)
            }
        }
    }

    fun scanHistoricalSms(
        limit: Int = 1000,
        fullRescan: Boolean = false,
        startDateMs: Long? = null,
        endDateMs: Long? = null,
        dateRangeLabel: String = "Selected Range",
        onComplete: ((InboxScanResult) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanningInbox = true)
            val result: InboxScanResult = repo.scanSmsInbox(
                startDateMs = startDateMs,
                endDateMs = endDateMs,
                limit = limit,
                fullRescan = fullRescan,
                dateRangeLabel = dateRangeLabel
            )
            reloadFromLocalDb()
            _uiState.value = _uiState.value.copy(
                isScanningInbox = false,
                lastScanSummary = result
            )
            onComplete?.invoke(result)
            when {
                result.permissionMissing -> emitMessage("Grant SMS permissions first to scan inbox")
                result.alreadyRunning -> emitMessage("Historical SMS scan is already running")
                result.error != null -> emitMessage("Scan error: ${result.error}")
                result.newlyQueued > 0 -> emitMessage("Scanned ${result.scanned} SMS • Added ${result.newlyQueued} transaction(s)")
                else -> emitMessage("Scanned ${result.scanned} SMS • No new transactions found")
            }
        }
    }

    fun reprocessHistoricalRange(
        startDateMs: Long,
        endDateMs: Long,
        onComplete: (com.krunal.monzi.ReprocessResult) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanningInbox = true)
            val result = repo.reprocessDateRange(startDateMs, endDateMs)
            reloadFromLocalDb()
            _uiState.value = _uiState.value.copy(
                isScanningInbox = false,
                lastReprocessSummary = result
            )
            onComplete(result)
            if (result.status == "COMPLETED") {
                emitMessage("Reprocessed ${result.totalExamined} txn(s): ${result.updatedCount} updated, ${result.reclassifiedCount} reclassified, balances recalculated")
            } else {
                emitMessage("Reprocess failed: ${result.error ?: "Unknown error"}")
            }
        }
    }

    fun ingestSingleSms(rawSms: String, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            when (val res = repo.ingestManualSms(rawSms)) {
                is ApiResult.Success -> {
                    reloadFromLocalDb()
                    emitMessage(res.data)
                    onDone(true, res.data)
                }
                is ApiResult.Error -> onDone(false, res.message)
            }
        }
    }

    fun ingestBulkSms(blocks: List<String>, onDone: (Int, Int) -> Unit) {
        viewModelScope.launch {
            when (val res = repo.ingestBulkSms(blocks)) {
                is ApiResult.Success -> {
                    reloadFromLocalDb()
                    val (added, dups) = res.data
                    emitMessage("Saved $added transaction(s)" + if (dups > 0) ", $dups duplicate(s) skipped" else "")
                    onDone(added, dups)
                }
                is ApiResult.Error -> {
                    emitMessage(res.message)
                    onDone(0, 0)
                }
            }
        }
    }

    fun loadDevices() {
        viewModelScope.launch {
            val devices = repo.fetchDevices()
            _uiState.value = _uiState.value.copy(devices = devices)
        }
    }

    // ── ANALYTICS PERIOD ─────────────────────────────────────────────────────

    fun selectAnalyticsPeriod(period: AnalyticsPeriod, customStart: Long? = null, customEnd: Long? = null) {
        val cur = _uiState.value
        val s = customStart ?: cur.customStartMs
        val e = customEnd ?: cur.customEndMs
        val stats = FinanceAnalyticsEngine.computePeriodAnalytics(
            period = period,
            accounts = cur.accounts,
            transactions = cur.transactions,
            customStartMs = s,
            customEndMs = e
        )
        _uiState.value = cur.copy(
            selectedPeriod = period,
            customStartMs = s,
            customEndMs = e,
            periodAnalytics = stats
        )
    }

    // ── CRUD: TRANSACTIONS, ACCOUNTS, CATEGORIES, RULES, SETTINGS ────────────

    fun createTransaction(
        amount: Double,
        direction: String,
        merchant: String,
        accountId: String?,
        categoryId: String?,
        paymentMode: String,
        notes: String?,
        transactionDateMs: Long,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            when (
                val res = repo.createManualTransaction(
                    amount, direction, merchant, accountId, categoryId, paymentMode, notes, transactionDateMs
                )
            ) {
                is ApiResult.Success -> {
                    reloadFromLocalDb()
                    emitMessage("Transaction saved")
                    onResult(true, null)
                }
                is ApiResult.Error -> {
                    emitMessage(res.message)
                    onResult(false, res.message)
                }
            }
        }
    }

    fun updateTransaction(
        txn: TransactionEntity,
        categoryId: String?,
        notes: String?,
        merchant: String?,
        transactionType: String?,
        amount: Double? = null,
        direction: String? = null,
        accountId: String? = null,
        transactionDateMs: Long? = null,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            when (
                val res = repo.updateTransaction(
                    txn = txn,
                    categoryId = categoryId,
                    notes = notes,
                    merchant = merchant,
                    transactionType = transactionType,
                    amount = amount,
                    direction = direction,
                    accountId = accountId,
                    transactionDateMs = transactionDateMs
                )
            ) {
                is ApiResult.Success -> {
                    reloadFromLocalDb()
                    emitMessage("Transaction updated")
                    onResult(true, null)
                }
                is ApiResult.Error -> {
                    emitMessage(res.message)
                    onResult(false, res.message)
                }
            }
        }
    }

    fun setThemeMode(mode: String) {
        repo.sessionMgr.setThemeMode(mode)
        _uiState.value = _uiState.value.copy(themeMode = repo.sessionMgr.getThemeMode())
    }

    fun deleteTransaction(txn: TransactionEntity, onDone: () -> Unit) {
        viewModelScope.launch {
            when (val res = repo.deleteTransaction(txn)) {
                is ApiResult.Success -> {
                    reloadFromLocalDb()
                    emitMessage("Transaction deleted")
                    onDone()
                }
                is ApiResult.Error -> emitMessage(res.message)
            }
        }
    }

    fun addAccount(
        bankName: String,
        last4Digits: String,
        accountType: String,
        initialBalance: Double,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            when (val res = repo.addAccount(bankName, last4Digits, accountType, initialBalance)) {
                is ApiResult.Success -> {
                    reloadFromLocalDb()
                    emitMessage("$bankName added")
                    onResult(true, null)
                }
                is ApiResult.Error -> {
                    emitMessage(res.message)
                    onResult(false, res.message)
                }
            }
        }
    }

    fun updateAccount(
        account: AccountEntity,
        bankName: String,
        accountName: String?,
        last4Digits: String,
        accountType: String,
        newBalance: Double,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            when (val res = repo.updateAccount(account, bankName, accountName, last4Digits, accountType, newBalance)) {
                is ApiResult.Success -> {
                    reloadFromLocalDb()
                    emitMessage("Account updated")
                    onResult(true, null)
                }
                is ApiResult.Error -> {
                    emitMessage(res.message)
                    onResult(false, res.message)
                }
            }
        }
    }

    fun updateAccountBalance(account: AccountEntity, newBalance: Double) {
        viewModelScope.launch {
            when (val res = repo.updateAccountBalance(account, newBalance)) {
                is ApiResult.Success -> {
                    reloadFromLocalDb()
                    emitMessage("Account balance updated")
                }
                is ApiResult.Error -> emitMessage(res.message)
            }
        }
    }

    fun deleteAccount(account: AccountEntity) {
        viewModelScope.launch {
            when (val res = repo.deleteAccount(account)) {
                is ApiResult.Success -> {
                    reloadFromLocalDb()
                    emitMessage("Account removed")
                }
                is ApiResult.Error -> emitMessage(res.message)
            }
        }
    }

    fun createCategory(name: String, color: String, isIncome: Boolean, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            when (val res = repo.createCategory(name, color, isIncome)) {
                is ApiResult.Success -> {
                    reloadFromLocalDb()
                    emitMessage("Category \"$name\" added")
                    onResult(true, null)
                }
                is ApiResult.Error -> {
                    emitMessage(res.message)
                    onResult(false, res.message)
                }
            }
        }
    }

    fun updateCategory(category: com.krunal.monzi.data.CategoryEntity, name: String, color: String, isIncome: Boolean, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            when (val res = repo.updateCategory(category, name, color, isIncome)) {
                is ApiResult.Success -> {
                    reloadFromLocalDb()
                    emitMessage("Category updated")
                    onResult(true, null)
                }
                is ApiResult.Error -> {
                    emitMessage(res.message)
                    onResult(false, res.message)
                }
            }
        }
    }

    fun deleteCategory(category: com.krunal.monzi.data.CategoryEntity, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            when (val res = repo.deleteCategory(category)) {
                is ApiResult.Success -> {
                    reloadFromLocalDb()
                    emitMessage("Category deleted")
                    onResult(true, null)
                }
                is ApiResult.Error -> {
                    emitMessage(res.message)
                    onResult(false, res.message)
                }
            }
        }
    }

    fun createRule(keyword: String, categoryId: String, priority: Int, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            when (val res = repo.createRule(keyword, categoryId, priority)) {
                is ApiResult.Success -> {
                    reloadFromLocalDb()
                    emitMessage("Rule added")
                    onResult(true, null)
                }
                is ApiResult.Error -> {
                    emitMessage(res.message)
                    onResult(false, res.message)
                }
            }
        }
    }

    fun updateRule(rule: TransactionRuleEntity, keyword: String, categoryId: String, priority: Int, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            when (val res = repo.updateRule(rule, keyword, categoryId, priority)) {
                is ApiResult.Success -> {
                    reloadFromLocalDb()
                    emitMessage("Rule updated")
                    onResult(true, null)
                }
                is ApiResult.Error -> {
                    emitMessage(res.message)
                    onResult(false, res.message)
                }
            }
        }
    }

    fun toggleRule(rule: TransactionRuleEntity, enabled: Boolean) {
        viewModelScope.launch {
            repo.toggleRule(rule, enabled)
            reloadFromLocalDb()
        }
    }

    fun deleteRule(rule: TransactionRuleEntity) {
        viewModelScope.launch {
            repo.deleteRule(rule)
            reloadFromLocalDb()
            emitMessage("Rule deleted")
        }
    }

    fun setBudgetAlertsEnabled(enabled: Boolean) {
        repo.setBudgetAlertsEnabled(enabled)
        _uiState.value = _uiState.value.copy(budgetAlertsEnabled = enabled)
    }

    fun updateProfileName(fullName: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            when (val res = repo.updateProfileName(fullName)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(profile = res.data)
                    emitMessage("Profile updated")
                    onResult(true, null)
                }
                is ApiResult.Error -> {
                    emitMessage(res.message)
                    onResult(false, res.message)
                }
            }
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repo.updateBiometricPreference(enabled)
            _uiState.value = _uiState.value.copy(profile = repo.sessionMgr.getCachedProfile())
            emitMessage(if (enabled) "Biometric app lock enabled" else "Biometric app lock disabled")
        }
    }

    fun regenerateSyncToken() {
        viewModelScope.launch {
            when (val res = repo.regenerateDeviceSyncToken()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(profile = res.data)
                    emitMessage("New Device Sync Token generated")
                }
                is ApiResult.Error -> emitMessage(res.message)
            }
        }
    }

    fun exportCsvAndShare(
        startMs: Long? = null,
        endMs: Long? = null,
        accountId: String? = null,
        onShareIntentReady: (Intent) -> Unit
    ) {
        viewModelScope.launch {
            val file = repo.exportTransactionsCsv(startMs, endMs, accountId)
            val uri = withContext(Dispatchers.Main) {
                FileProvider.getUriForFile(
                    repo.appContext,
                    "${repo.appContext.packageName}.fileprovider",
                    file
                )
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onShareIntentReady(Intent.createChooser(intent, "Export Monzi Transactions CSV"))
        }
    }

    // ── MONZI 2.1 ACTIONS ────────────────────────────────────────────────────

    fun addBalanceAdjustment(account: AccountEntity, adjustmentAmount: Double, reason: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.addBalanceAdjustment(account, adjustmentAmount, reason)
            reloadFromLocalDb()
            emitMessage("Balance adjustment applied")
            onDone()
        }
    }

    fun addOrUpdateCreditCard(
        existingCard: AccountEntity?,
        bankName: String,
        accountName: String?,
        last4Digits: String,
        creditLimit: Double?,
        openingOutstanding: Double,
        statementDay: Int?,
        dueDay: Int?,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            when (
                val res = repo.addOrUpdateCreditCard(
                    existingCard, bankName, accountName, last4Digits,
                    creditLimit, openingOutstanding, statementDay, dueDay
                )
            ) {
                is ApiResult.Success -> {
                    reloadFromLocalDb()
                    emitMessage("$bankName credit card saved")
                    onResult(true, null)
                }
                is ApiResult.Error -> {
                    emitMessage(res.message)
                    onResult(false, res.message)
                }
            }
        }
    }

    fun upsertCreditCardStatement(
        card: AccountEntity,
        totalDue: Double,
        minimumDue: Double,
        dueDateMs: Long?,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            when (val res = repo.upsertCreditCardStatement(card, totalDue, minimumDue, dueDateMs)) {
                is ApiResult.Success -> {
                    reloadFromLocalDb()
                    emitMessage("Credit card statement updated")
                    onResult(true, null)
                }
                is ApiResult.Error -> {
                    emitMessage(res.message)
                    onResult(false, res.message)
                }
            }
        }
    }

    fun recordCreditCardPayment(
        card: AccountEntity,
        sourceAccount: AccountEntity?,
        amount: Double,
        statement: CreditCardStatementEntity?,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            when (val res = repo.recordCreditCardPayment(card, sourceAccount, amount, statement)) {
                is ApiResult.Success -> {
                    reloadFromLocalDb()
                    emitMessage("Credit card payment recorded")
                    onResult(true, null)
                }
                is ApiResult.Error -> {
                    emitMessage(res.message)
                    onResult(false, res.message)
                }
            }
        }
    }

    fun saveBudget(
        existingId: String?,
        name: String,
        categoryId: String?,
        monthlyLimit: Double,
        alertThreshold: Int,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            when (val res = repo.saveBudget(existingId, name, categoryId, monthlyLimit, alertThreshold)) {
                is ApiResult.Success -> {
                    reloadFromLocalDb()
                    emitMessage("Budget saved")
                    onResult(true, null)
                }
                is ApiResult.Error -> {
                    emitMessage(res.message)
                    onResult(false, res.message)
                }
            }
        }
    }

    fun deleteBudget(budgetId: String) {
        viewModelScope.launch {
            repo.deleteBudget(budgetId)
            reloadFromLocalDb()
            emitMessage("Budget deleted")
        }
    }

    fun selectBudgetMonth(monthKey: String) {
        val cur = _uiState.value
        val eval = BudgetAndCardEngine.evaluateBudgets(
            budgets = cur.budgets,
            transactions = cur.transactions,
            refunds = cur.refunds,
            existingAlerts = cur.budgetAlerts,
            monthKey = monthKey,
            userId = repo.sessionMgr.getUserId()
        )
        _uiState.value = cur.copy(selectedBudgetMonth = monthKey, budgetEvaluation = eval)
    }

    fun acknowledgeBudgetAlert(alertId: String) {
        viewModelScope.launch {
            repo.acknowledgeBudgetAlert(alertId)
            reloadFromLocalDb()
        }
    }

    fun confirmSeparateTransaction(txn: TransactionEntity, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.confirmSeparateTransaction(txn)
            reloadFromLocalDb()
            emitMessage("Marked as confirmed separate transaction")
            onDone()
        }
    }

    fun confirmTransferStatus(txn: TransactionEntity, isTransfer: Boolean, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.confirmTransferStatus(txn, isTransfer)
            reloadFromLocalDb()
            emitMessage(if (isTransfer) "Confirmed as own-account transfer" else "Marked as regular transaction")
            onDone()
        }
    }

    fun retryFailedSyncItems() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true)
            repo.retryFailedSyncItems()
            reloadFromLocalDb()
            _uiState.value = _uiState.value.copy(isSyncing = false)
            emitMessage("Retried failed sync items")
        }
    }

    fun resetAllData(onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            when (val res = repo.resetAllData()) {
                is ApiResult.Success -> {
                    reloadFromLocalDb()
                    emitMessage("All transactions, accounts, budgets, rules, and logs cleared")
                    onResult(true, null)
                }
                is ApiResult.Error -> {
                    emitMessage(res.message)
                    onResult(false, res.message)
                }
            }
        }
    }
}
