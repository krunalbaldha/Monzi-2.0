package com.krunal.monzi

import com.krunal.monzi.data.AccountBalanceAdjustmentEntity
import com.krunal.monzi.data.AccountBalanceSnapshotEntity
import com.krunal.monzi.data.AccountEntity
import com.krunal.monzi.data.BudgetEntity
import com.krunal.monzi.data.CreditCardPaymentEntity
import com.krunal.monzi.data.CreditCardStatementEntity
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Monzi21EnginesTest {

    @Test
    fun `money math maintains decimal accuracy`() {
        assertEquals(0.30, MoneyMath.add(0.10, 0.20), 0.0001)
        assertEquals(1234.56, MoneyMath.subtract(2000.00, 765.44), 0.0001)
        assertEquals(66.7, MoneyMath.percentage(200.0, 300.0), 0.01)
    }

    @Test
    fun `parser extracts confidence score and credit card statement sms`() {
        val sms = "Dear Customer, Rs. 450.50 spent on HDFC Credit Card XX8812 at Zomato on 20-11-26. Avl Lmt Rs. 95,000.00 Ref 1234567890"
        val res = BankSmsParser.inspect("VM-HDFCBK", sms)
        assertEquals("VALID", res.status)
        val parsed = res.parsed!!
        assertEquals("CC_PURCHASE", parsed.transactionType)
        assertTrue(parsed.confidenceScore >= 0.85)
        assertTrue(parsed.isCardTransaction)

        val stmtSms = "HDFC Bank Credit Card XX8812 Statement: Total Amount Due Rs. 14,250.00, Min Amt Due Rs. 750.00. Due Date 05/12/2026."
        val stmtRes = BankSmsParser.inspect("VM-HDFCBK", stmtSms)
        assertEquals("STATEMENT", stmtRes.status)
        assertNotNull(stmtRes.statement)
        assertEquals(14250.0, stmtRes.statement!!.totalDue, 0.01)
        assertEquals(750.0, stmtRes.statement!!.minimumDue, 0.01)
        assertEquals("XX8812", stmtRes.statement!!.cardMask)
    }

    @Test
    fun `duplicate detector identifies exact and possible duplicates`() {
        val now = System.currentTimeMillis()
        val base = TransactionEntity(
            localId = "t1",
            accountId = "acc-1",
            smsFingerprint = "fp-1",
            amount = 299.0,
            direction = "DEBIT",
            transactionType = "EXPENSE",
            bankName = "HDFC Bank",
            accountMask = "XX1111",
            merchant = "Swiggy",
            referenceId = "UTR123456",
            transactionDateIso = BankSmsParser.toUtcIso(now),
            transactionDateMs = now
        )
        val exactSameRef = base.copy(localId = "t2", smsFingerprint = "fp-2")
        assertEquals("EXACT_DUPLICATE", DuplicateDetector.evaluateDuplicate(exactSameRef, listOf(base)).status)

        val possibleSameMerchantWindow = base.copy(
            localId = "t3",
            smsFingerprint = "fp-3",
            referenceId = null,
            merchant = "Swiggy India",
            transactionDateMs = now + 3 * 60_000L
        )
        assertEquals("POSSIBLE_DUPLICATE", DuplicateDetector.evaluateDuplicate(possibleSameMerchantWindow, listOf(base)).status)
    }

    @Test
    fun `reconciliation engine detects snapshot discrepancy without altering history`() {
        val acc = AccountEntity(
            id = "acc-1",
            userId = "u1",
            bankName = "HDFC Bank",
            accountNumberMask = "XX1234",
            openingBalance = 10000.0,
            currentBalance = 9500.0
        )
        val txns = listOf(
            TransactionEntity(
                localId = "t1",
                accountId = "acc-1",
                smsFingerprint = "fp1",
                amount = 500.0,
                direction = "DEBIT",
                transactionType = "EXPENSE",
                transactionDateIso = "2026-11-10T10:00:00Z",
                transactionDateMs = 1000L
            )
        )
        val snap = AccountBalanceSnapshotEntity(
            id = "s1",
            userId = "u1",
            accountId = "acc-1",
            reportedBalance = 9200.0,
            calculatedBalanceAtSnapshot = 9500.0,
            discrepancyAmount = -300.0,
            snapshotAtMs = 1500L
        )
        val summary = ReconciliationEngine.reconcileAccountLedger(acc, txns, emptyList(), listOf(snap))
        assertEquals(9500.0, summary.calculatedBalance, 0.01)
        assertEquals(-300.0, summary.discrepancyAmount, 0.01)
        assertEquals("DISCREPANCY_DETECTED", summary.reconciliationStatus)

        // Adding approved adjustment reconciles cleanly
        val adj = AccountBalanceAdjustmentEntity("a1", "u1", "acc-1", -300.0, "Missing SMS fee", approved = true)
        val reconciled = ReconciliationEngine.reconcileAccountLedger(acc, txns, listOf(adj), listOf(snap))
        assertEquals(9200.0, reconciled.calculatedBalance, 0.01)
        assertEquals("RECONCILED", reconciled.reconciliationStatus)
    }

    @Test
    fun `transfer detector matches own account transfer across two accounts`() {
        val now = System.currentTimeMillis()
        val acc1 = AccountEntity("acc-1", "u1", "HDFC Bank", "XX1111", accountType = "SAVINGS")
        val acc2 = AccountEntity("acc-2", "u1", "ICICI Bank", "XX2222", accountType = "SAVINGS")
        val debit = TransactionEntity(
            localId = "tx-d",
            accountId = "acc-1",
            smsFingerprint = "fp-d",
            amount = 5000.0,
            direction = "DEBIT",
            transactionType = "EXPENSE",
            paymentMode = "IMPS",
            referenceId = "IMPS998877",
            transactionDateIso = BankSmsParser.toUtcIso(now),
            transactionDateMs = now
        )
        val credit = TransactionEntity(
            localId = "tx-c",
            accountId = "acc-2",
            smsFingerprint = "fp-c",
            amount = 5000.0,
            direction = "CREDIT",
            transactionType = "INCOME",
            paymentMode = "IMPS",
            referenceId = "IMPS998877",
            transactionDateIso = BankSmsParser.toUtcIso(now + 60_000L),
            transactionDateMs = now + 60_000L
        )
        val outcome = TransferDetector.detectTransfer(credit, listOf(debit), listOf(acc1, acc2), "u1")
        assertNotNull(outcome)
        assertEquals("MATCHED_TRANSFER", outcome!!.transferRecord.status)
        assertEquals("acc-1", outcome.transferRecord.sourceAccountId)
        assertEquals("acc-2", outcome.transferRecord.destinationAccountId)
    }

    @Test
    fun `refund detector links partial refund and budgets subtract refund from category spend`() {
        val now = System.currentTimeMillis()
        val curMonth = IstTime.currentMonthKey(now)
        val orig = TransactionEntity(
            localId = "tx-orig",
            accountId = "acc-1",
            categoryId = "cat-shop",
            categoryName = "Shopping",
            smsFingerprint = "fp-o",
            amount = 2000.0,
            direction = "DEBIT",
            transactionType = "EXPENSE",
            merchant = "Amazon",
            transactionDateIso = BankSmsParser.toUtcIso(now - 3600_000L),
            transactionDateMs = now - 3600_000L
        )
        val refTx = TransactionEntity(
            localId = "tx-ref",
            accountId = "acc-1",
            categoryId = "cat-shop",
            categoryName = "Shopping",
            smsFingerprint = "fp-r",
            amount = 500.0,
            direction = "CREDIT",
            transactionType = "REFUND",
            isRefund = true,
            merchant = "Amazon Refund",
            transactionDateIso = BankSmsParser.toUtcIso(now),
            transactionDateMs = now
        )
        val refundOutcome = RefundDetector.detectRefund(refTx, listOf(orig), "u1")
        assertNotNull(refundOutcome)
        assertEquals("PARTIAL_REFUND", refundOutcome!!.refundRecord.refundType)
        assertEquals("tx-orig", refundOutcome.matchedOriginalTransaction?.localId)

        val budget = BudgetEntity(
            id = "b1",
            userId = "u1",
            name = "Shopping Cap",
            categoryId = "cat-shop",
            categoryName = "Shopping",
            monthlyLimit = 2000.0,
            startMonth = curMonth,
            alertThreshold = 70
        )
        val eval = BudgetAndCardEngine.evaluateBudgets(
            budgets = listOf(budget),
            transactions = listOf(orig, refTx),
            refunds = listOf(refundOutcome.refundRecord),
            existingAlerts = emptyList(),
            monthKey = curMonth,
            nowMs = now
        )
        assertEquals(1, eval.summaries.size)
        // Net spend = 2000 - 500 = 1500 (75%)
        assertEquals(1500.0, eval.summaries[0].netSpent, 0.01)
        assertEquals(75.0, eval.summaries[0].utilizationPercent, 0.1)
        // Crossed 50% and 70%
        assertEquals(2, eval.triggeredAlerts.size)
    }

    @Test
    fun `credit card accounting computes utilization and statement status`() {
        val now = System.currentTimeMillis()
        val card = AccountEntity(
            id = "cc-1",
            userId = "u1",
            bankName = "HDFC Bank",
            accountNumberMask = "XX8812",
            accountType = "CREDIT_CARD",
            openingBalance = 0.0,
            currentBalance = 15000.0,
            creditLimit = 100000.0
        )
        val txns = listOf(
            TransactionEntity(
                localId = "c1",
                accountId = "cc-1",
                smsFingerprint = "fc1",
                amount = 20000.0,
                direction = "DEBIT",
                transactionType = "CC_PURCHASE",
                transactionDateIso = BankSmsParser.toUtcIso(now),
                transactionDateMs = now
            ),
            TransactionEntity(
                localId = "c2",
                accountId = "cc-1",
                smsFingerprint = "fc2",
                amount = 5000.0,
                direction = "CREDIT",
                transactionType = "CC_BILL_PAYMENT",
                isSelfTransfer = true,
                isExcludedFromTotals = true,
                transactionDateIso = BankSmsParser.toUtcIso(now),
                transactionDateMs = now
            )
        )
        val stmt = CreditCardStatementEntity(
            id = "st1",
            userId = "u1",
            accountId = "cc-1",
            statementPeriod = IstTime.currentMonthKey(now),
            statementDateMs = now - 86_400_000L,
            totalDue = 20000.0,
            minimumDue = 1000.0,
            dueDateMs = now + 5 * 86_400_000L,
            paidAmount = 5000.0,
            paymentStatus = "PARTIALLY_PAID",
            updatedAtMs = now
        )
        val summaries = BudgetAndCardEngine.buildCreditCardSummaries(
            accounts = listOf(card),
            transactions = txns,
            statements = listOf(stmt),
            payments = listOf(CreditCardPaymentEntity("p1", "u1", "cc-1", null, "st1", "c2", 5000.0, now)),
            adjustments = emptyList(),
            nowMs = now
        )
        assertEquals(1, summaries.size)
        assertEquals(15000.0, summaries[0].outstandingAmount, 0.01)
        assertEquals(85000.0, summaries[0].availableCredit!!, 0.01)
        assertEquals(15.0, summaries[0].utilizationPercent, 0.1)
        assertEquals("PARTIALLY_PAID", summaries[0].statementStatus)
    }

    @Test
    fun `date range filters resolve accurate IST intervals without drift`() {
        val today = com.krunal.monzi.util.SmsDateFilterOption.TODAY.resolveRange()
        assertTrue(today.second > today.first)
        // Duration of a full day in ms is 86,399,999 ms
        assertEquals(86_399_999L, today.second - today.first)

        val yesterday = com.krunal.monzi.util.SmsDateFilterOption.YESTERDAY.resolveRange()
        assertTrue(yesterday.second < today.first)
        assertEquals(86_399_999L, yesterday.second - yesterday.first)

        val prevDay = com.krunal.monzi.util.SmsDateFilterOption.PREVIOUS_DAY.resolveRange()
        assertTrue(prevDay.second < yesterday.first)
        assertEquals(86_399_999L, prevDay.second - prevDay.first)

        val last7Days = com.krunal.monzi.util.SmsDateFilterOption.LAST_7_DAYS.resolveRange()
        assertTrue(last7Days.second >= today.second)
        assertTrue(last7Days.second - last7Days.first > 6 * 86_400_000L)

        val thisMonth = com.krunal.monzi.util.SmsDateFilterOption.THIS_MONTH.resolveRange()
        assertTrue(thisMonth.second > thisMonth.first)

        val lastMonth = com.krunal.monzi.util.SmsDateFilterOption.LAST_MONTH.resolveRange()
        assertTrue(lastMonth.second < thisMonth.first)

        val custom = com.krunal.monzi.util.SmsDateFilterOption.CUSTOM.resolveRange(
            customStartMs = 1700000000000L,
            customEndMs = 1700500000000L
        )
        assertTrue(custom.second > custom.first)
    }
}
