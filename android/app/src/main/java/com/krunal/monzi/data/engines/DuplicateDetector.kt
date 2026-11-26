package com.krunal.monzi.data.engines

import com.krunal.monzi.data.TransactionEntity
import com.krunal.monzi.util.MoneyMath
import java.util.Locale
import kotlin.math.abs

data class DuplicateMatchResult(
    val status: String, // UNIQUE, EXACT_DUPLICATE, POSSIBLE_DUPLICATE
    val linkedTransactionId: String?,
    val reason: String?
)

object DuplicateDetector {

    private const val POSSIBLE_WINDOW_MS = 10L * 60L * 1000L // 10 minutes

    fun evaluateDuplicate(
        candidate: TransactionEntity,
        existingTxns: List<TransactionEntity>
    ): DuplicateMatchResult {
        if (candidate.duplicateStatus == "CONFIRMED_SEPARATE") {
            return DuplicateMatchResult("CONFIRMED_SEPARATE", null, "User confirmed separate transaction")
        }

        val activeExisting = existingTxns.filter {
            it.localId != candidate.localId &&
                it.transactionStatus != "FAILED" &&
                it.duplicateStatus != "EXACT_DUPLICATE"
        }

        // 1. Exact duplicate by fingerprint
        if (candidate.smsFingerprint.isNotBlank()) {
            val fpMatch = activeExisting.firstOrNull { it.smsFingerprint == candidate.smsFingerprint }
            if (fpMatch != null) {
                return DuplicateMatchResult(
                    status = "EXACT_DUPLICATE",
                    linkedTransactionId = fpMatch.localId,
                    reason = "Identical SMS fingerprint already recorded"
                )
            }
        }

        // 2. Exact duplicate by reference_id + amount + direction
        val ref = candidate.referenceId?.trim()?.lowercase(Locale.US).orEmpty()
        if (ref.length >= 6) {
            val refMatch = activeExisting.firstOrNull {
                it.referenceId?.trim()?.lowercase(Locale.US) == ref &&
                    it.direction.equals(candidate.direction, ignoreCase = true) &&
                    MoneyMath.absDiff(it.amount, candidate.amount) <= 0.01
            }
            if (refMatch != null) {
                return DuplicateMatchResult(
                    status = "EXACT_DUPLICATE",
                    linkedTransactionId = refMatch.localId,
                    reason = "Matching reference ID and amount"
                )
            }
        }

        // 3. Possible duplicate: same account + amount + direction within 10 mins + merchant similarity
        val candMerchant = normalizeMerchant(candidate.merchant)
        val possibleMatch = activeExisting.firstOrNull { existing ->
            val sameAccount = if (!candidate.accountId.isNullOrBlank() && !existing.accountId.isNullOrBlank()) {
                candidate.accountId == existing.accountId
            } else {
                candidate.bankName.equals(existing.bankName, ignoreCase = true) &&
                    candidate.accountMask.equals(existing.accountMask, ignoreCase = true)
            }
            val sameAmount = MoneyMath.absDiff(existing.amount, candidate.amount) <= 0.01
            val sameDirection = existing.direction.equals(candidate.direction, ignoreCase = true)
            val withinWindow = abs(existing.transactionDateMs - candidate.transactionDateMs) <= POSSIBLE_WINDOW_MS
            val existingMerchant = normalizeMerchant(existing.merchant)
            val merchantSimilar = candMerchant.isEmpty() ||
                existingMerchant.isEmpty() ||
                merchantSimilarity(candMerchant, existingMerchant) >= 0.6

            sameAccount && sameAmount && sameDirection && withinWindow && merchantSimilar
        }

        if (possibleMatch != null && possibleMatch.localId != candidate.linkedTransactionId) {
            return DuplicateMatchResult(
                status = "POSSIBLE_DUPLICATE",
                linkedTransactionId = possibleMatch.localId,
                reason = "Same account, amount, and direction within 10 minutes"
            )
        }

        return DuplicateMatchResult("UNIQUE", null, null)
    }

    private fun normalizeMerchant(raw: String?): String =
        (raw ?: "").lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")

    internal fun merchantSimilarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b || a.contains(b) || b.contains(a)) return 1.0
        val maxLen = maxOf(a.length, b.length)
        val prefixLen = a.zip(b).takeWhile { it.first == it.second }.count()
        return prefixLen.toDouble() / maxLen.toDouble()
    }
}
