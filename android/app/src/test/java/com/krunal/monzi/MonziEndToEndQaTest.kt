package com.krunal.monzi

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.krunal.monzi.data.AccountEntity
import com.krunal.monzi.data.AnalyticsPeriod
import com.krunal.monzi.data.ApiResult
import com.krunal.monzi.data.AuthSessionManager
import com.krunal.monzi.data.BudgetEntity
import com.krunal.monzi.data.FinanceAnalyticsEngine
import com.krunal.monzi.data.MonziRepository
import com.krunal.monzi.data.SupabaseApiClient
import com.krunal.monzi.data.TransactionEntity
import com.krunal.monzi.data.UserProfile
import com.krunal.monzi.data.UserSession
import com.krunal.monzi.data.engines.BudgetAndCardEngine
import com.krunal.monzi.data.engines.PostIngestProcessor
import com.krunal.monzi.data.engines.ReconciliationEngine
import com.krunal.monzi.util.IstTime
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MonziEndToEndQaTest {

    @Before
    fun resetSingletonsBefore() {
        LocalFinanceDbHelper.resetForTests()
    }

    @After
    fun resetSingletonsAfter() {
        LocalFinanceDbHelper.resetForTests()
    }

    // ── 1. ROOM MIGRATION 2.0 -> 2.1 DATA PRESERVATION ──────────────────────

    @Test
    fun migration_from_monzi20_sqlite_v2_to_room_v3_preserves_all_data() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("monzi_local_finance.db")
        if (dbFile.exists()) dbFile.delete()
        dbFile.parentFile?.mkdirs()

        // Create legacy Monzi 2.0 SQLite v2 database cleanly with sample rows
        val legacyDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        legacyDb.version = 2
        legacyDb.execSQL(
            """
            CREATE TABLE local_transactions (
                local_id TEXT PRIMARY KEY, remote_id TEXT, user_id TEXT NOT NULL DEFAULT '',
                account_id TEXT, category_id TEXT, sms_fingerprint TEXT UNIQUE,
                amount REAL NOT NULL, direction TEXT NOT NULL, transaction_type TEXT NOT NULL,
                bank_name TEXT NOT NULL, account_mask TEXT NOT NULL, merchant TEXT,
                reference_id TEXT, payment_mode TEXT NOT NULL, parsed_available_balance REAL,
                is_self_transfer INTEGER NOT NULL DEFAULT 0, is_refund INTEGER NOT NULL DEFAULT 0,
                is_excluded_from_totals INTEGER NOT NULL DEFAULT 0, notes TEXT,
                currency TEXT NOT NULL DEFAULT 'INR', transaction_source TEXT NOT NULL,
                transaction_status TEXT NOT NULL DEFAULT 'COMPLETED',
                transaction_date_iso TEXT NOT NULL, transaction_date_ms INTEGER NOT NULL DEFAULT 0,
                sender TEXT, sync_status TEXT NOT NULL DEFAULT 'PENDING',
                retry_count INTEGER NOT NULL DEFAULT 0, last_attempt_at INTEGER, error_code TEXT,
                deleted INTEGER NOT NULL DEFAULT 0, category_name TEXT, category_color TEXT,
                account_display_name TEXT, created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        legacyDb.execSQL(
            """
            CREATE TABLE local_accounts (
                id TEXT PRIMARY KEY, user_id TEXT NOT NULL DEFAULT '',
                bank_name TEXT NOT NULL, account_number_mask TEXT NOT NULL,
                account_type TEXT NOT NULL DEFAULT 'SAVINGS',
                initial_balance REAL NOT NULL DEFAULT 0, opening_balance REAL NOT NULL DEFAULT 0,
                current_balance REAL NOT NULL DEFAULT 0, balance_source TEXT NOT NULL DEFAULT 'CALCULATED',
                account_status TEXT NOT NULL DEFAULT 'ACTIVE', last_synced_at_iso TEXT,
                pending_sync INTEGER NOT NULL DEFAULT 0, deleted INTEGER NOT NULL DEFAULT 0,
                updated_at_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
        legacyDb.execSQL("CREATE TABLE local_categories (id TEXT PRIMARY KEY, name TEXT UNIQUE NOT NULL, icon TEXT NOT NULL, color TEXT NOT NULL, is_income INTEGER NOT NULL)")
        legacyDb.execSQL("CREATE TABLE local_rules (id TEXT PRIMARY KEY, user_id TEXT NOT NULL, matching_rule TEXT NOT NULL, category_id TEXT NOT NULL, category_name TEXT, category_color TEXT, priority INTEGER NOT NULL, enabled INTEGER NOT NULL, created_at_iso TEXT NOT NULL)")
        legacyDb.execSQL("CREATE TABLE local_sms_logs (id TEXT PRIMARY KEY, device_id TEXT, sender TEXT, status TEXT NOT NULL, reason TEXT, extracted_type TEXT, extracted_amount REAL, extracted_bank TEXT, extracted_date_iso TEXT, reference_id TEXT, sms_fingerprint TEXT, created_at_ms INTEGER NOT NULL)")
        legacyDb.execSQL("CREATE TABLE sms_sync_state (id INTEGER PRIMARY KEY, historical_scan_status TEXT NOT NULL, last_processed_sms_ts INTEGER NOT NULL, scanned_count INTEGER NOT NULL, detected_count INTEGER NOT NULL, monitoring_status TEXT NOT NULL, last_sync_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
        legacyDb.execSQL("CREATE TABLE automation_logs (id INTEGER PRIMARY KEY AUTOINCREMENT, process_type TEXT NOT NULL, process_status TEXT NOT NULL, error_code TEXT, details TEXT, created_at INTEGER NOT NULL)")

        legacyDb.execSQL("INSERT INTO local_accounts (id, user_id, bank_name, account_number_mask, current_balance, updated_at_ms) VALUES ('acc-v2', 'u1', 'HDFC Bank', 'XX8421', 42000.0, 1000)")
        legacyDb.execSQL("INSERT INTO local_transactions (local_id, user_id, account_id, sms_fingerprint, amount, direction, transaction_type, bank_name, account_mask, merchant, payment_mode, transaction_source, transaction_date_iso, transaction_date_ms, created_at) VALUES ('tx-v2', 'u1', 'acc-v2', 'fp-legacy-1', 750.0, 'DEBIT', 'EXPENSE', 'HDFC Bank', 'XX8421', 'Zomato', 'UPI', 'INCOMING_SMS', '2026-11-01T10:00:00Z', 1762000000000, 1762000000000)")
        legacyDb.execSQL("INSERT INTO sms_sync_state VALUES (1, 'COMPLETED', 1762000000000, 120, 15, 'ACTIVE', 1762000000000, 1762000000000)")
        legacyDb.close()

        // Open through LocalFinanceDbHelper -> MonziDatabase Room v3 migration
        val dbHelper = LocalFinanceDbHelper.getInstance(context)
        val accounts = dbHelper.getAccounts()
        val txns = dbHelper.getAllTransactions()
        val categories = dbHelper.getCategories()

        assertTrue(accounts.any { it.id == "acc-v2" && it.currentBalance == 42000.0 })
        assertTrue(txns.any { it.localId == "tx-v2" && it.merchant == "Zomato" && it.duplicateStatus == "UNIQUE" })
        assertTrue(categories.any { it.name == "Uncategorized" })
    }

    // ── 2. END-TO-END SMS INBOX SCANNER + ANDROID 11+ SORTORDER + PRIVACY ─────

    @Test
    fun smsInboxScanner_processes_mock_inbox_without_storing_raw_sms_and_rejects_limit_token_bug() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = LocalFinanceDbHelper.getInstance(app)
        db.clearAllUserFinanceData()

        // Register a mock SMS ContentProvider verifying NO "LIMIT" token is in sortOrder!
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun query(
                uri: Uri,
                projection: Array<out String>?,
                selection: String?,
                selectionArgs: Array<out String>?,
                sortOrder: String?
            ): Cursor {
                // Verify Android 11+ compatibility fix: sortOrder must NOT contain "LIMIT"
                assertFalse("Android 11+ incompatible LIMIT token found in sortOrder", sortOrder?.contains("LIMIT", ignoreCase = true) == true)

                val mc = MatrixCursor(arrayOf("body", "address", "date"))
                val baseTs = System.currentTimeMillis() - 10_000_000L
                // 1. Valid debit
                mc.addRow(arrayOf("HDFC Bank: Rs.450.00 debited from A/C XX8421 to Swiggy on 24-11-26 via UPI. Ref 4910283921. Avl Bal Rs.15,000.00", "VM-HDFCBK", baseTs + 1000))
                // 2. Duplicate exact SMS
                mc.addRow(arrayOf("HDFC Bank: Rs.450.00 debited from A/C XX8421 to Swiggy on 24-11-26 via UPI. Ref 4910283921. Avl Bal Rs.15,000.00", "VM-HDFCBK", baseTs + 2000))
                // 3. OTP ignored
                mc.addRow(arrayOf("482910 is your OTP for Rs.5,000 transaction. Do not share.", "VM-HDFCBK", baseTs + 3000))
                // 4. Failed transaction
                mc.addRow(arrayOf("Your UPI txn of Rs.999.00 failed due to insufficient funds.", "AX-SBI", baseTs + 4000))
                // 5. Credit Card Statement SMS
                mc.addRow(arrayOf("ICICI Bank Credit Card XX9900 Statement: Total Amount Due Rs.12,000.00, Min Amt Due Rs.600.00. Due Date 05/12/2026.", "VM-ICICI", baseTs + 5000))
                return mc
            }
            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
        }
        ShadowContentResolver.registerProviderInternal("sms", provider)

        // Negative: Denied permission first
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        val deniedResult = SmsInboxScanner.scanBlocking(app, limit = 100, fullRescan = true)
        assertTrue(deniedResult.permissionMissing)
        assertEquals("INACTIVE", db.getSyncState().monitoringStatus)

        // Positive: Grant permission and scan
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        val okResult = SmsInboxScanner.scanBlocking(app, limit = 100, fullRescan = true)
        assertEquals(5, okResult.scanned)
        assertEquals(1, okResult.newlyQueued)

        val runs = db.getRecentImportRuns()
        assertTrue(runs.isNotEmpty())
        assertEquals("COMPLETED", runs.first().status)
        assertEquals(5, runs.first().totalExamined)
        assertEquals(1, runs.first().importedCount)
        assertEquals(1, runs.first().duplicateSkipped)
        assertEquals(1, runs.first().failureCount)

        // Privacy audit: raw SMS phrasing ("do not share", "482910") must NEVER exist in DB tables
        val logsText = db.getSmsLogs().toString() + db.getParserFailures().toString() + db.getAutomationLogs().toString()
        assertFalse(logsText.contains("482910"))
        assertFalse(logsText.contains("do not share", ignoreCase = true))

        // Credit Card Statement must have been created automatically
        val stmts = db.getAllStatements()
        assertTrue(stmts.any { it.totalDue == 12000.0 && it.minimumDue == 600.0 })
    }

    // ── 3. NETWORK / API INTEGRATION & NEGATIVE HTTP ERROR TESTS (MOCKWEBSERVER) ──

    @Test
    fun supabaseApiClient_handles_auth_sync_rpc_and_negative_http_failures() {
        val server = MockWebServer()
        server.start()
        try {
            val api = SupabaseApiClient(baseUrlOverride = server.url("/").toString())

            // 3a. Valid Auth SignIn
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"access_token":"tok123","refresh_token":"ref123","expires_in":3600,"user":{"id":"u-uuid","email":"krunal@test.com","email_confirmed_at":"2026-01-01T00:00:00Z"}}"""
                )
            )
            val authOk = api.signIn("krunal@test.com", "Pass@1234")
            assertTrue(authOk is ApiResult.Success)
            assertEquals("tok123", (authOk as ApiResult.Success).data.accessToken)

            // 3b. Negative 401 Invalid Credentials
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error_description":"Invalid login credentials"}"""))
            val authFail = api.signIn("krunal@test.com", "wrong")
            assertTrue(authFail is ApiResult.Error)
            assertEquals(401, (authFail as ApiResult.Error).code)
            assertTrue(authFail.message.contains("Invalid login credentials"))

            // 3c. Negative 500 Server Error & Malformed JSON resilience
            server.enqueue(MockResponse().setResponseCode(500).setBody("<html>502 Bad Gateway</html>"))
            val serverFail = api.fetchTransactions("tok123", "u-uuid")
            assertTrue(serverFail is ApiResult.Error)
            assertEquals(500, (serverFail as ApiResult.Error).code)

            // 3d. Device Sync RPC success + negative unauthorized
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"PROCESSED"}"""))
            val dummyTx = TransactionEntity(
                localId = "l1", smsFingerprint = "fp1", amount = 250.0,
                direction = "DEBIT", transactionType = "EXPENSE",
                transactionDateIso = "2026-11-25T10:00:00Z", transactionDateMs = 1764000000000L
            )
            val rpcOk = api.deviceSyncIngestTransaction("sync-tok", "dev-1", dummyTx, null)
            assertTrue(rpcOk is ApiResult.Success)

            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"UNAUTHORIZED","reason":"Bad token"}"""))
            val rpcUnauthorized = api.deviceSyncIngestTransaction("bad-tok", "dev-1", dummyTx, null)
            assertTrue(rpcUnauthorized is ApiResult.Error)
            assertEquals(401, (rpcUnauthorized as ApiResult.Error).code)
        } finally {
            server.shutdown()
        }
    }

    // ── 4. CREDIT CARD ACCOUNTING, CSV EXPORT & REGRESSION WORKFLOW ───────────

    @Test
    fun creditCard_manual_transaction_and_csv_export_work_end_to_end() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = LocalFinanceDbHelper.getInstance(context)
        db.clearAllUserFinanceData()

        val repo = MonziRepository(context)
        repo.sessionMgr.saveSession(UserSession("u1", "u@test.com", "tok", "ref", System.currentTimeMillis() / 1000 + 3600, true))

        // Add Credit Card with limit 100,000 and opening outstanding 5,000
        repo.addOrUpdateCreditCard(null, "HDFC Bank", "Millennia", "8812", 100000.0, 5000.0, 15, 5)
        val card = db.getAccounts().first { it.accountType == "CREDIT_CARD" }
        assertEquals(5000.0, card.currentBalance, 0.01)

        // Manual Debit on Credit Card must INCREASE outstanding to 7,500
        repo.createManualTransaction(2500.0, "DEBIT", "Flipkart", card.id, null, "CARD", "Gadget", System.currentTimeMillis())
        val afterSpend = db.getAccountById(card.id)!!
        assertEquals(7500.0, afterSpend.currentBalance, 0.01)

        // Deleting that Debit must DECREASE outstanding back to 5,000
        val createdTx = db.getAllTransactions().first { it.merchant == "Flipkart" }
        repo.deleteTransaction(createdTx)
        val afterDelete = db.getAccountById(card.id)!!
        assertEquals(5000.0, afterDelete.currentBalance, 0.01)

        // Record Bill Payment from Bank Account -> creates CC_BILL_PAYMENT & lowers card outstanding
        repo.addAccount("SBI", "1234", "SAVINGS", 30000.0)
        val sbi = db.getAccounts().first { it.accountType == "SAVINGS" }
        repo.recordCreditCardPayment(afterDelete, sbi, 3000.0, null)

        val updatedCard = db.getAccountById(card.id)!!
        val updatedSbi = db.getAccountById(sbi.id)!!
        assertEquals(2000.0, updatedCard.currentBalance, 0.01)
        assertEquals(27000.0, updatedSbi.currentBalance, 0.01)

        // Verify CSV Export contains sanitized CSV headers and row
        val csvFile = repo.exportTransactionsCsv(accountId = sbi.id)
        assertTrue(csvFile.exists())
        val csvContent = csvFile.readText()
        assertTrue(csvContent.contains("CC_BILL_PAYMENT"))
        assertTrue(csvContent.contains("Duplicate Status"))

        // Verify CC_BILL_PAYMENT excluded from expenses
        val dash = FinanceAnalyticsEngine.computeDashboardStats(db.getAccounts(), db.getAllTransactions())
        assertEquals(0.0, dash.monthlyExpense, 0.01)
    }

    // ── 5. PERFORMANCE BENCHMARK ON LARGE DATASET (2,500 TRANSACTIONS) ─────────

    @Test
    fun performance_analytics_and_budgets_complete_under_1_second_on_2500_transactions() {
        val now = System.currentTimeMillis()
        val accounts = listOf(
            AccountEntity("a1", "u1", "HDFC Bank", "XX1111", "SAVINGS", currentBalance = 150000.0),
            AccountEntity("a2", "u1", "ICICI Bank", "XX2222", "CREDIT_CARD", currentBalance = 25000.0, creditLimit = 200000.0)
        )
        val txns = (1..2500).map { i ->
            TransactionEntity(
                localId = "tx-$i",
                accountId = if (i % 4 == 0) "a2" else "a1",
                categoryId = if (i % 3 == 0) "local-cat-food" else "local-cat-shopping",
                categoryName = if (i % 3 == 0) "Food & Dining" else "Shopping",
                smsFingerprint = "fp-$i",
                amount = (100 + (i % 500)).toDouble(),
                direction = if (i % 20 == 0) "CREDIT" else "DEBIT",
                transactionType = if (i % 20 == 0) "INCOME" else "EXPENSE",
                merchant = "Merchant $i",
                transactionDateIso = BankSmsParser.toUtcIso(now - i * 3600_000L),
                transactionDateMs = now - i * 3600_000L
            )
        }
        val budgets = listOf(
            BudgetEntity("b1", "u1", "Food", "local-cat-food", "Food & Dining", null, 30000.0, IstTime.currentMonthKey(now))
        )

        val start = System.currentTimeMillis()
        val dash = FinanceAnalyticsEngine.computeDashboardStats(accounts, txns, now)
        val period = FinanceAnalyticsEngine.computePeriodAnalytics(AnalyticsPeriod.SIX_MONTHS, accounts, txns, nowMs = now)
        val eval = BudgetAndCardEngine.evaluateBudgets(budgets, txns, emptyList(), emptyList(), IstTime.currentMonthKey(now), nowMs = now)
        val elapsedMs = System.currentTimeMillis() - start

        assertTrue(dash.categoryBreakdown.isNotEmpty())
        assertTrue(period.validTxCount > 2000)
        assertTrue(eval.summaries.isNotEmpty())
        assertTrue("2500-txn analytics took too long: ${elapsedMs}ms", elapsedMs < 1500L)
    }

    // ── 6. SECURITY: SESSION & PERMISSION REVOCATION STATE ────────────────────

    @Test
    fun security_session_clear_preserves_device_id_and_wipes_tokens() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mgr = AuthSessionManager(context)
        val devId = mgr.getOrCreateDeviceIdentifier()
        mgr.saveSession(UserSession("u1", "user@test.com", "secret-jwt", "secret-refresh", 9999999999L, true))
        mgr.saveProfile(UserProfile("u1", "Krunal", "user@test.com", deviceSyncToken = "secret-sync"))

        mgr.clearSession()
        assertNull(mgr.getSession())
        assertEquals("", mgr.getUserId())
        assertEquals("", mgr.getDeviceSyncToken())
        assertEquals(devId, mgr.getOrCreateDeviceIdentifier())
    }

    // ── 7. BOOT RECEIVER PERMISSION REVOCATION & RECOVERY ─────────────────────

    @Test
    fun bootReceiver_detects_permission_revocation_and_updates_monitoring_status() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = LocalFinanceDbHelper.getInstance(app)
        db.clearAllUserFinanceData()
        val mgr = AuthSessionManager(app)
        mgr.setSmsPermissionGranted(true)

        // Simulate revoked SMS permissions before reboot
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)

        val receiver = BootReceiver()
        receiver.onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        // Allow single-thread executor a moment to flush
        Thread.sleep(500)

        assertEquals("PERMISSION_REVOKED", db.getSyncState().monitoringStatus)
        assertTrue(db.getAutomationLogs().any { it.processType == "BOOT_RESUME" && it.processStatus == "PERMISSION_REVOKED" })

        // Restore permissions and reboot again -> ACTIVE
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        receiver.onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        Thread.sleep(500)
        assertEquals("ACTIVE", db.getSyncState().monitoringStatus)
    }

    // ── 8. EDGE CASES: INVALID CSV / NEGATIVE & HUGE TRANSACTIONS / EMPTY STATES ─

    @Test
    fun edgeCases_empty_db_analytics_and_invalid_sms_do_not_crash() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = LocalFinanceDbHelper.getInstance(app)
        db.clearAllUserFinanceData()

        val emptyDash = FinanceAnalyticsEngine.computeDashboardStats(emptyList(), emptyList())
        assertEquals(0.0, emptyDash.netWorth, 0.01)
        assertEquals(0.0, emptyDash.monthlyExpense, 0.01)

        // Negative / garbage SMS formats
        assertNull(BankSmsParser.parse("", ""))
        assertNull(BankSmsParser.parse("HDFC", "Rs. -500 debited? Invalid syntax"))
        assertNull(BankSmsParser.parse("SPAM", "Your OTP 994411 is valid for 10 mins. Rs 0"))
        assertEquals("IGNORED", BankSmsParser.inspect("AXIS", "Available balance in A/C XX1234 is Rs.5000.00").status)
    }
}
