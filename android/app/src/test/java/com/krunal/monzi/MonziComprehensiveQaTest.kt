package com.krunal.monzi

import androidx.test.core.app.ApplicationProvider
import com.krunal.monzi.data.AccountBalanceAdjustmentEntity
import com.krunal.monzi.data.AccountBalanceSnapshotEntity
import com.krunal.monzi.data.AccountEntity
import com.krunal.monzi.data.AnalyticsPeriod
import com.krunal.monzi.data.BudgetEntity
import com.krunal.monzi.data.FinanceAnalyticsEngine
import com.krunal.monzi.data.RefundRecordEntity
import com.krunal.monzi.data.TransactionEntity
import com.krunal.monzi.data.engines.BudgetAndCardEngine
import com.krunal.monzi.data.engines.DuplicateDetector
import com.krunal.monzi.data.engines.ReconciliationEngine
import com.krunal.monzi.data.engines.RefundDetector
import com.krunal.monzi.data.engines.TransferDetector
import com.krunal.monzi.util.IstTime
import com.krunal.monzi.util.MoneyMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class MonziComprehensiveQaTest {

    private val nowMs = IstTime.monthRangeMillis("2026-11").first + 10L * 86_400_000L + 12L * 3600_000L

    private fun makeTx(
        id: String = UUID.randomUUID().toString(),
        accountId: String? = "acc-hdfc",
        amount: Double,
        direction: String,
        type: String,
        merchant: String = "Merchant",
        categoryName: String = "Shopping",
        categoryId: String? = null,
        dateMs: Long = nowMs,
        bankName: String = "HDFC Bank",
        mask: String = "XX1234",
        isSelfTransfer: Boolean = type == "TRANSFER",
        isRefund: Boolean = type == "REFUND" || type == "REVERSAL",
        isExcluded: Boolean = type == "TRANSFER" || type == "CC_BILL_PAYMENT",
        linkedId: String? = null,
        status: String = "COMPLETED",
        dupStatus: String = "UNIQUE",
        refId: String? = null
    ) = TransactionEntity(
        localId = id,
        userId = "user-qa",
        accountId = accountId,
        categoryId = categoryId,
        smsFingerprint = "fp-$id",
        amount = amount,
        direction = direction,
        transactionType = type,
        bankName = bankName,
        accountMask = mask,
        merchant = merchant,
        referenceId = refId,
        paymentMode = "UPI",
        isSelfTransfer = isSelfTransfer,
        isRefund = isRefund,
        isExcludedFromTotals = isExcluded,
        linkedTransactionId = linkedId,
        transactionStatus = status,
        duplicateStatus = dupStatus,
        transactionDateIso = BankSmsParser.toUtcIso(dateMs),
        transactionDateMs = dateMs,
        categoryName = categoryName
    )

    @Test
    fun scenario01_criticalNeftSalary49800AndExpenses32000YieldsNetSavings17800() {
        val neftSms = "HDFC Bank: Rs. 49,800.00 credited to your A/c XX1234 via NEFT from ACME SOFTWARE PVT LTD Ref NEFT123456 on 11-Nov-26"
        val parsedSalary = BankSmsParser.inspect("VM-HDFCBK", neftSms, nowMs)
        assertEquals("VALID", parsedSalary.status)
        val salary = parsedSalary.parsed!!
        assertEquals(49800.0, salary.amount, 0.01)
        assertEquals("CREDIT", salary.direction)
        assertEquals("INCOME", salary.transactionType)
        assertFalse("NEFT salary from employer must NOT be misclassified as self-transfer", salary.isSelfTransfer)
        assertFalse("NEFT salary must NOT be excluded from totals", salary.isExcludedFromTotals)

        val hdfcAcc = AccountEntity("acc-hdfc", "user-qa", "HDFC Bank", "XX1234", "SAVINGS", currentBalance = 17800.0)
        val txSalary = makeTx(
            id = "tx-sal",
            amount = salary.amount,
            direction = salary.direction,
            type = salary.transactionType,
            merchant = salary.merchant ?: "ACME SOFTWARE",
            categoryName = salary.transactionCategory,
            isSelfTransfer = salary.isSelfTransfer,
            isExcluded = salary.isExcludedFromTotals
        )
        val txExp1 = makeTx(id = "tx-rent", amount = 22000.0, direction = "DEBIT", type = "EXPENSE", merchant = "Landlord", categoryName = "Rent & Housing")
        val txExp2 = makeTx(id = "tx-food", amount = 10000.0, direction = "DEBIT", type = "EXPENSE", merchant = "Swiggy", categoryName = "Food & Dining")

        val dash = FinanceAnalyticsEngine.computeDashboardStats(listOf(hdfcAcc), listOf(txSalary, txExp1, txExp2), nowMs)
        val period = FinanceAnalyticsEngine.computePeriodAnalytics(AnalyticsPeriod.THIS_MONTH, listOf(hdfcAcc), listOf(txSalary, txExp1, txExp2), nowMs = nowMs)

        assertEquals(49800.0, dash.monthlyIncome, 0.01)
        assertEquals(32000.0, dash.monthlyExpense, 0.01)
        assertEquals(17800.0, dash.netSavings, 0.01)
        assertEquals(dash.monthlyIncome, period.totalIncome, 0.01)
        assertEquals(dash.monthlyExpense, period.totalExpense, 0.01)
        assertEquals(17800.0, period.netSavings, 0.01)
    }

    @Test
    fun scenario02And03_upiOutgoingCreditedToVpaStaysDebitWhileAddedToWalletIsCredit() {
        val outgoing = BankSmsParser.inspect(
            "HDFC",
            "HDFC Bank: A/c XX1234 debited by Rs. 1,500.00 on 11-Nov-26 and credited to VPA friend@okicici Ref 987654",
            nowMs
        )
        assertEquals("VALID", outgoing.status)
        assertEquals("DEBIT", outgoing.parsed!!.direction)

        val walletIn = BankSmsParser.inspect(
            "PAYTM",
            "Rs. 500.00 added to your wallet A/c XX9900 via UPI",
            nowMs
        )
        assertEquals("VALID", walletIn.status)
        assertEquals("CREDIT", walletIn.parsed!!.direction)
    }

    @Test
    fun scenario04To06_internalSelfTransfersExcludedFromIncomeAndExpensesAcrossBankAccounts() {
        val hdfc = AccountEntity("acc-1", "user-qa", "HDFC Bank", "XX1111", "SAVINGS", openingBalance = 50000.0, currentBalance = 40000.0)
        val sbi = AccountEntity("acc-2", "user-qa", "SBI", "XX2222", "SAVINGS", openingBalance = 5000.0, currentBalance = 15000.0)

        val debitOut = makeTx(id = "t-out", accountId = hdfc.id, amount = 10000.0, direction = "DEBIT", type = "EXPENSE", isSelfTransfer = false, isExcluded = false, dateMs = nowMs)
        val creditIn = makeTx(id = "t-in", accountId = sbi.id, amount = 10000.0, direction = "CREDIT", type = "INCOME", isSelfTransfer = false, isExcluded = false, dateMs = nowMs + 60_000L, bankName = "SBI", mask = "XX2222")

        val transferRec = TransferDetector.detectTransfer(
            candidate = creditIn,
            existingTxns = listOf(debitOut),
            accounts = listOf(hdfc, sbi),
            userId = "user-qa"
        )
        assertNotNull(transferRec)

        val txOutConfirmed = debitOut.copy(transactionType = "TRANSFER", isSelfTransfer = true, isExcludedFromTotals = true)
        val txInConfirmed = creditIn.copy(transactionType = "TRANSFER", isSelfTransfer = true, isExcludedFromTotals = true)

        val dash = FinanceAnalyticsEngine.computeDashboardStats(listOf(hdfc, sbi), listOf(txOutConfirmed, txInConfirmed), nowMs)
        assertEquals(0.0, dash.monthlyIncome, 0.01)
        assertEquals(0.0, dash.monthlyExpense, 0.01)
        assertEquals(0.0, dash.netSavings, 0.01)
        assertEquals(55000.0, dash.totalAssets, 0.01)
    }

    @Test
    fun scenario07And08_refundsReduceNetExpenseAndLinkedCategoryBreakdown() {
        val acc = AccountEntity("acc-1", "user-qa", "HDFC Bank", "XX1111", "SAVINGS", currentBalance = 20000.0)
        val shopSpend = makeTx(id = "orig-shop", amount = 5000.0, direction = "DEBIT", type = "EXPENSE", merchant = "Amazon", categoryName = "Shopping", dateMs = nowMs)
        val foodSpend = makeTx(id = "orig-food", amount = 1200.0, direction = "DEBIT", type = "EXPENSE", merchant = "Swiggy", categoryName = "Food & Dining", dateMs = nowMs)
        val partialRefund = makeTx(
            id = "ref-shop",
            amount = 2000.0,
            direction = "CREDIT",
            type = "REFUND",
            merchant = "Amazon",
            categoryName = "Refund",
            linkedId = "orig-shop",
            dateMs = nowMs + 3600_000L
        )
        val match = RefundDetector.detectRefund(partialRefund, listOf(shopSpend, foodSpend), "user-qa")
        assertNotNull(match)
        assertEquals("PARTIAL_REFUND", match!!.refundRecord.refundType)

        val dash = FinanceAnalyticsEngine.computeDashboardStats(listOf(acc), listOf(shopSpend, foodSpend, partialRefund), nowMs)
        assertEquals(0.0, dash.monthlyIncome, 0.01)
        assertEquals(4200.0, dash.monthlyExpense, 0.01)
        assertEquals(2000.0, dash.refundsTotal, 0.01)

        val shopCat = dash.categoryBreakdown.first { it.name == "Shopping" }
        assertEquals(3000.0, shopCat.amount, 0.01)
        assertEquals(4200.0, MoneyMath.sum(dash.categoryBreakdown.map { it.amount }), 0.01)
    }

    @Test
    fun scenario09_cashbackTrackedSeparatelyWithoutDistortingCoreIncome() {
        val acc = AccountEntity("acc-1", "user-qa", "HDFC Bank", "XX1111", "SAVINGS", currentBalance = 10200.0)
        val salary = makeTx(amount = 50000.0, direction = "CREDIT", type = "INCOME", merchant = "Employer", categoryName = "Salary")
        val cashback = makeTx(amount = 200.0, direction = "CREDIT", type = "INCOME", merchant = "Amazon Cashback", categoryName = "Cashback")

        val dash = FinanceAnalyticsEngine.computeDashboardStats(listOf(acc), listOf(salary, cashback), nowMs)
        assertEquals(50000.0, dash.monthlyIncome, 0.01)
        assertEquals(200.0, dash.cashbackTotal, 0.01)
        assertEquals(50000.0, dash.today.income, 0.01)
        assertEquals(50000.0, dash.cashFlow6Months.last().income, 0.01)
    }

    @Test
    fun scenario10And11_creditCardSpendAndBillPaymentNeverDoubleCountAndSurviveRecalculationDescOrder() {
        val bank = AccountEntity("bank-1", "user-qa", "HDFC Bank", "XX1111", "SAVINGS", openingBalance = 50000.0, currentBalance = 42000.0)
        val card = AccountEntity("cc-1", "user-qa", "ICICI Bank", "XX9090", "CREDIT_CARD", openingBalance = 0.0, currentBalance = 0.0, creditLimit = 100000.0)

        val ccPurchase = makeTx(id = "cc-spend", accountId = card.id, amount = 8000.0, direction = "DEBIT", type = "CC_PURCHASE", merchant = "Croma", categoryName = "Shopping", dateMs = nowMs - 86_400_000L, bankName = "ICICI Bank", mask = "XX9090")
        val bankPayLeg = makeTx(id = "cc-pay-bank", accountId = bank.id, amount = 8000.0, direction = "DEBIT", type = "CC_BILL_PAYMENT", merchant = "ICICI Bill Pay", isExcluded = true, isSelfTransfer = true, dateMs = nowMs)
        val cardPayLeg = makeTx(id = "cc-pay-card", accountId = card.id, amount = 8000.0, direction = "CREDIT", type = "CC_BILL_PAYMENT", merchant = "ICICI Payment Received", isExcluded = true, isSelfTransfer = true, linkedId = bankPayLeg.localId, dateMs = nowMs)

        // Note: newest-first DESC order like SQLite getAllTransactions()
        val descTxns = listOf(cardPayLeg, bankPayLeg, ccPurchase)
        val bankSummary = ReconciliationEngine.reconcileAccountLedger(bank, descTxns, emptyList(), emptyList())
        val cardSummary = ReconciliationEngine.reconcileAccountLedger(card, descTxns, emptyList(), emptyList())

        assertEquals(42000.0, bankSummary.calculatedBalance, 0.01)
        assertEquals(0.0, cardSummary.calculatedBalance, 0.01)
        assertEquals(100000.0, cardSummary.availableCredit ?: 0.0, 0.01)

        val dash = FinanceAnalyticsEngine.computeDashboardStats(listOf(bank, card), descTxns, nowMs)
        assertEquals(8000.0, dash.monthlyExpense, 0.01)
    }

    @Test
    fun scenario12And13And14_bankReportedSnapshotPreservedAndManualEditClearsTransferExclusion() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = LocalFinanceDbHelper.getInstance(ctx)
        db.clearAllUserFinanceData()

        val acc = AccountEntity("acc-snap", "user-qa", "SBI", "XX4321", "SAVINGS", openingBalance = 0.0, currentBalance = 43788.0, balanceSource = "BANK_REPORTED")
        db.upsertAccountLocal(acc)
        val snapTx = makeTx(id = "snap-tx", accountId = acc.id, amount = 500.0, direction = "CREDIT", type = "TRANSFER", isSelfTransfer = true, isExcluded = true, dateMs = nowMs - 3600_000L)
        val afterTx = makeTx(id = "after-tx", accountId = acc.id, amount = 200.0, direction = "DEBIT", type = "EXPENSE", dateMs = nowMs)
        db.insertLocalTransaction(snapTx)
        db.insertLocalTransaction(afterTx)
        db.insertSnapshot(AccountBalanceSnapshotEntity("s1", "user-qa", acc.id, 43988.0, 500.0, 43488.0, snapTx.localId, snapTx.transactionDateMs))

        ReconciliationEngine.recalculateAllAccountBalances(db)
        val reloadedAcc = db.getAccountById(acc.id)!!
        assertEquals(43788.0, reloadedAcc.currentBalance, 0.01)

        // Manual edit changing TRANSFER -> INCOME must clear is_self_transfer and is_excluded_from_totals
        db.updateTransactionLocal(
            localId = snapTx.localId,
            categoryId = "local-cat-salary",
            categoryName = "Salary",
            categoryColor = "#10B981",
            notes = "Corrected bonus",
            merchant = "Employer Bonus",
            transactionType = "INCOME",
            amount = 1500.0,
            direction = "CREDIT"
        )
        val updatedTx = db.getAllTransactions().first { it.localId == snapTx.localId }
        assertEquals("INCOME", updatedTx.transactionType)
        assertFalse(updatedTx.isSelfTransfer)
        assertFalse(updatedTx.isExcludedFromTotals)
        assertEquals(1500.0, updatedTx.amount, 0.01)

        val dash = FinanceAnalyticsEngine.computeDashboardStats(listOf(reloadedAcc), db.getAllTransactions(), nowMs)
        assertEquals(1500.0, dash.monthlyIncome, 0.01)
        assertEquals(200.0, dash.monthlyExpense, 0.01)
        assertEquals(1300.0, dash.netSavings, 0.01)
        db.clearAllUserFinanceData()
    }

    @Test
    fun scenario15To19_duplicatesFailedNegativeBoundaryAndPostgresMicrosecondsTimestamps() {
        // Postgres microsecond TIMESTAMPTZ parsing preserves exact IST calendar day (2026-11-25 18:25Z == 23:55 IST Nov 25)
        val parsedMs = BankSmsParser.parseIsoToMillis("2026-11-25 18:25:00.123456+00")
        assertEquals("2026-11-25", IstTime.toDateKey(parsedMs))

        // Failed & Promotional & Negative amount rejections
        assertEquals("FAILED", BankSmsParser.inspect("HDFC", "Payment of Rs. 2,000 failed due to insufficient balance", nowMs).status)
        assertEquals("IGNORED", BankSmsParser.inspect("HDFC", "Congratulations! Pre-approved loan up to Rs. 5,00,000 apply now", nowMs).status)
        assertEquals("IGNORED", BankSmsParser.inspect("HDFC", "A/c XX1234 debited by Rs. -500", nowMs).status)
        assertEquals("IGNORED", BankSmsParser.inspect("HDFC", "", nowMs).status)

        // Exact duplicate detection
        val tx1 = makeTx(id = "d1", amount = 450.0, direction = "DEBIT", type = "EXPENSE", refId = "UTR9988")
        val dupCandidate = tx1.copy(localId = "d2")
        val dup = DuplicateDetector.evaluateDuplicate(
            candidate = dupCandidate,
            existingTxns = listOf(tx1)
        )
        assertEquals("EXACT_DUPLICATE", dup.status)
    }

    @Test
    fun scenario20_budgetThresholdsEvaluateCorrectlyAfterRefunds() {
        val budget = BudgetEntity(
            id = "b-food",
            userId = "user-qa",
            name = "Food Budget",
            categoryId = "cat-food",
            categoryName = "Food & Dining",
            monthlyLimit = 5000.0,
            startMonth = "2026-11",
            alertThreshold = 80
        )
        val t1 = makeTx(id = "f1", amount = 4500.0, direction = "DEBIT", type = "EXPENSE", categoryId = "cat-food", categoryName = "Food & Dining")
        val ref = makeTx(id = "f-ref", amount = 1000.0, direction = "CREDIT", type = "REFUND", categoryId = "cat-food", categoryName = "Food & Dining", linkedId = "f1")
        val res = BudgetAndCardEngine.evaluateBudgets(
            budgets = listOf(budget),
            transactions = listOf(t1, ref),
            refunds = listOf(RefundRecordEntity("r1", "user-qa", ref.localId, t1.localId, "PARTIAL_REFUND", 1000.0, "MATCHED_TO_ORIGINAL")),
            existingAlerts = emptyList(),
            monthKey = "2026-11",
            userId = "user-qa",
            nowMs = nowMs
        )
        val summary = res.summaries.first()
        assertEquals(4500.0, summary.grossSpent, 0.01)
        assertEquals(1000.0, summary.refundedAmount, 0.01)
        assertEquals(3500.0, summary.netSpent, 0.01)
        assertEquals("APPROACHING", summary.healthStatus)
        assertNull(res.triggeredAlerts.firstOrNull { it.thresholdPct >= 80 })
    }
}
