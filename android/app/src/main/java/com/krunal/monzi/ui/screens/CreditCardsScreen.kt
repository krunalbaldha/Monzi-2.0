package com.krunal.monzi.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Payment
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krunal.monzi.data.AccountEntity
import com.krunal.monzi.data.CreditCardStatementEntity
import com.krunal.monzi.data.TransactionEntity
import com.krunal.monzi.data.engines.CreditCardSummary
import com.krunal.monzi.ui.MonziUiState
import com.krunal.monzi.ui.components.MonziCard
import com.krunal.monzi.ui.components.StatusPill
import com.krunal.monzi.ui.components.TransactionRowItem
import com.krunal.monzi.ui.components.bankCardGradient
import com.krunal.monzi.ui.theme.MonziColors
import com.krunal.monzi.ui.theme.formatInr
import com.krunal.monzi.ui.theme.formatIstDateOnly
import com.krunal.monzi.util.MoneyMath

@Composable
fun CreditCardsScreen(
    state: MonziUiState,
    onBack: () -> Unit,
    onSaveCard: (
        existingCard: AccountEntity?,
        bankName: String,
        accountName: String?,
        last4Digits: String,
        creditLimit: Double?,
        openingOutstanding: Double,
        statementDay: Int?,
        dueDay: Int?,
        onDone: (Boolean, String?) -> Unit
    ) -> Unit,
    onUpsertStatement: (card: AccountEntity, totalDue: Double, minDue: Double, dueDateMs: Long?, onDone: (Boolean, String?) -> Unit) -> Unit,
    onRecordPayment: (card: AccountEntity, sourceAcc: AccountEntity?, amount: Double, stmt: CreditCardStatementEntity?, onDone: (Boolean, String?) -> Unit) -> Unit,
    onDeleteCard: (AccountEntity) -> Unit = {},
    onSelectTransaction: (TransactionEntity) -> Unit
) {
    val cards = state.creditCardSummaries
    val bankAccounts = state.accounts.filter { it.accountType != "CREDIT_CARD" }

    var showCardDialog by remember { mutableStateOf(false) }
    var editingCard by remember { mutableStateOf<AccountEntity?>(null) }
    var deletingCard by remember { mutableStateOf<AccountEntity?>(null) }
    var payingSummary by remember { mutableStateOf<CreditCardSummary?>(null) }
    var statementCard by remember { mutableStateOf<CreditCardSummary?>(null) }
    var expandedCardId by remember { mutableStateOf<String?>(cards.firstOrNull()?.card?.id) }

    val totalOutstanding = MoneyMath.sum(cards.map { it.outstandingAmount })
    val totalLimit = MoneyMath.sum(cards.mapNotNull { it.creditLimit })
    val totalAvail = if (totalLimit > 0.0) maxOf(0.0, MoneyMath.subtract(totalLimit, totalOutstanding)) else null

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = com.krunal.monzi.ui.theme.MonziDimens.ScreenPadding,
            end = com.krunal.monzi.ui.theme.MonziDimens.ScreenPadding,
            top = 12.dp,
            bottom = 112.dp
        ),
        verticalArrangement = Arrangement.spacedBy(com.krunal.monzi.ui.theme.MonziDimens.CardSpacing)
    ) {
        item {
            com.krunal.monzi.ui.components.MonziSubScreenTopBar(
                title = "Credit Cards",
                subtitle = "${cards.size} card(s) tracked",
                onBack = onBack,
                trailing = {
                    Button(
                        onClick = {
                            editingCard = null
                            showCardDialog = true
                        },
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Card", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // Credit Cards Portfolio KPIs
        item {
            MonziCard {
                Text("Credit Summary", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Total Outstanding", fontSize = 11.sp, color = MonziColors.TextSecondary)
                        Text(formatInr(totalOutstanding), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace, color = MonziColors.RoseDark)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Available Credit", fontSize = 11.sp, color = MonziColors.TextSecondary)
                        Text(
                            totalAvail?.let { formatInr(it) } ?: "Set limit",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MonziColors.EmeraldDark
                        )
                    }
                }
            }
        }

        if (cards.isEmpty()) {
            item {
                MonziCard {
                    Text("No credit cards yet", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Credit cards are automatically discovered from card spend/statement SMS, or you can add one manually with your credit limit and billing cycle.",
                        fontSize = 12.sp,
                        color = MonziColors.TextSecondary
                    )
                }
            }
        } else {
            items(cards, key = { it.card.id }) { summary ->
                val card = summary.card
                val isExpanded = expandedCardId == card.id
                val (pillBg, pillText) = when (summary.statementStatus) {
                    "PAID" -> MonziColors.EmeraldSoft to MonziColors.EmeraldDark
                    "OVERDUE" -> MonziColors.RoseSoft to MonziColors.RoseDark
                    "DUE", "PARTIALLY_PAID" -> MonziColors.AmberSoft to MonziColors.AmberDark
                    else -> MonziColors.BlueSoft to MonziColors.BluePrimary
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(bankCardGradient(card.bankName, "CREDIT_CARD"))
                            .clickable { expandedCardId = if (isExpanded) null else card.id }
                            .padding(18.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.CreditCard, contentDescription = null, tint = Color.White)
                                    Column {
                                        Text(card.accountName ?: card.bankName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                                        Text("${card.bankName} • ${card.accountNumberMask}", color = Color.White.copy(alpha = 0.78f), fontSize = 11.sp)
                                    }
                                }
                                StatusPill(summary.statementStatus, pillBg, pillText)
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("OUTSTANDING", color = Color.White.copy(alpha = 0.68f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text(formatInr(summary.outstandingAmount), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("LIMIT", color = Color.White.copy(alpha = 0.68f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        summary.creditLimit?.let { formatInr(it) } ?: "Not set",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            if (summary.creditLimit != null && summary.creditLimit > 0.0) {
                                LinearProgressIndicator(
                                    progress = { (summary.utilizationPercent / 100.0).toFloat().coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
                                    color = if (summary.utilizationPercent > 70.0) MonziColors.Rose else Color.White,
                                    trackColor = Color.White.copy(alpha = 0.25f)
                                )
                                Text(
                                    "Utilization ${summary.utilizationPercent.toInt()}% • Available ${formatInr(summary.availableCredit ?: 0.0)}",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    if (isExpanded) {
                        MonziCard {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { payingSummary = summary },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Rounded.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Pay Bill", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { statementCard = summary },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Rounded.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Statement", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                IconButton(onClick = {
                                    editingCard = card
                                    showCardDialog = true
                                }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit Card")
                                }
                                IconButton(onClick = { deletingCard = card }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete Card", tint = MonziColors.RoseDark)
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            summary.latestStatement?.let { st ->
                                Text("Latest Statement (${st.statementPeriod})", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                Text(
                                    "Total Due: ${formatInr(st.totalDue)} • Min Due: ${formatInr(st.minimumDue)} • Paid: ${formatInr(st.paidAmount)}",
                                    fontSize = 11.sp,
                                    color = MonziColors.TextSecondary
                                )
                                st.dueDateMs?.let {
                                    Text("Due Date: ${formatIstDateOnly(it)}", fontSize = 11.sp, color = MonziColors.RoseDark, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.height(8.dp))
                            }

                            Text("Card Spends & Payments (${summary.cardTransactions.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            if (summary.cardTransactions.isEmpty()) {
                                Text("No transactions linked to this card yet.", fontSize = 12.sp, color = MonziColors.TextMuted)
                            } else {
                                summary.cardTransactions.take(5).forEach { tx ->
                                    Box(modifier = Modifier.padding(vertical = 3.dp)) {
                                        TransactionRowItem(tx = tx, onClick = { onSelectTransaction(tx) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCardDialog) {
        com.krunal.monzi.ui.sheets.CreditCardEditorSheet(
            existingCard = editingCard,
            onDismiss = { showCardDialog = false },
            onSave = { existing, bank, last4, limit, openBal, stmtDay, dueDay, cb ->
                onSaveCard(existing, bank, null, last4, limit, openBal, stmtDay, dueDay) { ok, err ->
                    cb(ok, err)
                    if (ok) showCardDialog = false
                }
            }
        )
    }

    deletingCard?.let { card ->
        com.krunal.monzi.ui.components.MonziConfirmDialog(
            title = "Delete Credit Card?",
            message = "Remove ${card.bankName} (${card.accountNumberMask})? Historical transactions linked to this card will also be cleaned up cleanly.",
            confirmLabel = "Delete",
            onConfirm = {
                onDeleteCard(card)
                deletingCard = null
            },
            onDismiss = { deletingCard = null },
            isDestructive = true
        )
    }

    payingSummary?.let { summary ->
        com.krunal.monzi.ui.sheets.CardPaymentSheet(
            summary = summary,
            bankAccounts = bankAccounts,
            onDismiss = { payingSummary = null },
            onPay = { card, srcAcc, amt, stmt, cb ->
                onRecordPayment(card, srcAcc, amt, stmt) { ok, err ->
                    cb(ok, err)
                    if (ok) payingSummary = null
                }
            }
        )
    }

    statementCard?.let { summary ->
        UpdateStatementSheet(
            summary = summary,
            onDismiss = { statementCard = null },
            onSave = { totalDue, minDue, cb ->
                onUpsertStatement(summary.card, totalDue, minDue, summary.dueDateMs) { ok, err ->
                    cb(ok, err)
                    if (ok) statementCard = null
                }
            }
        )
    }
}

@Composable
private fun UpdateStatementSheet(
    summary: CreditCardSummary,
    onDismiss: () -> Unit,
    onSave: (totalDue: Double, minDue: Double, cb: (Boolean, String?) -> Unit) -> Unit
) {
    var totalText by remember { mutableStateOf((summary.latestStatement?.totalDue ?: summary.outstandingAmount).toInt().toString()) }
    var minText by remember { mutableStateOf((summary.latestStatement?.minimumDue ?: 0.0).toInt().toString()) }
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    com.krunal.monzi.ui.components.MonziFullBottomSheet(
        title = "Update Card Statement",
        subtitle = "${summary.card.bankName} (${summary.card.accountNumberMask})",
        onDismiss = { if (!saving) onDismiss() },
        footer = {
            errorMsg?.let { Text(it, fontSize = 12.sp, color = MonziColors.Rose, fontWeight = FontWeight.SemiBold) }
            Button(
                onClick = {
                    val total = totalText.toDoubleOrNull()
                    val min = minText.trim().ifBlank { "0" }.toDoubleOrNull()
                    when {
                        total == null || total < 0.0 -> errorMsg = "Enter a valid total amount due"
                        min == null || min < 0.0 -> errorMsg = "Enter a valid minimum amount due"
                        min > total -> errorMsg = "Minimum due cannot exceed total due"
                        else -> {
                            saving = true
                            errorMsg = null
                            onSave(total, min) { ok, err ->
                                saving = false
                                if (!ok) errorMsg = err ?: "Unable to save statement"
                            }
                        }
                    }
                },
                enabled = !saving && (totalText.toDoubleOrNull() ?: -1.0) >= 0.0,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text(if (saving) "Saving..." else "Save Statement", fontWeight = FontWeight.Bold) }
        }
    ) {
        OutlinedTextField(
            value = totalText,
            onValueChange = { totalText = it.filter { c -> c.isDigit() || c == '.' }; errorMsg = null },
            label = { Text("Total Amount Due (₹)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = minText,
            onValueChange = { minText = it.filter { c -> c.isDigit() || c == '.' }; errorMsg = null },
            label = { Text("Minimum Amount Due (₹)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
