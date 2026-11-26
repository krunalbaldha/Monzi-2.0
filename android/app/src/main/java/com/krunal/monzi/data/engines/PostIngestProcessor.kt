package com.krunal.monzi.data.engines

import android.content.Context
import com.krunal.monzi.LocalFinanceDbHelper
import com.krunal.monzi.NotificationHelper
import com.krunal.monzi.ParsedCreditCardStatement
import com.krunal.monzi.data.AccountEntity
import com.krunal.monzi.data.CreditCardStatementEntity
import com.krunal.monzi.data.TransactionEntity
import com.krunal.monzi.util.IstTime
import com.krunal.monzi.util.MoneyMath
import java.util.UUID

object PostIngestProcessor {

    /**
     * Runs duplicate check, snapshot reconciliation, transfer matching,
     * refund linking, and budget threshold alert checks after inserting a transaction.
     */
    fun processTransaction(
        context: Context?,
        db: LocalFinanceDbHelper,
        txn: TransactionEntity,
        userId: String
    ) {
        val allTxns = db.getAllTransactions(2000)
        val accounts = db.getAccounts()

        // 1. Possible duplicate evaluation (exact duplicates are already blocked/ignored by fingerprint/ref)
        val dupResult = DuplicateDetector.evaluateDuplicate(txn, allTxns)
        if (dupResult.status == "POSSIBLE_DUPLICATE" || dupResult.status == "EXACT_DUPLICATE") {
            db.updateTransactionAutomationFlags(
                localId = txn.localId,
                duplicateStatus = dupResult.status,
                linkedTransactionId = dupResult.linkedTransactionId,
                isExcludedFromTotals = if (dupResult.status == "EXACT_DUPLICATE") true else null
            )
            if (dupResult.status == "EXACT_DUPLICATE") return
        }

        // 2. Snapshot & balance reconciliation
        val account = txn.accountId?.let { id -> accounts.firstOrNull { it.id == id } }
        if (account != null && txn.parsedAvailableBalance != null) {
            ReconciliationEngine.recordSnapshotAndReconcile(db, account, txn, userId)
        }

        // 3. Transfer detection & own-account linking
        val transferOutcome = TransferDetector.detectTransfer(txn, allTxns, accounts, userId)
        if (transferOutcome != null) {
            val rec = transferOutcome.transferRecord
            db.upsertTransfer(rec)
            val updatedType = if (rec.transferType == "BANK_TO_CARD") "CC_BILL_PAYMENT" else "TRANSFER"
            transferOutcome.debitTransactionId?.let { dId ->
                db.updateTransactionAutomationFlags(
                    localId = dId,
                    transferStatus = rec.status,
                    linkedTransactionId = transferOutcome.creditTransactionId,
                    isSelfTransfer = true,
                    isExcludedFromTotals = true,
                    transactionType = if (txn.localId == dId && updatedType == "CC_BILL_PAYMENT") "CC_BILL_PAYMENT" else "TRANSFER"
                )
            }
            transferOutcome.creditTransactionId?.let { cId ->
                db.updateTransactionAutomationFlags(
                    localId = cId,
                    transferStatus = rec.status,
                    linkedTransactionId = transferOutcome.debitTransactionId,
                    isSelfTransfer = true,
                    isExcludedFromTotals = true,
                    transactionType = updatedType
                )
            }
        }

        // 4. Refund / reversal detection & linking
        if (txn.direction == "CREDIT" && (txn.isRefund || txn.transactionType == "REFUND" || txn.transactionType == "REVERSAL")) {
            val refundOutcome = RefundDetector.detectRefund(txn, allTxns, userId)
            if (refundOutcome != null) {
                db.upsertRefund(refundOutcome.refundRecord)
                val matchedOrig = refundOutcome.matchedOriginalTransaction
                db.updateTransactionAutomationFlags(
                    localId = txn.localId,
                    refundStatus = refundOutcome.refundRecord.status,
                    linkedTransactionId = matchedOrig?.localId
                )
                if (matchedOrig != null && !matchedOrig.categoryId.isNullOrBlank()) {
                    db.updateTransactionLocal(
                        localId = txn.localId,
                        categoryId = matchedOrig.categoryId,
                        categoryName = matchedOrig.categoryName,
                        categoryColor = matchedOrig.categoryColor,
                        notes = txn.notes,
                        merchant = txn.merchant,
                        transactionType = txn.transactionType
                    )
                }
            }
        }

        // 5. Budget threshold evaluation (trigger once per crossing)
        val alertsEnabled = context?.let { com.krunal.monzi.data.AuthSessionManager(it).isBudgetAlertsEnabled() } ?: true
        val activeBudgets = db.getAllBudgets().filter { it.status == "ACTIVE" }
        if (alertsEnabled && activeBudgets.isNotEmpty()) {
            val refreshedTxns = db.getAllTransactions(2000)
            val eval = BudgetAndCardEngine.evaluateBudgets(
                budgets = activeBudgets,
                transactions = refreshedTxns,
                refunds = db.getAllRefunds(),
                existingAlerts = db.getAllBudgetAlerts(),
                monthKey = IstTime.toMonthKey(txn.transactionDateMs),
                userId = userId
            )
            for (alert in eval.triggeredAlerts) {
                val inserted = db.insertBudgetAlertIfNew(alert)
                if (inserted && context != null) {
                    NotificationHelper.notifyBudgetAlert(
                        context = context,
                        title = "Budget Alert (${alert.thresholdPct.coerceAtMost(100)}%)",
                        message = alert.message
                    )
                }
            }
        }
    }

    fun applyCreditCardStatement(
        db: LocalFinanceDbHelper,
        stmt: ParsedCreditCardStatement,
        userId: String
    ) {
        val accounts = db.getAccounts()
        val existingCard = accounts.firstOrNull {
            it.accountType == "CREDIT_CARD" &&
                it.bankName.equals(stmt.bankName, ignoreCase = true) &&
                it.accountNumberMask.equals(stmt.cardMask, ignoreCase = true)
        } ?: accounts.firstOrNull {
            it.bankName.equals(stmt.bankName, ignoreCase = true) &&
                it.accountNumberMask.equals(stmt.cardMask, ignoreCase = true)
        }

        val card = if (existingCard != null) {
            val updated = existingCard.copy(
                accountType = "CREDIT_CARD",
                currentBalance = maxOf(existingCard.currentBalance, stmt.totalDue),
                updatedAtMs = System.currentTimeMillis()
            )
            db.upsertAccountLocal(updated)
            updated
        } else {
            val created = AccountEntity(
                id = "local-acc-${UUID.randomUUID()}",
                userId = userId,
                bankName = stmt.bankName,
                accountNumberMask = stmt.cardMask,
                accountType = "CREDIT_CARD",
                openingBalance = 0.0,
                currentBalance = stmt.totalDue,
                balanceSource = "BANK_REPORTED",
                reconciliationStatus = "BANK_SNAPSHOT_AVAILABLE",
                pendingSync = true,
                updatedAtMs = System.currentTimeMillis()
            )
            db.upsertAccountLocal(created)
            created
        }

        val existingStmt = db.getAllStatements().firstOrNull {
            it.accountId == card.id && it.statementPeriod == stmt.statementPeriod
        }
        val paidAmount = existingStmt?.paidAmount ?: 0.0
        val record = CreditCardStatementEntity(
            id = existingStmt?.id ?: UUID.randomUUID().toString(),
            userId = userId,
            accountId = card.id,
            statementPeriod = stmt.statementPeriod,
            statementDateMs = stmt.statementDateMs,
            totalDue = MoneyMath.round2(stmt.totalDue),
            minimumDue = MoneyMath.round2(stmt.minimumDue),
            dueDateMs = stmt.dueDateMs,
            paidAmount = paidAmount,
            paymentStatus = "UPCOMING",
            updatedAtMs = System.currentTimeMillis()
        )
        val status = BudgetAndCardEngine.computeStatementStatus(record)
        db.upsertStatement(record.copy(paymentStatus = status))
    }
}
