package com.krunal.monzi

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krunal.monzi.data.TransactionEntity
import com.krunal.monzi.ui.AuthGateState
import com.krunal.monzi.ui.BiometricHelper
import com.krunal.monzi.ui.MonziViewModel
import com.krunal.monzi.ui.components.MonziAmbientBackground
import com.krunal.monzi.ui.screens.AccountsScreen
import com.krunal.monzi.ui.screens.AnalyticsScreen
import com.krunal.monzi.ui.screens.BiometricLockScreen
import com.krunal.monzi.ui.screens.BudgetsScreen
import com.krunal.monzi.ui.screens.CreditCardsScreen
import com.krunal.monzi.ui.screens.HomeScreen
import com.krunal.monzi.ui.screens.LoginScreen
import com.krunal.monzi.ui.screens.MoreScreen
import com.krunal.monzi.ui.screens.SettingsScreen
import com.krunal.monzi.ui.screens.SignupScreen
import com.krunal.monzi.ui.screens.TransactionsScreen
import com.krunal.monzi.ui.sheets.AddAccountSheet
import com.krunal.monzi.ui.sheets.AddTransactionSheet
import com.krunal.monzi.ui.sheets.SetupChecklistSheet
import com.krunal.monzi.ui.sheets.SmsSyncCenterSheet
import com.krunal.monzi.ui.sheets.TransactionDetailSheet
import com.krunal.monzi.ui.theme.MonziColors
import com.krunal.monzi.ui.theme.MonziTheme

class MainActivity : AppCompatActivity() {

    private val viewModel: MonziViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.onPermissionOrConnectivityChanged()
    }

    private val smsSyncedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SyncWorker.ACTION_SMS_SYNCED) {
                viewModel.reloadFromLocalDb()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        NotificationHelper.createNotificationChannel(applicationContext)
        SyncWorker.schedulePeriodicRetry(applicationContext)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            MonziTheme(themeMode = state.themeMode) {
                val isDark = MonziColors.IsDark
                LaunchedEffect(isDark) {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !isDark
                        isAppearanceLightNavigationBars = !isDark
                    }
                }
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    viewModel.messages.collect { msg ->
                        snackbarHostState.showSnackbar(msg)
                    }
                }

                MonziAmbientBackground {
                    MonziRootApp(
                        state = state,
                        snackbarHostState = snackbarHostState,
                        viewModel = viewModel,
                        biometricAvailable = viewModel.isBiometricCapable(),
                        isBatteryOptimizedIgnored = isIgnoringBatteryOptimizations(),
                        onRequestSmsPermissions = { requestSmsAndNotificationPermissions() },
                        onRequestBatteryWhitelist = { openBatteryOptimizationPrompt() },
                        onLaunchBiometricPrompt = {
                            BiometricHelper.authenticate(
                                activity = this@MainActivity,
                                onSuccess = { viewModel.unlockWithBiometricSuccess() },
                                onError = { /* User can retry or use password fallback */ }
                            )
                        },
                        onEnrollBiometricPrompt = {
                            BiometricHelper.authenticate(
                                activity = this@MainActivity,
                                title = "Enable Biometric Lock",
                                subtitle = "Confirm your Fingerprint or Face Unlock to protect Monzi",
                                negativeButtonText = "Cancel",
                                onSuccess = { viewModel.setBiometricEnabled(true) },
                                onError = { /* Keep disabled on failure */ }
                            )
                        },
                        onShareCsv = { shareIntent ->
                            startActivity(shareIntent)
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            smsSyncedReceiver,
            IntentFilter(SyncWorker.ACTION_SMS_SYNCED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.onPermissionOrConnectivityChanged()
        if (LocalFinanceDbHelper.getInstance(applicationContext).getPendingCount() > 0) {
            SyncWorker.enqueuePendingFlush(applicationContext)
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(smsSyncedReceiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun requestSmsAndNotificationPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun openBatteryOptimizationPrompt() {
        if (!isIgnoringBatteryOptimizations()) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }
    }
}

@Composable
private fun MonziRootApp(
    state: com.krunal.monzi.ui.MonziUiState,
    snackbarHostState: SnackbarHostState,
    viewModel: MonziViewModel,
    biometricAvailable: Boolean,
    isBatteryOptimizedIgnored: Boolean,
    onRequestSmsPermissions: () -> Unit,
    onRequestBatteryWhitelist: () -> Unit,
    onLaunchBiometricPrompt: () -> Unit,
    onEnrollBiometricPrompt: () -> Unit,
    onShareCsv: (Intent) -> Unit
) {
    var authScreenMode by rememberSaveable { mutableStateOf("login") }

    when (state.authGate) {
        AuthGateState.LOADING -> {
            Box(
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MonziColors.BluePrimary)
            }
        }

        AuthGateState.UNAUTHENTICATED -> {
            Box(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                if (authScreenMode == "signup") {
                    SignupScreen(
                        onSignUp = { name, email, pass, cb -> viewModel.signUp(name, email, pass, cb) },
                        onResendVerification = { email, cb -> viewModel.resendVerification(email, cb) },
                        onNavigateToLogin = { authScreenMode = "login" }
                    )
                } else {
                    LoginScreen(
                        initialEmail = viewModel.getRememberedEmail(),
                        onSignIn = { email, pass, rem, cb -> viewModel.signIn(email, pass, rem, cb) },
                        onRecoverPassword = { email, cb -> viewModel.recoverPassword(email, cb) },
                        onNavigateToSignup = { authScreenMode = "signup" }
                    )
                }
            }
        }

        AuthGateState.LOCKED -> {
            LaunchedEffect(Unit) {
                if (biometricAvailable) {
                    onLaunchBiometricPrompt()
                }
            }
            Box(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                BiometricLockScreen(
                    profile = state.profile,
                    biometricAvailable = biometricAvailable,
                    onRequestBiometricPrompt = onLaunchBiometricPrompt,
                    onUnlockWithPassword = { pass, cb -> viewModel.unlockWithPassword(pass, cb) },
                    onSignOut = { viewModel.signOut() }
                )
            }
        }

        AuthGateState.AUTHENTICATED -> {
            // 0 = Home, 1 = Txns, 2 = Analytics, 3 = More
            var selectedTab by rememberSaveable { mutableIntStateOf(0) }
            // Sub-screens: "accounts", "credit_cards", "budgets", "settings"
            var activeSubScreen by rememberSaveable { mutableStateOf<String?>(null) }
            var txAccountFilterId by rememberSaveable { mutableStateOf<String?>(null) }
            var txReviewOnly by rememberSaveable { mutableStateOf(false) }

            var showAddTransaction by remember { mutableStateOf(false) }
            var showAddAccount by remember { mutableStateOf(false) }
            var showSmsCenter by remember { mutableStateOf(false) }
            var showSetupChecklist by remember { mutableStateOf(false) }
            var selectedTransaction by remember { mutableStateOf<TransactionEntity?>(null) }

            BackHandler(enabled = activeSubScreen != null || selectedTab != 0) {
                if (activeSubScreen != null) {
                    activeSubScreen = null
                } else {
                    selectedTab = 0
                }
            }

            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.padding(bottom = 96.dp)
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .statusBarsPadding()
                ) {
                    when (activeSubScreen) {
                        "accounts" -> AccountsScreen(
                            state = state,
                            onBack = { activeSubScreen = null },
                            onOpenAddAccount = { showAddAccount = true },
                            onOpenCreditCards = { activeSubScreen = "credit_cards" },
                            onViewAccountTransactions = { acc ->
                                txAccountFilterId = acc.id
                                activeSubScreen = null
                                selectedTab = 1
                            },
                            onUpdateAccount = { acc, bank, name, last4, type, bal, done ->
                                viewModel.updateAccount(acc, bank, name, last4, type, bal, done)
                            },
                            onUpdateAccountBalance = { acc, bal -> viewModel.updateAccountBalance(acc, bal) },
                            onAddAdjustment = { acc, amt, reason, done ->
                                viewModel.addBalanceAdjustment(acc, amt, reason, done)
                            },
                            onDeleteAccount = { acc -> viewModel.deleteAccount(acc) }
                        )

                        "budgets" -> BudgetsScreen(
                            state = state,
                            onBack = { activeSubScreen = null },
                            onSelectMonth = { mk -> viewModel.selectBudgetMonth(mk) },
                            onSaveBudget = { id, name, catId, limit, threshold, done ->
                                viewModel.saveBudget(id, name, catId, limit, threshold, done)
                            },
                            onDeleteBudget = { id -> viewModel.deleteBudget(id) },
                            onAcknowledgeAlert = { id -> viewModel.acknowledgeBudgetAlert(id) }
                        )

                        "credit_cards" -> CreditCardsScreen(
                            state = state,
                            onBack = { activeSubScreen = null },
                            onSaveCard = { existing, bank, name, last4, limit, openBal, stmtDay, dueDay, done ->
                                viewModel.addOrUpdateCreditCard(existing, bank, name, last4, limit, openBal, stmtDay, dueDay, done)
                            },
                            onUpsertStatement = { card, totalDue, minDue, dueDate, done ->
                                viewModel.upsertCreditCardStatement(card, totalDue, minDue, dueDate, done)
                            },
                            onRecordPayment = { card, src, amt, stmt, done ->
                                viewModel.recordCreditCardPayment(card, src, amt, stmt, done)
                            },
                            onDeleteCard = { card -> viewModel.deleteAccount(card) },
                            onSelectTransaction = { selectedTransaction = it }
                        )

                        "settings" -> SettingsScreen(
                            state = state,
                            biometricAvailable = biometricAvailable,
                            isBatteryOptimizedIgnored = isBatteryOptimizedIgnored,
                            onBack = { activeSubScreen = null },
                            onUpdateProfileName = { name, done -> viewModel.updateProfileName(name, done) },
                            onToggleBiometric = { enabled ->
                                if (enabled && biometricAvailable) {
                                    onEnrollBiometricPrompt()
                                } else {
                                    viewModel.setBiometricEnabled(false)
                                }
                            },
                            onToggleBudgetAlerts = { enabled -> viewModel.setBudgetAlertsEnabled(enabled) },
                            onLockSession = { viewModel.lockSession() },
                            onSignOut = { viewModel.signOut() },
                            onGrantSmsPermission = onRequestSmsPermissions,
                            onRequestBatteryWhitelist = onRequestBatteryWhitelist,
                            onScanInbox = { limit, full -> viewModel.scanHistoricalSms(limit, full) },
                            onOpenSmsCenter = { showSmsCenter = true },
                            onSyncNow = { viewModel.refreshCloudData() },
                            onRegenerateSyncToken = { viewModel.regenerateSyncToken() },
                            onCreateRule = { kw, catId, prio, done -> viewModel.createRule(kw, catId, prio, done) },
                            onUpdateRule = { r, kw, catId, prio, done -> viewModel.updateRule(r, kw, catId, prio, done) },
                            onToggleRule = { r, en -> viewModel.toggleRule(r, en) },
                            onDeleteRule = { r -> viewModel.deleteRule(r) },
                            onCreateCategory = { name, color, isInc, done -> viewModel.createCategory(name, color, isInc, done) },
                            onUpdateCategory = { c, name, color, isInc, done -> viewModel.updateCategory(c, name, color, isInc, done) },
                            onDeleteCategory = { c, done -> viewModel.deleteCategory(c, done) },
                            onExportCsv = { s, e, accId -> viewModel.exportCsvAndShare(s, e, accId, onShareCsv) },
                            onResetAllData = { done -> viewModel.resetAllData(done) },
                            onChangeThemeMode = { mode -> viewModel.setThemeMode(mode) }
                        )

                        else -> when (selectedTab) {
                            0 -> HomeScreen(
                                state = state,
                                onRefresh = { viewModel.refreshCloudData() },
                                onOpenChecklist = { showSetupChecklist = true },
                                onOpenSmsCenter = { showSmsCenter = true },
                                onSyncQueueNow = { viewModel.flushPendingQueueNow() },
                                onOpenAddTransaction = { showAddTransaction = true },
                                onOpenAddAccount = { showAddAccount = true },
                                onNavigateToAccounts = { activeSubScreen = "accounts" },
                                onNavigateToTransactions = { selectedTab = 1 },
                                onNavigateToAnalytics = { selectedTab = 2 },
                                onOpenSettings = { activeSubScreen = "settings" },
                                onOpenBudgets = { activeSubScreen = "budgets" },
                                onOpenCreditCards = { activeSubScreen = "credit_cards" },
                                onOpenReviewTransactions = {
                                    txReviewOnly = true
                                    selectedTab = 1
                                },
                                onAcknowledgeAlert = { id -> viewModel.acknowledgeBudgetAlert(id) },
                                onSelectTransaction = { selectedTransaction = it }
                            )

                            1 -> TransactionsScreen(
                                state = state,
                                initialAccountFilterId = txAccountFilterId,
                                initialReviewOnly = txReviewOnly,
                                onClearInitialAccountFilter = { txAccountFilterId = null },
                                onClearReviewFilter = { txReviewOnly = false },
                                onSelectTransaction = { selectedTransaction = it }
                            )

                            2 -> AnalyticsScreen(
                                state = state,
                                onSelectPeriod = { p, s, e -> viewModel.selectAnalyticsPeriod(p, s, e) }
                            )

                            3 -> MoreScreen(
                                state = state,
                                onOpenAccounts = { activeSubScreen = "accounts" },
                                onOpenCreditCards = { activeSubScreen = "credit_cards" },
                                onOpenBudgets = { activeSubScreen = "budgets" },
                                onOpenSmsCenter = { showSmsCenter = true },
                                onOpenChecklist = { showSetupChecklist = true },
                                onOpenSettings = { activeSubScreen = "settings" }
                            )
                        }
                    }

                    // Floating Glass Bottom Navigation: Home | Txns | Add (+) | Analytics | More
                    MonziFloatingGlassBottomBar(
                        selectedTab = selectedTab,
                        isSubScreenOpen = activeSubScreen != null,
                        onSelectTab = { tab ->
                            activeSubScreen = null
                            selectedTab = tab
                        },
                        onClickAdd = { showAddTransaction = true },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }

            if (showAddTransaction) {
                AddTransactionSheet(
                    accounts = state.accounts,
                    categories = state.categories,
                    onDismiss = { showAddTransaction = false },
                    onSave = { amt, dir, merch, accId, catId, mode, notes, dateMs, cb ->
                        viewModel.createTransaction(amt, dir, merch, accId, catId, mode, notes, dateMs) { ok, err ->
                            cb(ok, err)
                            if (ok) showAddTransaction = false
                        }
                    }
                )
            }

            selectedTransaction?.let { tx ->
                TransactionDetailSheet(
                    transaction = tx,
                    categories = state.categories,
                    accounts = state.accounts,
                    onDismiss = { selectedTransaction = null },
                    onUpdate = { catId, notes, merch, type, amt, dir, accId, dateMs, cb ->
                        viewModel.updateTransaction(tx, catId, notes, merch, type, amt, dir, accId, dateMs) { ok, err ->
                            cb(ok, err)
                            if (ok) selectedTransaction = null
                        }
                    },
                    onConfirmSeparate = {
                        viewModel.confirmSeparateTransaction(tx) { selectedTransaction = null }
                    },
                    onConfirmTransfer = { isTrf ->
                        viewModel.confirmTransferStatus(tx, isTrf) { selectedTransaction = null }
                    },
                    onDelete = {
                        viewModel.deleteTransaction(tx) {
                            selectedTransaction = null
                        }
                    }
                )
            }

            if (showAddAccount) {
                AddAccountSheet(
                    onDismiss = { showAddAccount = false },
                    onSave = { bank, last4, type, bal, cb ->
                        viewModel.addAccount(bank, last4, type, bal) { ok, err ->
                            cb(ok, err)
                            if (ok) showAddAccount = false
                        }
                    }
                )
            }

            if (showSmsCenter) {
                SmsSyncCenterSheet(
                    state = state,
                    onDismiss = { showSmsCenter = false },
                    onRequestSmsPermission = onRequestSmsPermissions,
                    onScanInbox = { limit, full -> viewModel.scanHistoricalSms(limit = limit, fullRescan = full) },
                    onScanDateRange = { startMs, endMs, label, limit, cb ->
                        viewModel.scanHistoricalSms(
                            startDateMs = startMs,
                            endDateMs = endMs,
                            dateRangeLabel = label,
                            limit = limit,
                            onComplete = cb
                        )
                    },
                    onReprocessDateRange = { startMs, endMs, cb ->
                        viewModel.reprocessHistoricalRange(startMs, endMs, cb)
                    },
                    onPreviewCount = { startMs, endMs, cb ->
                        viewModel.previewSmsInbox(startMs, endMs, cb)
                    },
                    onIngestSingle = { raw, cb -> viewModel.ingestSingleSms(raw, cb) },
                    onIngestBulk = { blocks, cb -> viewModel.ingestBulkSms(blocks, cb) },
                    onRetryFailedSync = { viewModel.retryFailedSyncItems() }
                )
            }

            if (showSetupChecklist) {
                SetupChecklistSheet(
                    state = state,
                    isBatteryOptimizedIgnored = isBatteryOptimizedIgnored,
                    onDismiss = { showSetupChecklist = false },
                    onGrantSmsPermission = onRequestSmsPermissions,
                    onRequestBatteryWhitelist = onRequestBatteryWhitelist,
                    onScanInbox = { viewModel.scanHistoricalSms(1000, false) },
                    onSyncNow = { viewModel.flushPendingQueueNow() }
                )
            }
        }
    }
}

@Composable
private fun MonziFloatingGlassBottomBar(
    selectedTab: Int,
    isSubScreenOpen: Boolean,
    onSelectTab: (Int) -> Unit,
    onClickAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(32.dp),
                    ambientColor = MonziColors.BluePrimary.copy(alpha = 0.16f),
                    spotColor = Color(0x22000000)
                ),
            shape = RoundedCornerShape(32.dp),
            color = MonziColors.CardSurfaceStrong,
            border = BorderStroke(1.dp, MonziColors.GlassBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BottomNavItemButton(
                    label = "Home",
                    icon = Icons.Rounded.Home,
                    selected = !isSubScreenOpen && selectedTab == 0,
                    onClick = { onSelectTab(0) },
                    modifier = Modifier.weight(1f)
                )
                BottomNavItemButton(
                    label = "Txns",
                    icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                    selected = !isSubScreenOpen && selectedTab == 1,
                    onClick = { onSelectTab(1) },
                    modifier = Modifier.weight(1f)
                )
                // Central gap reserved for floating Add button
                Spacer(modifier = Modifier.weight(0.95f))
                BottomNavItemButton(
                    label = "Analytics",
                    icon = Icons.Rounded.BarChart,
                    selected = !isSubScreenOpen && selectedTab == 2,
                    onClick = { onSelectTab(2) },
                    modifier = Modifier.weight(1f)
                )
                BottomNavItemButton(
                    label = "More",
                    icon = Icons.Rounded.GridView,
                    selected = isSubScreenOpen || selectedTab == 3,
                    onClick = { onSelectTab(3) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Floating Central Add (+) Action
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-13).dp)
                .size(54.dp)
                .shadow(
                    elevation = 14.dp,
                    shape = CircleShape,
                    ambientColor = Color(0x662468EB),
                    spotColor = Color(0x552468EB)
                )
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(MonziColors.BluePrimary, Color(0xFF2563EB))
                    )
                )
                .border(2.5.dp, Color.White, CircleShape)
                .clickable(onClick = onClickAdd),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Add Transaction",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun BottomNavItemButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MonziColors.BlueSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) MonziColors.BluePrimary else MonziColors.TextMuted,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.5.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (selected) MonziColors.BluePrimary else MonziColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
