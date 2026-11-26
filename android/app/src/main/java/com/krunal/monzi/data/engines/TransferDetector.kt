package com.krunal.monzi.data.engines

import com.krunal.monzi.data.AccountEntity
import com.krunal.monzi.data.TransactionEntity
import com.krunal.monzi.data.TransferRecordEntity
import com.krunal.monzi.util.MoneyMath
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

data class TransferDetectionOutcome(
    val transferRecord: TransferRecordEntity,
    val debitTransactionId: String?,
    val creditTransactionId: String?
)

object TransferDetector {

    private const val TRANSFER_WINDOW_MS = 24L * 60L * 60L * 1000L

    fun detectTransfer(
        candidate: TransactionEntity,
        existingTxns: List<TransactionEntity>,
        accounts: List<AccountEntity>,
        userId: String
    ): TransferDetectionOutcome? {
        if (candidate.transactionStatus == "FAILED" || candidate.duplicateStatus == "EXACT_DUPLICATE") return null
        if (candidate.transferStatus == "NOT_A_TRANSFER") return null
        if (candidate.isRefund) return null

        val oppositeDir = if (candidate.direction == "DEBIT") "CREDIT" else "DEBIT"
        val accountMap = accounts.associateBy { it.id }

        val candidateAccountId = candidate.accountId
        val matchedPartner = existingTxns
            .filter { partner ->
                partner.localId != candidate.localId &&
                    partner.transactionStatus != "FAILED" &&
                    partner.duplicateStatus != "EXACT_DUPLICATE" &&
                    partner.transferStatus != "NOT_A_TRANSFER" &&
                    !partner.isRefund &&
                    partner.direction == oppositeDir &&
                    MoneyMath.absDiff(partner.amount, candidate.amount) <= 0.01 &&
                    abs(partner.transactionDateMs - candidate.transactionDateMs) <= TRANSFER_WINDOW_MS &&
                    !partner.accountId.isNullOrBlank() &&
                    !candidateAccountId.isNullOrBlank() &&
                    partner.accountId != candidateAccountId
            }
            .sortedByDescending { partner ->
                val sameRef = !candidate.referenceId.isNullOrBlank() &&
                    candidate.referenceId.equals(partner.referenceId, ignoreCase = true)
                val sameSelfFlag = candidate.isSelfTransfer || partner.isSelfTransfer
                when {
                    sameRef -> 3
                    sameSelfFlag -> 2
                    else -> 1
                }
            }
            .firstOrNull()

        if (matchedPartner != null) {
            val debitLeg = if (candidate.direction == "DEBIT") candidate else matchedPartner
            val creditLeg = if (candidate.direction == "CREDIT") candidate else matchedPartner
            val srcAcc = debitLeg.accountId?.let { accountMap[it] }
            val dstAcc = creditLeg.accountId?.let { accountMap[it] }
            val transferType = classifyTransferType(srcAcc, dstAcc, debitLeg.paymentMode)

            val record = TransferRecordEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                sourceAccountId = debitLeg.accountId,
                destinationAccountId = creditLeg.accountId,
                debitTransactionId = debitLeg.localId,
                creditTransactionId = creditLeg.localId,
                amount = MoneyMath.round2(candidate.amount),
                transferType = transferType,
                status = "MATCHED_TRANSFER",
                referenceId = debitLeg.referenceId ?: creditLeg.referenceId,
                transferDateMs = minOf(debitLeg.transactionDateMs, creditLeg.transactionDateMs)
            )
            return TransferDetectionOutcome(record, debitLeg.localId, creditLeg.localId)
        }

        if (candidate.isSelfTransfer || candidate.transactionType == "TRANSFER" || candidate.transactionType == "CC_BILL_PAYMENT") {
            val transferType = if (candidate.transactionType == "CC_BILL_PAYMENT") {
                "BANK_TO_CARD"
            } else {
                classifyTransferType(
                    src = candidate.accountId?.let { accountMap[it] },
                    dst = null,
                    paymentMode = candidate.paymentMode
                )
            }
            val record = TransferRecordEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                sourceAccountId = if (candidate.direction == "DEBIT") candidate.accountId else null,
                destinationAccountId = if (candidate.direction == "CREDIT") candidate.accountId else null,
                debitTransactionId = if (candidate.direction == "DEBIT") candidate.localId else null,
                creditTransactionId = if (candidate.direction == "CREDIT") candidate.localId else null,
                amount = MoneyMath.round2(candidate.amount),
                transferType = transferType,
                status = "UNMATCHED_TRANSFER",
                referenceId = candidate.referenceId,
                transferDateMs = candidate.transactionDateMs
            )
            return TransferDetectionOutcome(
                transferRecord = record,
                debitTransactionId = record.debitTransactionId,
                creditTransactionId = record.creditTransactionId
            )
        }

        return null
    }

    private fun classifyTransferType(
        src: AccountEntity?,
        dst: AccountEntity?,
        paymentMode: String
    ): String {
        val mode = paymentMode.uppercase(Locale.US)
        return when {
            dst?.accountType == "CREDIT_CARD" -> "BANK_TO_CARD"
            src?.accountType == "SAVINGS" && dst?.accountType == "WALLET" -> "BANK_TO_WALLET"
            src?.accountType == "WALLET" && dst?.accountType == "SAVINGS" -> "WALLET_TO_BANK"
            mode in listOf("NEFT", "IMPS", "RTGS") -> "NEFT_IMPS_RTGS_SELF"
            mode == "UPI" -> "UPI_SELF"
            else -> "BANK_TO_BANK"
        }
    }
}
