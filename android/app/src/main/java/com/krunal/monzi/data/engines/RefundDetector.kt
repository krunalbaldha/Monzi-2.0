package com.krunal.monzi.data.engines

import com.krunal.monzi.data.RefundRecordEntity
import com.krunal.monzi.data.TransactionEntity
import com.krunal.monzi.util.MoneyMath
import java.util.Locale
import java.util.UUID

data class RefundDetectionOutcome(
    val refundRecord: RefundRecordEntity,
    val matchedOriginalTransaction: TransactionEntity?
)

object RefundDetector {

    private const val REFUND_LOOKBACK_MS = 30L * 24L * 60L * 60L * 1000L

    fun detectRefund(
        creditTxn: TransactionEntity,
        existingTxns: List<TransactionEntity>,
        userId: String
    ): RefundDetectionOutcome? {
        if (creditTxn.direction != "CREDIT") return null
        val isRefundCandidate = creditTxn.isRefund ||
            creditTxn.transactionType == "REFUND" ||
            creditTxn.transactionType == "REVERSAL"
        if (!isRefundCandidate) return null

        val minDate = creditTxn.transactionDateMs - REFUND_LOOKBACK_MS
        val refundMerchant = norm(creditTxn.merchant)

        val candidates = existingTxns.filter { orig ->
            orig.localId != creditTxn.localId &&
                orig.direction == "DEBIT" &&
                orig.transactionStatus != "FAILED" &&
                orig.duplicateStatus != "EXACT_DUPLICATE" &&
                !orig.isSelfTransfer &&
                orig.transactionDateMs in minDate..creditTxn.transactionDateMs &&
                orig.amount + 0.01 >= creditTxn.amount
        }

        val bestMatch = candidates
            .mapNotNull { orig ->
                val sameAccount = !orig.accountId.isNullOrBlank() && orig.accountId == creditTxn.accountId
                val origMerchant = norm(orig.merchant)
                val merchantOverlap = refundMerchant.isNotEmpty() && origMerchant.isNotEmpty() &&
                    DuplicateDetector.merchantSimilarity(refundMerchant, origMerchant) >= 0.55
                val sameAmount = MoneyMath.absDiff(orig.amount, creditTxn.amount) <= 0.01
                val sameRef = !creditTxn.referenceId.isNullOrBlank() &&
                    creditTxn.referenceId.equals(orig.referenceId, ignoreCase = true)

                val score = (if (sameRef) 10 else 0) +
                    (if (sameAmount) 5 else 0) +
                    (if (merchantOverlap) 4 else 0) +
                    (if (sameAccount) 2 else 0)

                if (score >= 4) orig to score else null
            }
            .maxByOrNull { it.second }
            ?.first

        val refundType = when {
            creditTxn.transactionType == "REVERSAL" -> "REVERSAL"
            bestMatch == null -> "UNMATCHED_REFUND"
            MoneyMath.absDiff(bestMatch.amount, creditTxn.amount) <= 0.01 -> "FULL_REFUND"
            else -> "PARTIAL_REFUND"
        }

        val status = when {
            bestMatch == null -> "UNMATCHED_REFUND"
            refundType == "FULL_REFUND" -> "FULL_REFUND"
            refundType == "PARTIAL_REFUND" -> "PARTIAL_REFUND"
            else -> "MATCHED_TO_ORIGINAL"
        }

        val record = RefundRecordEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            refundTransactionId = creditTxn.localId,
            originalTransactionId = bestMatch?.localId,
            refundType = refundType,
            refundAmount = MoneyMath.round2(creditTxn.amount),
            status = status
        )
        return RefundDetectionOutcome(record, bestMatch)
    }

    private fun norm(s: String?): String =
        (s ?: "").lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
}
