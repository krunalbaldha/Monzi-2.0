package com.krunal.monzi

import com.krunal.monzi.data.AccountEntity
import com.krunal.monzi.data.AnalyticsPeriod
import com.krunal.monzi.data.FinanceAnalyticsEngine
import com.krunal.monzi.data.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankSmsParserAndAnalyticsTest {

    @Test
    fun parsesHdfcUpiDebitWithBalanceAndRef() {
        val sms = "HDFC Bank: Rs.480.00 debited from A/C XX8421 to Swiggy on 24-11-26 via UPI. Ref 4910283921. Avl Bal Rs.12,340.50"
        val parsed = BankSmsParser.parse("VK-HDFCBK", sms, 1764000000000L)
        assertNotNull(parsed)
        assertEquals(480.0, parsed!!.amount, 0.01)
        assertEquals("DEBIT", parsed.direction)
        assertEquals("HDFC Bank", parsed.bankName)
        assertEquals("XX8421", parsed.accountMask)
        assertEquals("Swiggy", parsed.merchant)
        assertEquals("4910283921", parsed.referenceId)
        assertEquals("UPI", parsed.paymentMode)
        assertEquals(12340.50, parsed.parsedAvailableBalance!!, 0.01)
        assertEquals("Food & Dining", parsed.transactionCategory)
    }

    @Test
    fun parsesIciciCreditAndSalaryCategory() {
        val sms = "ICICI Bank Acct XX5512 credited with INR 75,000.00 on 25-11-2026 towards Salary. Info: ACME CORP. Ref UTR99887766"
        val parsed = BankSmsParser.parse("AX-ICICIB", sms, 1764000000000L)
        assertNotNull(parsed)
        assertEquals(75000.0, parsed!!.amount, 0.01)
        assertEquals("CREDIT", parsed.direction)
        assertEquals("ICICI Bank", parsed.bankName)
        assertEquals("INCOME", parsed.transactionType)
        assertEquals("Salary", parsed.transactionCategory)
    }

    @Test
    fun filtersOtpPromoFailedAndBalanceOnlySms() {
        assertNull(BankSmsParser.parse("HDFC", "123456 is your OTP for Rs.500 txn. Do not share."))
        assertNull(BankSmsParser.parse("ICICI", "Congratulations! Pre-approved loan up to Rs.5,00,000. Apply now!"))
        assertNull(BankSmsParser.parse("SBI", "Your UPI transaction of Rs.999 failed due to insufficient balance."))
        assertNull(BankSmsParser.parse("AXIS", "Available balance in A/C XX1234 is Rs.10,450.00."))
        assertNull(BankSmsParser.parse("KOTAK", "Update your KYC before 30-11-26 to avoid account freeze."))
    }

    @Test
    fun fingerprintIsDeterministic() {
        val sms1 = "Rs.250 paid to Zomato from A/c XX1122 via UPI Ref 1122334455 on 25-11-26"
        val sms2 = "Rs.250.00 debited from A/c XX1122 to Zomato via UPI Ref 1122334455 on 25-11-26"
        val p1 = BankSmsParser.parse("HDFC", sms1, 1764000000000L)!!
        val p2 = BankSmsParser.parse("HDFC", sms2, 1764000000000L)!!
        assertEquals(BankSmsParser.computeFingerprint(p1), BankSmsParser.computeFingerprint(p2))
    }

    @Test
    fun analyticsExcludesSelfTransfersAndComputesNetWorth() {
        val now = System.currentTimeMillis()
        val accounts = listOf(
            AccountEntity("a1", "u1", "HDFC Bank", "XX8421", "SAVINGS", currentBalance = 50000.0),
            AccountEntity("a2", "u1", "ICICI Bank", "XX9900", "CREDIT_CARD", currentBalance = 10000.0)
        )
        val txns = listOf(
            TransactionEntity(
                localId = "t1", userId = "u1", accountId = "a1", smsFingerprint = "fp1",
                amount = 60000.0, direction = "CREDIT", transactionType = "INCOME",
                merchant = "Salary", transactionDateIso = BankSmsParser.toUtcIso(now),
                transactionDateMs = now, categoryName = "Salary"
            ),
            TransactionEntity(
                localId = "t2", userId = "u1", accountId = "a1", smsFingerprint = "fp2",
                amount = 1500.0, direction = "DEBIT", transactionType = "EXPENSE",
                merchant = "Swiggy", transactionDateIso = BankSmsParser.toUtcIso(now),
                transactionDateMs = now, categoryName = "Food & Dining"
            ),
            TransactionEntity(
                localId = "t3", userId = "u1", accountId = "a1", smsFingerprint = "fp3",
                amount = 10000.0, direction = "DEBIT", transactionType = "TRANSFER",
                isExcludedFromTotals = true, merchant = "Self Transfer",
                transactionDateIso = BankSmsParser.toUtcIso(now), transactionDateMs = now
            )
        )
        val dash = FinanceAnalyticsEngine.computeDashboardStats(accounts, txns, now)
        assertEquals(40000.0, dash.netWorth, 0.01)
        assertEquals(60000.0, dash.monthlyIncome, 0.01)
        assertEquals(1500.0, dash.monthlyExpense, 0.01)
        assertEquals(58500.0, dash.netSavings, 0.01)

        val period = FinanceAnalyticsEngine.computePeriodAnalytics(AnalyticsPeriod.TODAY, accounts, txns, nowMs = now)
        assertEquals(60000.0, period.totalIncome, 0.01)
        assertEquals(1500.0, period.totalExpense, 0.01)
        assertTrue(period.categoryBreakdown.any { it.name == "Food & Dining" })
    }

    @Test
    fun dashboardSparklineAndOneYearPeriodComputeFromRealData() {
        val now = System.currentTimeMillis()
        val accounts = listOf(
            AccountEntity("a1", "u1", "SBI", "XX1001", "SAVINGS", currentBalance = 30000.0)
        )
        val txns = listOf(
            TransactionEntity(
                localId = "t1", userId = "u1", accountId = "a1", smsFingerprint = "fp10",
                amount = 10000.0, direction = "CREDIT", transactionType = "INCOME",
                merchant = "Client", transactionDateIso = BankSmsParser.toUtcIso(now - 2 * 86_400_000L),
                transactionDateMs = now - 2 * 86_400_000L, categoryName = "Salary"
            ),
            TransactionEntity(
                localId = "t2", userId = "u1", accountId = "a1", smsFingerprint = "fp11",
                amount = 2000.0, direction = "DEBIT", transactionType = "EXPENSE",
                merchant = "Amazon", transactionDateIso = BankSmsParser.toUtcIso(now),
                transactionDateMs = now, categoryName = "Shopping"
            )
        )
        val dash = FinanceAnalyticsEngine.computeDashboardStats(accounts, txns, now)
        assertEquals(14, dash.balanceSparkline.size)
        assertEquals(30000.0, dash.balanceSparkline.last(), 0.01)
        assertNotNull(dash.balanceChangePct)

        val yearPeriod = FinanceAnalyticsEngine.computePeriodAnalytics(AnalyticsPeriod.ONE_YEAR, accounts, txns, nowMs = now)
        assertEquals(10000.0, yearPeriod.totalIncome, 0.01)
        assertEquals(2000.0, yearPeriod.totalExpense, 0.01)
        assertTrue(yearPeriod.activityTrend.isNotEmpty())
    }
}
