package com.krunal.monzi.data.engines

import com.krunal.monzi.data.AccountBalanceAdjustmentEntity
import com.krunal.monzi.data.AccountBalanceSnapshotEntity
import com.krunal.monzi.data.AccountEntity
import com.krunal.monzi.data.BudgetAlertEntity
import com.krunal.monzi.data.BudgetEntity
import com.krunal.monzi.data.CreditCardPaymentEntity
import com.krunal.monzi.data.CreditCardStatementEntity
import com.krunal.monzi.data.RefundRecordEntity
import com.krunal.monzi.data.TransactionEntity
import com.krunal.monzi.util.IstTime
import com.krunal.monzi.util.MoneyMath
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class CreditCardSummary(
    val card: AccountEntity,
    val outstandingAmount: Double,
    val creditLimit: Double?,
    val availableCredit: Double?,
    val utilizationPercent: Double,
    val unbilledSpend: Double,
    val latestStatement: CreditCardStatementEntity?,
    val statementStatus: String, // UPCOMING, DUE, PARTIALLY_PAID, PAID, OVERDUE, UNKNOWN
    val totalDueRemaining: Double,
    val minimumDue: Double,
    val dueDateMs: Long?,
    val cardTransactions: List<TransactionEntity>,
    val payments: List<CreditCardPaymentEntity>
)

data class BudgetProgressSummary(
    val budget: BudgetEntity,
    val monthKey: String,
    val grossSpent: Double,
    val refundedAmount: Double,
    val netSpent: Double,
    val monthlyLimit: Double,
    val remainingAmount: Double,
    val utilizationPercent: Double,
    val projectedEndOfMonthSpend: Double,
    val healthStatus: String // SAFE, APPROACHING, WARNING, EXCEEDED
)

data class BudgetEvaluationResult(
    val summaries: List<BudgetProgressSummary>,
    val overallLimit: Double,
    val overallNetSpent: Double,
    val overallUtilizationPct: Double,
    val triggeredAlerts: List<BudgetAlertEntity>,
    val smartInsights: List<String>
)

object BudgetAndCardEngine {

    fun buildCreditCardSummaries(
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>,
        statements: List<CreditCardStatementEntity>,
        payments: List<CreditCardPaymentEntity>,
        adjustments: List<AccountBalanceAdjustmentEntity>,
        snapshots: List<AccountBalanceSnapshotEntity> = emptyList(),
        nowMs: Long = System.currentTimeMillis()
    ): List<CreditCardSummary> {
        val ccAccounts = accounts.filter { it.accountType == "CREDIT_CARD" }
        return ccAccounts.map { card ->
            val reconciled = ReconciliationEngine.reconcileAccountLedger(
                account = card,
                transactions = transactions,
                adjustments = adjustments,
                snapshots = snapshots
            )
            val cardTxns = transactions.filter {
                it.accountId == card.id &&
                    it.transactionStatus != "FAILED" &&
                    it.duplicateStatus != "EXACT_DUPLICATE"
            }.sortedByDescending { it.transactionDateMs }

            val cardStatements = statements.filter { it.accountId == card.id }
                .sortedByDescending { it.statementDateMs }
            val latestStatement = cardStatements.firstOrNull()
            val cardPayments = payments.filter { it.cardAccountId == card.id }
                .sortedByDescending { it.paymentDateMs }

            val stmtStartMs = latestStatement?.statementDateMs ?: 0L
            val unbilledSpend = MoneyMath.sum(
                cardTxns.filter { it.direction == "DEBIT" && it.transactionDateMs >= stmtStartMs }
                    .map { it.amount }
            )

            val totalDueRemaining = latestStatement?.let {
                maxOf(0.0, MoneyMath.subtract(it.totalDue, it.paidAmount))
            } ?: reconciled.calculatedBalance

            val stmtStatus = if (latestStatement != null) {
                computeStatementStatus(latestStatement, nowMs)
            } else {
                if (reconciled.calculatedBalance > 0.0) "UPCOMING" else "PAID"
            }

            CreditCardSummary(
                card = card,
                outstandingAmount = reconciled.calculatedBalance,
                creditLimit = reconciled.creditLimit,
                availableCredit = reconciled.availableCredit,
                utilizationPercent = reconciled.utilizationPercent,
                unbilledSpend = MoneyMath.round2(unbilledSpend),
                latestStatement = latestStatement,
                statementStatus = stmtStatus,
                totalDueRemaining = MoneyMath.round2(totalDueRemaining),
                minimumDue = latestStatement?.minimumDue ?: 0.0,
                dueDateMs = latestStatement?.dueDateMs,
                cardTransactions = cardTxns,
                payments = cardPayments
            )
        }
    }

    fun computeStatementStatus(stmt: CreditCardStatementEntity, nowMs: Long = System.currentTimeMillis()): String {
        val remaining = MoneyMath.subtract(stmt.totalDue, stmt.paidAmount)
        return when {
            stmt.totalDue <= 0.0 || remaining <= 0.01 -> "PAID"
            stmt.paidAmount > 0.01 && remaining > 0.01 -> {
                if (stmt.dueDateMs != null && nowMs > stmt.dueDateMs) "OVERDUE" else "PARTIALLY_PAID"
            }
            stmt.dueDateMs != null && nowMs > stmt.dueDateMs -> "OVERDUE"
            stmt.dueDateMs != null && (stmt.dueDateMs - nowMs) <= 7L * 24L * 3600L * 1000L -> "DUE"
            else -> "UPCOMING"
        }
    }

    fun evaluateBudgets(
        budgets: List<BudgetEntity>,
        transactions: List<TransactionEntity>,
        refunds: List<RefundRecordEntity>,
        existingAlerts: List<BudgetAlertEntity>,
        monthKey: String = IstTime.currentMonthKey(),
        userId: String = "",
        nowMs: Long = System.currentTimeMillis()
    ): BudgetEvaluationResult {
        val (monthStart, monthEnd) = IstTime.monthRangeMillis(monthKey)

        // Valid transactions in selected month
        val monthTxns = transactions.filter { tx ->
            tx.transactionDateMs in monthStart..monthEnd &&
                tx.transactionStatus != "FAILED" &&
                tx.duplicateStatus != "EXACT_DUPLICATE" &&
                !tx.isExcludedFromTotals &&
                !tx.isSelfTransfer &&
                tx.transactionType != "TRANSFER" &&
                tx.transactionType != "CC_BILL_PAYMENT"
        }

        val expenseTxns = monthTxns.filter { it.direction == "DEBIT" }
        val refundTxns = monthTxns.filter {
            it.direction == "CREDIT" && (it.isRefund || it.transactionType == "REFUND" || it.transactionType == "REVERSAL")
        }

        // Map original debit txns to matched refund amounts so net category spend reflects refunds
        val txnById = transactions.associateBy { it.localId }
        val matchedRefundTotalsByCategory = HashMap<String, Double>()
        for (ref in refunds) {
            val refTx = txnById[ref.refundTransactionId] ?: continue
            if (refTx.transactionDateMs !in monthStart..monthEnd) continue
            val origTx = ref.originalTransactionId?.let { txnById[it] }
            val catKey = origTx?.categoryId ?: origTx?.categoryName ?: refTx.categoryId ?: refTx.categoryName ?: "Uncategorized"
            matchedRefundTotalsByCategory[catKey] = MoneyMath.add(matchedRefundTotalsByCategory[catKey] ?: 0.0, ref.refundAmount)
        }
        // Also include standalone detected refund txns not yet in refunds table
        val recordedRefundIds = refunds.map { it.refundTransactionId }.toHashSet()
        for (rtx in refundTxns) {
            if (rtx.localId in recordedRefundIds) continue
            val catKey = rtx.categoryId ?: rtx.categoryName ?: "Uncategorized"
            matchedRefundTotalsByCategory[catKey] = MoneyMath.add(matchedRefundTotalsByCategory[catKey] ?: 0.0, rtx.amount)
        }

        val istCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"), Locale.US).apply {
            timeInMillis = nowMs
        }
        val currentMonthKey = IstTime.currentMonthKey(nowMs)
        val totalDaysInMonth = istCal.getActualMaximum(Calendar.DAY_OF_MONTH).coerceAtLeast(28)
        val elapsedDays = if (monthKey == currentMonthKey) {
            istCal.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
        } else totalDaysInMonth

        val existingAlertKeys = existingAlerts
            .filter { it.monthKey == monthKey }
            .map { "${it.budgetId}:${it.thresholdPct}" }
            .toHashSet()

        val newlyTriggeredAlerts = ArrayList<BudgetAlertEntity>()
        val summaries = budgets.filter { it.status == "ACTIVE" }.map { budget ->
            val matchingExpenses = if (budget.categoryId.isNullOrBlank()) {
                expenseTxns
            } else {
                expenseTxns.filter {
                    it.categoryId == budget.categoryId ||
                        (!budget.categoryName.isNullOrBlank() && it.categoryName.equals(budget.categoryName, ignoreCase = true))
                }
            }
            val grossSpent = MoneyMath.sum(matchingExpenses.map { it.amount })
            val refundDeducted = if (budget.categoryId.isNullOrBlank()) {
                MoneyMath.sum(matchedRefundTotalsByCategory.values)
            } else {
                val byId = matchedRefundTotalsByCategory[budget.categoryId] ?: 0.0
                val byName = budget.categoryName?.let { matchedRefundTotalsByCategory[it] } ?: 0.0
                if (byId > 0.0) byId else byName
            }
            val netSpent = maxOf(0.0, MoneyMath.subtract(grossSpent, refundDeducted))
            val remaining = MoneyMath.subtract(budget.monthlyLimit, netSpent)
            val usagePct = MoneyMath.percentage(netSpent, budget.monthlyLimit)
            val dailyAvg = if (elapsedDays > 0) netSpent / elapsedDays else netSpent
            val projected = MoneyMath.round2(dailyAvg * totalDaysInMonth)

            val health = when {
                usagePct >= 100.0 -> "EXCEEDED"
                usagePct >= budget.alertThreshold -> "WARNING"
                usagePct >= 50.0 -> "APPROACHING"
                else -> "SAFE"
            }

            val thresholds = listOf(50, budget.alertThreshold, 100, 101).distinct().sorted()
            for (threshold in thresholds) {
                val crossed = if (threshold == 101) usagePct > 100.01 else usagePct >= threshold
                val key = "${budget.id}:$threshold"
                if (crossed && key !in existingAlertKeys && monthKey == currentMonthKey) {
                    val label = if (threshold == 101) "exceeded 100%" else "reached $threshold%"
                    newlyTriggeredAlerts.add(
                        BudgetAlertEntity(
                            id = UUID.randomUUID().toString(),
                            userId = userId,
                            budgetId = budget.id,
                            monthKey = monthKey,
                            thresholdPct = threshold,
                            usagePct = usagePct,
                            spentAmount = netSpent,
                            message = "${budget.name} has $label (₹${netSpent.toInt()} of ₹${budget.monthlyLimit.toInt()})",
                            acknowledged = false,
                            triggeredAtMs = nowMs
                        )
                    )
                    existingAlertKeys.add(key)
                }
            }

            BudgetProgressSummary(
                budget = budget,
                monthKey = monthKey,
                grossSpent = MoneyMath.round2(grossSpent),
                refundedAmount = MoneyMath.round2(refundDeducted),
                netSpent = MoneyMath.round2(netSpent),
                monthlyLimit = MoneyMath.round2(budget.monthlyLimit),
                remainingAmount = MoneyMath.round2(remaining),
                utilizationPercent = usagePct,
                projectedEndOfMonthSpend = projected,
                healthStatus = health
            )
        }

        // Compute overall monthly summary
        val overallBudget = summaries.firstOrNull { it.budget.categoryId.isNullOrBlank() }
        val totalGross = MoneyMath.sum(expenseTxns.map { it.amount })
        val totalRefunds = MoneyMath.sum(matchedRefundTotalsByCategory.values)
        val totalNetSpent = maxOf(0.0, MoneyMath.subtract(totalGross, totalRefunds))
        val overallLimit = overallBudget?.monthlyLimit ?: MoneyMath.sum(summaries.map { it.monthlyLimit })
        val overallUtil = MoneyMath.percentage(totalNetSpent, overallLimit)

        // Smart Insights
        val insights = ArrayList<String>()
        val topCategory = expenseTxns
            .groupBy { it.categoryName ?: "Uncategorized" }
            .mapValues { entry -> MoneyMath.sum(entry.value.map { it.amount }) }
            .maxByOrNull { it.value }
        if (topCategory != null && totalNetSpent > 0.0) {
            val share = MoneyMath.percentage(topCategory.value, totalNetSpent)
            insights.add("Highest category this month: ${topCategory.key} (${share.toInt()}% of spend).")
        }
        val exceededCount = summaries.count { it.healthStatus == "EXCEEDED" }
        if (exceededCount > 0) {
            insights.add("$exceededCount budget(s) exceeded their monthly target.")
        } else if (summaries.any { it.healthStatus == "WARNING" }) {
            insights.add("One or more budgets are above your alert threshold.")
        }
        val atRiskProjection = summaries.firstOrNull {
            it.healthStatus != "EXCEEDED" && it.projectedEndOfMonthSpend > it.monthlyLimit && it.monthlyLimit > 0
        }
        if (atRiskProjection != null) {
            insights.add("At current pace, ${atRiskProjection.budget.name} may reach ₹${atRiskProjection.projectedEndOfMonthSpend.toInt()} by month end.")
        }

        return BudgetEvaluationResult(
            summaries = summaries,
            overallLimit = MoneyMath.round2(overallLimit),
            overallNetSpent = MoneyMath.round2(totalNetSpent),
            overallUtilizationPct = overallUtil,
            triggeredAlerts = newlyTriggeredAlerts,
            smartInsights = insights
        )
    }
}
