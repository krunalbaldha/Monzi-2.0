package com.krunal.monzi

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.krunal.monzi.data.AccountBalanceAdjustmentEntity
import com.krunal.monzi.data.AccountEntity
import com.krunal.monzi.data.ApiResult
import com.krunal.monzi.data.AuthSessionManager
import com.krunal.monzi.data.BudgetEntity
import com.krunal.monzi.data.CategoryEntity
import com.krunal.monzi.data.MonziRepository
import com.krunal.monzi.data.SupabaseApiClient
import com.krunal.monzi.data.TransactionEntity
import com.krunal.monzi.data.UserProfile
import com.krunal.monzi.data.UserSession
import com.krunal.monzi.data.engines.PostIngestProcessor
import com.krunal.monzi.util.IstTime
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MonziCrudAndResetTest {

    private lateinit var context: Context
    private lateinit var db: LocalFinanceDbHelper
    private lateinit var server: MockWebServer
    private var online: Boolean = false

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        LocalFinanceDbHelper.resetForTests()
        db = LocalFinanceDbHelper.getInstance(context)
        db.clearAllUserFinanceData()
        AuthSessionManager(context).apply {
            clearSession()
            setPendingCloudReset(false)
            setBudgetAlertsEnabled(true)
        }
        online = false
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        LocalFinanceDbHelper.resetForTests()
        server.shutdown()
    }

    private fun newRepo(): MonziRepository {
        return MonziRepository(
            context = context,
            api = SupabaseApiClient(baseUrlOverride = server.url("/").toString()),
            onlineOverride = { online }
        ).also { repo ->
            repo.sessionMgr.saveSession(
                UserSession(
                    userId = "11111111-1111-4111-8111-111111111111",
                    email = "krunal@monzi.app",
                    accessToken = "valid-jwt",
                    refreshToken = "valid-refresh",
                    expiresAtEpochSec = System.currentTimeMillis() / 1000 + 7200,
                    emailConfirmed = true
                )
            )
            repo.sessionMgr.saveProfile(
                UserProfile(
                    id = "11111111-1111-4111-8111-111111111111",
                    fullName = "Krunal",
                    email = "krunal@monzi.app",
                    deviceSyncToken = "sync-tok-1"
                )
            )
        }
    }

    // ── 1. TRANSACTIONS CRUD + BALANCE RECALCULATION + VALIDATION ─────────────

    @Test
    fun transactions_crud_updates_balances_validates_inputs_and_syncs_offline_queue() = runBlocking {
        val repo = newRepo()
        val accRes = repo.addAccount("HDFC Bank", "4321", "SAVINGS", 10000.0)
        assertTrue(accRes is ApiResult.Success)
        val acc = db.getAccounts().first()
        assertEquals(10000.0, acc.currentBalance, 0.01)

        // Negative cases: invalid amount
        assertTrue(repo.createManualTransaction(0.0, "DEBIT", "Bad", acc.id, null, "UPI", null, System.currentTimeMillis()) is ApiResult.Error)
        assertTrue(repo.createManualTransaction(-50.0, "DEBIT", "Bad", acc.id, null, "UPI", null, System.currentTimeMillis()) is ApiResult.Error)

        // Create DEBIT 1500 -> balance 8500
        val txCreate = repo.createManualTransaction(1500.0, "DEBIT", "Swiggy", acc.id, null, "UPI", "Lunch", System.currentTimeMillis())
        assertTrue(txCreate is ApiResult.Success)
        assertEquals(8500.0, db.getAccountById(acc.id)!!.currentBalance, 0.01)
        val created = db.getAllTransactions().first { it.merchant == "Swiggy" }

        // Update amount (1500 -> 2000) & direction (DEBIT -> CREDIT) -> revert -1500 (+1500 -> 10000), apply +2000 -> 12000
        val txUpdate = repo.updateTransaction(
            txn = created,
            categoryId = created.categoryId,
            notes = "Refund received",
            merchant = "Swiggy Refund",
            transactionType = "REFUND",
            amount = 2000.0,
            direction = "CREDIT"
        )
        assertTrue(txUpdate is ApiResult.Success)
        assertEquals(12000.0, db.getAccountById(acc.id)!!.currentBalance, 0.01)
        val updated = db.getAllTransactions().first { it.localId == created.localId }
        assertEquals("Swiggy Refund", updated.merchant)
        assertEquals(2000.0, updated.amount, 0.01)
        assertEquals("CREDIT", updated.direction)

        // Delete reverts 12000 - 2000 -> 10000
        val txDelete = repo.deleteTransaction(updated)
        assertTrue(txDelete is ApiResult.Success)
        assertEquals(10000.0, db.getAccountById(acc.id)!!.currentBalance, 0.01)
        assertTrue(db.getAllTransactions().none { it.localId == created.localId })

        // Verify persistence after simulated app restart
        LocalFinanceDbHelper.resetForTests()
        val reopenedDb = LocalFinanceDbHelper.getInstance(context)
        assertEquals(10000.0, reopenedDb.getAccountById(acc.id)!!.currentBalance, 0.01)
        assertTrue(reopenedDb.getAllTransactions().none { it.localId == created.localId })
    }

    @Test
    fun transaction_update_and_delete_use_fingerprint_lookup_when_remoteId_null_online() = runBlocking {
        online = true
        val repo = newRepo()
        val cloudTxUuid = "aaaa1111-1111-4111-8111-111111111111"
        val patchedBodies = mutableListOf<String>()
        val deletedUrls = mutableListOf<String>()

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    request.method == "GET" && path.startsWith("/rest/v1/transactions") && path.contains("sms_fingerprint=eq.fp-sms-99") ->
                        MockResponse().setResponseCode(200).setBody("""[{"id":"$cloudTxUuid"}]""")
                    request.method == "PATCH" && path.contains("/rest/v1/transactions?id=eq.$cloudTxUuid") -> {
                        patchedBodies.add(request.body.readUtf8())
                        MockResponse().setResponseCode(204)
                    }
                    request.method == "DELETE" && path.contains("/rest/v1/transactions?id=eq.$cloudTxUuid") -> {
                        deletedUrls.add(path)
                        MockResponse().setResponseCode(204)
                    }
                    else -> MockResponse().setResponseCode(200).setBody("[]")
                }
            }
        }

        val smsTx = TransactionEntity(
            localId = "local-sms-99",
            remoteId = null,
            userId = "11111111-1111-4111-8111-111111111111",
            smsFingerprint = "fp-sms-99",
            amount = 450.0,
            direction = "DEBIT",
            transactionType = "EXPENSE",
            bankName = "HDFC Bank",
            accountMask = "XX4321",
            merchant = "Zomato",
            transactionDateIso = BankSmsParser.toUtcIso(System.currentTimeMillis()),
            transactionDateMs = System.currentTimeMillis(),
            syncStatus = LocalFinanceDbHelper.STATUS_SYNCED
        )
        db.insertLocalTransaction(smsTx)

        val updRes = repo.updateTransaction(smsTx, null, "Dinner", "Zomato Pro", "EXPENSE", 500.0, "DEBIT")
        assertTrue(updRes is ApiResult.Success)
        assertTrue(patchedBodies.isNotEmpty())
        assertEquals(500.0, org.json.JSONObject(patchedBodies.first()).getDouble("amount"), 0.01)
        assertEquals(cloudTxUuid, db.getAllTransactions().first { it.localId == "local-sms-99" }.remoteId)

        val reloaded = db.getAllTransactions().first { it.localId == "local-sms-99" }
        val delRes = repo.deleteTransaction(reloaded)
        assertTrue(delRes is ApiResult.Success)
        assertEquals(1, deletedUrls.size)
    }

    // ── 2. ACCOUNTS & CREDIT CARDS: DUPLICATES, REMAP, STATEMENTS, PAYMENTS ───

    @Test
    fun accounts_and_credit_cards_prevent_duplicates_remap_local_ids_and_persist() = runBlocking {
        val repo = newRepo()

        // Create offline account & verify duplicate check
        assertTrue(repo.addAccount("ICICI Bank", "5678", "SAVINGS", 25000.0) is ApiResult.Success)
        val dupBank = repo.addAccount("ICICI Bank", "5678", "SAVINGS", 1000.0)
        assertTrue("Expected duplicate account error", dupBank is ApiResult.Error)

        val icici = db.getAccounts().first { it.bankName == "ICICI Bank" }
        assertTrue(icici.id.startsWith("local-acc-"))

        // Validation: reject short digits or blank name
        assertTrue(repo.updateAccount(icici, "", null, "5678", "SAVINGS", 25000.0) is ApiResult.Error)
        assertTrue(repo.updateAccount(icici, "ICICI Bank", null, "12", "SAVINGS", 25000.0) is ApiResult.Error)
        assertTrue(repo.updateAccount(icici, "ICICI Bank", "Salary Account", "5678", "SAVINGS", 28000.0) is ApiResult.Success)
        assertEquals(28000.0, db.getAccountById(icici.id)!!.currentBalance, 0.01)

        // Create offline Credit Card & attach a transaction to its local-acc id
        assertTrue(repo.addOrUpdateCreditCard(null, "Axis Bank", "Magnus", "9900", 300000.0, 4000.0, 12, 2) is ApiResult.Success)
        val localCard = db.getAccounts().first { it.accountType == "CREDIT_CARD" }
        assertTrue(localCard.id.startsWith("local-acc-"))
        repo.createManualTransaction(1200.0, "DEBIT", "Amazon", localCard.id, null, "CARD", null, System.currentTimeMillis())

        // Duplicate card should be rejected
        assertTrue(repo.addOrUpdateCreditCard(null, "Axis Bank", "Dup", "9900", 100000.0, 0.0, 12, 2) is ApiResult.Error)

        // Come online & sync: mock cloud assigning real UUIDs to both accounts & replacing local-acc-* cleanly (NO duplicates)
        online = true
        val iciciCloudId = "22222222-2222-4222-8222-222222222222"
        val cardCloudId = "33333333-3333-4333-8333-333333333333"

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    request.method == "POST" && path.contains("/rest/v1/accounts") -> {
                        val body = request.body.readUtf8()
                        val cloudId = if (body.contains("CREDIT_CARD")) cardCloudId else iciciCloudId
                        val accType = if (body.contains("CREDIT_CARD")) "CREDIT_CARD" else "SAVINGS"
                        val bank = if (body.contains("CREDIT_CARD")) "Axis Bank" else "ICICI Bank"
                        val mask = if (body.contains("CREDIT_CARD")) "XX9900" else "XX5678"
                        val row = """{"id":"$cloudId","user_id":"11111111-1111-4111-8111-111111111111","bank_name":"$bank","account_number_mask":"$mask","account_type":"$accType","current_balance":0,"opening_balance":0,"is_active":true}"""
                        MockResponse().setResponseCode(201).setBody("[$row]")
                    }
                    request.method == "POST" && path.contains("/rest/v1/transactions") -> {
                        val body = request.body.readUtf8()
                        assertFalse("Transaction payload must not contain local-acc-* after remap!", body.contains("local-acc-"))
                        MockResponse().setResponseCode(201).setBody("""[{"id":"44444444-4444-4444-8444-444444444444"}]""")
                    }
                    request.method == "GET" && path.contains("/rest/v1/accounts") ->
                        MockResponse().setResponseCode(200).setBody(
                            """[{"id":"$iciciCloudId","user_id":"11111111-1111-4111-8111-111111111111","bank_name":"ICICI Bank","account_name":"Salary Account","account_number_mask":"XX5678","account_type":"SAVINGS","current_balance":28000.0,"opening_balance":28000.0,"is_active":true},{"id":"$cardCloudId","user_id":"11111111-1111-4111-8111-111111111111","bank_name":"Axis Bank","account_name":"Magnus","account_number_mask":"XX9900","account_type":"CREDIT_CARD","current_balance":5200.0,"opening_balance":4000.0,"credit_limit":300000.0,"is_active":true}]"""
                        )
                    else -> MockResponse().setResponseCode(200).setBody("[]")
                }
            }
        }

        val syncRes = repo.syncAllFromCloud()
        assertTrue(syncRes is ApiResult.Success)
        val allAccs = db.getAccounts()
        assertEquals("Expected zero duplicate accounts after local->UUID remap", 2, allAccs.size)
        assertNotNull(db.getAccountById(cardCloudId))
        assertNull(db.getAccountById(localCard.id))

        // Verify credit card statement upsert uses real UUID and rejects invalid minDue > totalDue
        val syncedCard = db.getAccountById(cardCloudId)!!
        assertTrue(repo.upsertCreditCardStatement(syncedCard, 3000.0, 5000.0, null) is ApiResult.Error)
        online = false
        assertTrue(repo.upsertCreditCardStatement(syncedCard, 5200.0, 500.0, null) is ApiResult.Success)
        val stmt = db.getAllStatements().first { it.accountId == cardCloudId }
        assertFalse("Statement ID must be a UUID rather than stmt-*", stmt.id.startsWith("stmt-"))
        assertTrue(runCatching { UUID.fromString(stmt.id) }.isSuccess)
    }

    // ── 3. CATEGORIES, AUTO-CATEGORIZATION RULES & BUDGETS CRUD ───────────────

    @Test
    fun categories_rules_and_budgets_crud_enforce_validations_and_alerts_pref() = runBlocking {
        val repo = newRepo()

        // Create Category & reject duplicate
        val catRes = repo.createCategory("Pet Care", "#FF6B6B", isIncome = false)
        assertTrue(catRes is ApiResult.Success)
        assertTrue(repo.createCategory("pet care", "#FF6B6B", isIncome = false) is ApiResult.Error)
        val customCat = db.getCategories().first { it.name == "Pet Care" }

        // Update Category
        assertTrue(repo.updateCategory(customCat, "Pets & Vet", "#059669", false) is ApiResult.Success)
        assertEquals("Pets & Vet", db.getCategories().first { it.id == customCat.id }.name)

        // Uncategorized cannot be deleted
        val uncat = db.getCategories().firstOrNull { it.name.equals("Uncategorized", ignoreCase = true) }
        if (uncat != null) {
            assertTrue(repo.deleteCategory(uncat) is ApiResult.Error)
        }

        // Create Rule, reject duplicate keyword, edit & toggle rule
        assertTrue(repo.createRule("headsupfortails", customCat.id, 10) is ApiResult.Success)
        assertTrue(repo.createRule("HEADSUPFORTAILS", customCat.id, 10) is ApiResult.Error)
        val rule = db.getRules().first { it.matchingRule == "headsupfortails" }
        assertTrue(repo.updateRule(rule, "petshop", customCat.id, 25) is ApiResult.Success)
        val updatedRule = db.getRules().first { it.id == rule.id }
        assertEquals("petshop", updatedRule.matchingRule)
        assertEquals(25, updatedRule.priority)
        assertTrue(repo.toggleRule(updatedRule, false) is ApiResult.Success)
        assertFalse(db.getRules().first { it.id == rule.id }.enabled)

        // Budgets CRUD + duplicate guard + alerts preference toggle
        assertTrue(repo.saveBudget(null, "Pet Budget", customCat.id, 5000.0, 80, false) is ApiResult.Success)
        assertTrue("Duplicate active budget for same category+month must fail", repo.saveBudget(null, "Another", customCat.id, 6000.0, 80, false) is ApiResult.Error)
        val budget = db.getAllBudgets().first { it.categoryId == customCat.id }
        assertTrue(repo.saveBudget(budget.id, "Pet Budget Updated", customCat.id, 7500.0, 90, false) is ApiResult.Success)
        assertEquals(7500.0, db.getAllBudgets().first { it.id == budget.id }.monthlyLimit, 0.01)

        // Budget Alerts toggle honored by PostIngestProcessor
        repo.sessionMgr.setBudgetAlertsEnabled(false)
        assertFalse(repo.sessionMgr.isBudgetAlertsEnabled())
        val alertTx = TransactionEntity(
            localId = "tx-pet-1",
            userId = repo.sessionMgr.getUserId(),
            categoryId = customCat.id,
            categoryName = "Pets & Vet",
            smsFingerprint = "fp-pet-1",
            amount = 7400.0,
            direction = "DEBIT",
            transactionType = "EXPENSE",
            merchant = "Vet Clinic",
            transactionDateIso = BankSmsParser.toUtcIso(System.currentTimeMillis()),
            transactionDateMs = System.currentTimeMillis(),
            syncStatus = LocalFinanceDbHelper.STATUS_SYNCED
        )
        db.insertLocalTransaction(alertTx)
        PostIngestProcessor.processTransaction(context, db, alertTx, repo.sessionMgr.getUserId())
        assertTrue("Budget alert should not be created when disabled", db.getAllBudgetAlerts().isEmpty())

        // Delete budget, rule, category
        assertTrue(repo.deleteBudget(budget.id) is ApiResult.Success)
        assertTrue(db.getAllBudgets().none { it.id == budget.id })
        assertTrue(repo.deleteRule(updatedRule) is ApiResult.Success)
        assertTrue(db.getRules().none { it.id == rule.id })
        assertTrue(repo.deleteCategory(db.getCategories().first { it.id == customCat.id }) is ApiResult.Success)
        assertTrue(db.getCategories().none { it.id == customCat.id })
    }

    // ── 4. RESET ALL FINANCIAL DATA: ONLINE, HTTP ERROR, OFFLINE DEFERRED ─────

    @Test
    fun resetAllData_deletes_all_cloud_tables_and_local_db_when_online() = runBlocking {
        online = true
        val repo = newRepo()
        val deletedPaths = mutableListOf<String>()
        var clearedSelfLinks = false

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    request.method == "PATCH" && path.contains("/rest/v1/transactions") && request.body.readUtf8().contains("linked_transaction_id") -> {
                        clearedSelfLinks = true
                        MockResponse().setResponseCode(204)
                    }
                    request.method == "DELETE" && path.startsWith("/rest/v1/") -> {
                        deletedPaths.add(path.substringBefore("?"))
                        MockResponse().setResponseCode(204)
                    }
                    else -> MockResponse().setResponseCode(200).setBody("[]")
                }
            }
        }

        // Seed rich financial data locally
        db.upsertAccountLocal(AccountEntity("acc-1", "u1", "SBI", "XX1111", "SAVINGS", currentBalance = 50000.0))
        db.insertLocalTransaction(
            TransactionEntity(
                localId = "tx-1", smsFingerprint = "fp-1", amount = 500.0,
                direction = "DEBIT", transactionType = "EXPENSE",
                transactionDateIso = BankSmsParser.toUtcIso(System.currentTimeMillis()),
                transactionDateMs = System.currentTimeMillis()
            )
        )
        db.upsertBudget(BudgetEntity("b-1", "u1", "Food", null, null, null, 4000.0, IstTime.currentMonthKey()))
        db.insertAdjustment(AccountBalanceAdjustmentEntity("adj-1", "u1", "acc-1", 100.0, "Fix"))
        db.upsertCategory(CategoryEntity("c-custom", "CustomCat", "Tag", "#112233", isIncome = false))

        val res = repo.resetAllData()
        assertTrue(res is ApiResult.Success)
        assertTrue("Self-FK linked_transaction_id must be cleared before deleting transactions", clearedSelfLinks)
        assertTrue(deletedPaths.contains("/rest/v1/transactions"))
        assertTrue(deletedPaths.contains("/rest/v1/accounts"))
        assertTrue(deletedPaths.contains("/rest/v1/transaction_rules"))
        assertTrue(deletedPaths.contains("/rest/v1/sync_queue"))
        assertTrue(deletedPaths.contains("/rest/v1/budgets"))
        assertTrue(deletedPaths.contains("/rest/v1/budget_alerts"))
        assertTrue(db.getAccounts().isEmpty())
        assertTrue(db.getAllTransactions().isEmpty())
        assertTrue(db.getAllBudgets().isEmpty())
        assertTrue(db.getAllAdjustments().isEmpty())
        assertFalse(repo.sessionMgr.isPendingCloudReset())
    }

    @Test
    fun resetAllData_surfaces_error_without_wiping_local_db_when_cloud_fails() = runBlocking {
        online = true
        val repo = newRepo()

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return if (request.method == "DELETE" && path.startsWith("/rest/v1/transactions")) {
                    MockResponse().setResponseCode(500).setBody("""{"message":"FK constraint error"}""")
                } else {
                    MockResponse().setResponseCode(204)
                }
            }
        }

        db.upsertAccountLocal(AccountEntity("acc-keep", "u1", "HDFC Bank", "XX9876", "SAVINGS", currentBalance = 12345.0))
        val res = repo.resetAllData()
        assertTrue(res is ApiResult.Error)
        assertTrue((res as ApiResult.Error).message.contains("FK constraint error") || res.code == 500)
        // Local data must NOT be silently destroyed if online cloud reset failed
        assertEquals(1, db.getAccounts().size)
    }

    @Test
    fun offline_resetAllData_sets_pendingCloudReset_and_wipes_cloud_before_next_sync_pull() = runBlocking {
        online = false
        val repo = newRepo()
        db.upsertAccountLocal(AccountEntity("acc-off", "u1", "Kotak", "XX3333", "SAVINGS", currentBalance = 9000.0))

        val offlineReset = repo.resetAllData()
        assertTrue(offlineReset is ApiResult.Success)
        assertTrue(db.getAccounts().isEmpty())
        assertTrue(repo.sessionMgr.isPendingCloudReset())

        // Come back online and trigger syncAllFromCloud -> MUST execute cloud reset before fetching
        online = true
        val callOrder = mutableListOf<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                if (request.method == "DELETE" && path.startsWith("/rest/v1/transactions")) {
                    callOrder.add("DELETE_TX")
                    return MockResponse().setResponseCode(204)
                }
                if (request.method == "GET" && path.startsWith("/rest/v1/transactions")) {
                    callOrder.add("GET_TX")
                    return MockResponse().setResponseCode(200).setBody("[]")
                }
                return if (request.method == "GET") MockResponse().setResponseCode(200).setBody("[]")
                else MockResponse().setResponseCode(204)
            }
        }

        val syncAfterReset = repo.syncAllFromCloud()
        assertTrue(syncAfterReset is ApiResult.Success)
        assertFalse(repo.sessionMgr.isPendingCloudReset())
        val delIdx = callOrder.indexOf("DELETE_TX")
        val getIdx = callOrder.indexOf("GET_TX")
        assertTrue("Cloud reset DELETE must run before cloud GET pull", delIdx >= 0 && getIdx > delIdx)
    }

    // ── 5. PROFILE NAME VALIDATION & UPDATE ───────────────────────────────────

    @Test
    fun updateProfileName_validates_blank_and_persists_valid_name() = runBlocking {
        online = false
        val repo = newRepo()
        assertTrue(repo.updateProfileName("   ") is ApiResult.Error)
        assertTrue(repo.updateProfileName("Krunal Baldha") is ApiResult.Success)
        assertEquals("Krunal Baldha", repo.sessionMgr.getCachedProfile()?.fullName)
    }
}
