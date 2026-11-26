package com.krunal.monzi.data.engines

import com.krunal.monzi.LocalFinanceDbHelper
import com.krunal.monzi.data.AccountBalanceAdjustmentEntity
import com.krunal.monzi.data.AccountBalanceSnapshotEntity
import com.krunal.monzi.data.AccountEntity
import com.krunal.monzi.data.TransactionEntity
import com.krunal.monzi.util.MoneyMath
import java.util.UUID
import kotlin.math.abs

data class ReconciledAccountSummary(
    val account: AccountEntity,
    val calculatedBalance: Double,
    val latestSnapshot: AccountBalanceSnapshotEntity?,
    val discrepancyAmount: Double,
    val reconciliationStatus: String,
    val creditLimit: Double? = null,
    val availableCredit: Double? = null,
    val utilizationPercent: Double = 0.0
)

object ReconciliationEngine {

    fun reconcileAccountLedger(
        account: AccountEntity,
        transactions: List<TransactionEntity>,
        adjustments: List<AccountBalanceAdjustmentEntity>,
        snapshots: List<AccountBalanceSnapshotEntity>
    ): ReconciledAccountSummary {
        val accountTxns = transactions.filter {
            it.accountId == account.id &&
                it.transactionStatus != "FAILED" &&
                it.duplicateStatus != "EXACT_DUPLICATE"
        }
        val approvedAdjSum = MoneyMath.sum(
            adjustments.filter { it.accountId == account.id && it.approved }.map { it.adjustmentAmount }
        )

        val calculated = if (account.accountType == "CREDIT_CARD") {
            // Accumulate signed delta before clamping at 0 so list order (DESC vs ASC) never distorts final outstanding debt.
            var netDelta = 0.0
            for (tx in accountTxns) {
                netDelta = if (tx.direction == "DEBIT") {
                    MoneyMath.add(netDelta, tx.amount)
                } else {
                    MoneyMath.subtract(netDelta, tx.amount)
                }
            }
            maxOf(0.0, MoneyMath.add(MoneyMath.add(account.openingBalance, netDelta), approvedAdjSum))
        } else {
            var balance = account.openingBalance
            for (tx in accountTxns) {
                balance = if (tx.direction == "CREDIT") {
                    MoneyMath.add(balance, tx.amount)
                } else {
                    MoneyMath.subtract(balance, tx.amount)
                }
            }
            MoneyMath.add(balance, approvedAdjSum)
        }

        val latestSnapshot = snapshots
            .filter { it.accountId == account.id }
            .maxByOrNull { it.snapshotAtMs }

        val discrepancy = if (latestSnapshot != null && account.accountType != "CREDIT_CARD") {
            MoneyMath.subtract(latestSnapshot.reportedBalance, calculated)
        } else 0.0

        val status = when {
            account.bankName == "Bank" && account.accountNumberMask == "XX0000" -> "NEEDS_ACCOUNT_MAPPING"
            latestSnapshot == null -> "CALCULATED_ONLY"
            abs(discrepancy) > 0.01 -> "DISCREPANCY_DETECTED"
            else -> "RECONCILED"
        }

        val limit = account.creditLimit
        val availCredit = if (account.accountType == "CREDIT_CARD" && limit != null) {
            maxOf(0.0, MoneyMath.subtract(limit, calculated))
        } else account.availableBalance

        val utilization = if (account.accountType == "CREDIT_CARD" && limit != null && limit > 0.0) {
            MoneyMath.percentage(calculated, limit)
        } else 0.0

        return ReconciledAccountSummary(
            account = account.copy(
                reconciliationStatus = status,
                availableBalance = availCredit ?: account.availableBalance
            ),
            calculatedBalance = MoneyMath.round2(calculated),
            latestSnapshot = latestSnapshot,
            discrepancyAmount = MoneyMath.round2(discrepancy),
            reconciliationStatus = status,
            creditLimit = limit,
            availableCredit = availCredit?.let { MoneyMath.round2(it) },
            utilizationPercent = utilization
        )
    }

    fun recordSnapshotAndReconcile(
        db: LocalFinanceDbHelper,
        account: AccountEntity,
        txn: TransactionEntity,
        userId: String
    ) {
        val reported = txn.parsedAvailableBalance ?: return
        val allTxns = db.getAllTransactions(5000)
        val allAdjustments = db.getAllAdjustments()
        val snapshots = db.getSnapshotsForAccount(account.id)
        val summary = reconcileAccountLedger(account, allTxns, allAdjustments, snapshots)

        if (account.accountType == "CREDIT_CARD") {
            // For credit cards, SMS available balance is usually available credit limit
            val inferredLimit = account.creditLimit
            val nextAvail = MoneyMath.round2(reported)
            db.upsertAccountLocal(
                account.copy(
                    availableBalance = nextAvail,
                    creditLimit = inferredLimit ?: MoneyMath.add(nextAvail, summary.calculatedBalance),
                    updatedAtMs = System.currentTimeMillis()
                )
            )
            return
        }

        val discrepancy = MoneyMath.subtract(reported, summary.calculatedBalance)
        val snapshot = AccountBalanceSnapshotEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            accountId = account.id,
            reportedBalance = MoneyMath.round2(reported),
            calculatedBalanceAtSnapshot = summary.calculatedBalance,
            discrepancyAmount = discrepancy,
            sourceTransactionId = txn.localId,
            snapshotAtMs = txn.transactionDateMs
        )
        db.insertSnapshot(snapshot)

        val status = if (abs(discrepancy) > 0.01) "DISCREPANCY_DETECTED" else "RECONCILED"
        db.upsertAccountLocal(
            account.copy(
                currentBalance = MoneyMath.round2(reported),
                availableBalance = MoneyMath.round2(reported),
                balanceSource = "BANK_REPORTED",
                reconciliationStatus = status,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    /**
     * Re-evaluates ledger calculations for all accounts without creating duplicate snapshots.
     */
    fun recalculateAllAccountBalances(db: LocalFinanceDbHelper) {
        val accounts = db.getAccounts()
        val allTxns = db.getAllTransactions(5000)
        val allAdjustments = db.getAllAdjustments()
        val snapshots = db.getAllSnapshots()

        for (acc in accounts) {
            val summary = reconcileAccountLedger(acc, allTxns, allAdjustments, snapshots)
            val snap = summary.latestSnapshot
            // Preserve BANK_REPORTED balance anchored on latest SMS snapshot + subsequent valid txns
            // so incomplete historical SMS logs never overwrite a verified bank balance.
            val nextBalance = if (acc.balanceSource == "BANK_REPORTED" && acc.accountType != "CREDIT_CARD" && snap != null) {
                val postSnapTxns = allTxns.filter {
                    it.accountId == acc.id &&
                        it.localId != snap.sourceTransactionId &&
                        it.transactionDateMs > snap.snapshotAtMs &&
                        it.transactionStatus != "FAILED" &&
                        it.duplicateStatus != "EXACT_DUPLICATE"
                }
                var bal = snap.reportedBalance
                for (tx in postSnapTxns) {
                    bal = if (tx.direction == "CREDIT") MoneyMath.add(bal, tx.amount) else MoneyMath.subtract(bal, tx.amount)
                }
                val postAdj = MoneyMath.sum(
                    allAdjustments.filter { it.accountId == acc.id && it.approved && it.createdAtMs > snap.snapshotAtMs }
                        .map { it.adjustmentAmount }
                )
                MoneyMath.round2(MoneyMath.add(bal, postAdj))
            } else {
                summary.calculatedBalance
            }
            val updated = acc.copy(
                currentBalance = nextBalance,
                reconciliationStatus = summary.reconciliationStatus,
                availableBalance = summary.availableCredit ?: if (acc.balanceSource == "BANK_REPORTED" && acc.accountType != "CREDIT_CARD") nextBalance else acc.availableBalance,
                creditLimit = summary.creditLimit ?: acc.creditLimit,
                updatedAtMs = System.currentTimeMillis()
            )
            db.upsertAccountLocal(updated)
        }
    }
}
