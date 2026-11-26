package com.krunal.monzi.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krunal.monzi.BuildConfig
import com.krunal.monzi.data.AccountEntity
import com.krunal.monzi.data.BudgetEntity
import com.krunal.monzi.data.CategoryEntity
import com.krunal.monzi.data.TransactionRuleEntity
import com.krunal.monzi.data.engines.CreditCardSummary
import com.krunal.monzi.data.engines.ReconciledAccountSummary
import com.krunal.monzi.ui.components.MonziFullBottomSheet
import com.krunal.monzi.ui.components.MonziHalfBottomSheet
import com.krunal.monzi.ui.components.categoryVisualFor
import com.krunal.monzi.ui.theme.MonziColors
import com.krunal.monzi.ui.theme.formatInr
import com.krunal.monzi.ui.theme.formatIstDateOnly
import com.krunal.monzi.ui.theme.parseHexColor

@Composable
fun EditAccountSheet(
    account: AccountEntity,
    onDismiss: () -> Unit,
    onSave: (AccountEntity, (Boolean, String?) -> Unit) -> Unit
) {
    var name by remember(account.id) { mutableStateOf(account.accountName ?: "") }
    var bankName by remember(account.id) { mutableStateOf(account.bankName) }
    var limitText by remember(account.id) { mutableStateOf(account.creditLimit?.toString() ?: "") }
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    MonziFullBottomSheet(
        title = "Edit Account Details",
        subtitle = "${account.bankName} ${account.accountNumberMask} • ${account.accountType.replace("_", " ")}",
        onDismiss = { if (!saving) onDismiss() },
        footer = {
            if (!errorMsg.isNullOrBlank()) {
                Text(errorMsg!!, fontSize = 12.sp, color = MonziColors.Rose, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    val trimmedBank = bankName.trim()
                    if (trimmedName.isBlank() || trimmedBank.isBlank()) {
                        errorMsg = "Bank name and account nickname are required"
                        return@Button
                    }
                    saving = true
                    onSave(
                        account.copy(
                            accountName = trimmedName,
                            bankName = trimmedBank,
                            creditLimit = if (account.accountType == "CREDIT_CARD") limitText.toDoubleOrNull() else account.creditLimit,
                            updatedAtMs = System.currentTimeMillis()
                        )
                    ) { ok, err ->
                        saving = false
                        if (!ok) errorMsg = err ?: "Could not update account"
                    }
                },
                enabled = !saving,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(if (saving) "Saving..." else "Save Account", fontWeight = FontWeight.Bold)
            }
        }
    ) {
        OutlinedTextField(
            value = bankName,
            onValueChange = { bankName = it; errorMsg = null },
            label = { Text("Bank Name") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; errorMsg = null },
            label = { Text("Account Nickname") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        if (account.accountType == "CREDIT_CARD") {
            OutlinedTextField(
                value = limitText,
                onValueChange = { limitText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Credit Limit (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ReconcileAccountSheet(
    summary: ReconciledAccountSummary,
    onDismiss: () -> Unit,
    onCreateAdjustment: (accountId: String, amount: Double, reason: String) -> Unit
) {
    val acc = summary.account
    var amountText by remember(acc.id) { mutableStateOf(summary.discrepancyAmount.toString()) }
    var reason by remember(acc.id) { mutableStateOf("Opening / historical balance alignment") }

    MonziFullBottomSheet(
        title = "Reconcile Account Ledger",
        subtitle = "${acc.bankName} ${acc.accountNumberMask}",
        onDismiss = onDismiss,
        footer = {
            Button(
                onClick = {
                    val adj = amountText.trim().toDoubleOrNull() ?: 0.0
                    onCreateAdjustment(acc.id, adj, reason.trim().ifBlank { "Manual adjustment" })
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Approve Balance Adjustment", fontWeight = FontWeight.Bold)
            }
        }
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MonziColors.SurfaceMuted,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ledger Calculated", fontSize = 12.sp, color = MonziColors.TextSecondary)
                    Text(formatInr(summary.calculatedBalance), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MonziColors.TextPrimary)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Latest Bank SMS Snapshot", fontSize = 12.sp, color = MonziColors.TextSecondary)
                    Text(
                        summary.latestSnapshot?.let { formatInr(it.reportedBalance) } ?: "None yet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MonziColors.BluePrimary
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Discrepancy", fontSize = 12.sp, color = MonziColors.TextSecondary)
                    Text(
                        formatInr(summary.discrepancyAmount),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = if (kotlin.math.abs(summary.discrepancyAmount) > 0.01) MonziColors.AmberDark else MonziColors.EmeraldDark
                    )
                }
            }
        }
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' || c == '-' } },
            label = { Text("Adjustment Amount (₹)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = reason,
            onValueChange = { reason = it },
            label = { Text("Adjustment Reason") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BudgetEditorSheet(
    existingBudget: BudgetEntity?,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (existingId: String?, name: String, categoryId: String?, monthlyLimit: Double, alertThreshold: Int, (Boolean, String?) -> Unit) -> Unit
) {
    var name by remember(existingBudget?.id) { mutableStateOf(existingBudget?.name ?: "") }
    var categoryId by remember(existingBudget?.id) { mutableStateOf(existingBudget?.categoryId) }
    var limitText by remember(existingBudget?.id) { mutableStateOf(existingBudget?.monthlyLimit?.toInt()?.toString() ?: "") }
    var threshold by remember(existingBudget?.id) { mutableIntStateOf(existingBudget?.alertThreshold ?: 80) }
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val expenseCats = remember(categories) { categories.filter { !it.isIncome } }

    MonziFullBottomSheet(
        title = if (existingBudget == null) "Create Monthly Budget" else "Edit Budget",
        subtitle = "Evaluated dynamically in IST after refunds",
        onDismiss = { if (!saving) onDismiss() },
        footer = {
            if (!errorMsg.isNullOrBlank()) {
                Text(errorMsg!!, color = MonziColors.Rose, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = {
                    val limit = limitText.trim().toDoubleOrNull() ?: 0.0
                    if (limit <= 0.0) {
                        errorMsg = "Monthly limit must be greater than ₹0"
                        return@Button
                    }
                    saving = true
                    onSave(
                        existingBudget?.id,
                        name.trim().ifBlank { expenseCats.firstOrNull { it.id == categoryId }?.name ?: "Monthly Spend" },
                        categoryId,
                        limit,
                        threshold
                    ) { ok, err ->
                        saving = false
                        if (!ok) errorMsg = err ?: "Could not save budget"
                    }
                },
                enabled = !saving,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(if (saving) "Saving..." else "Save Budget", fontWeight = FontWeight.Bold)
            }
        }
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Budget Title (Optional)") },
            placeholder = { Text("e.g. Dining Out or Overall Monthly") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = limitText,
            onValueChange = { limitText = it.filter { c -> c.isDigit() || c == '.' }; errorMsg = null },
            label = { Text("Monthly Limit (₹)") },
            prefix = { Text("₹ ") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Text("Category Scope", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MonziColors.TextSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = categoryId == null,
                onClick = { categoryId = null },
                label = { Text("Overall Monthly", fontSize = 11.sp) }
            )
            expenseCats.forEach { cat ->
                val vis = categoryVisualFor(cat.name, explicitColorHex = cat.color)
                FilterChip(
                    selected = categoryId == cat.id,
                    onClick = {
                        categoryId = cat.id
                        if (name.isBlank()) name = cat.name
                    },
                    leadingIcon = { Icon(vis.icon, contentDescription = null, tint = vis.accentColor, modifier = Modifier.size(15.dp)) },
                    label = { Text(cat.name, fontSize = 11.sp) }
                )
            }
        }
        Text("Alert Warning Threshold", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MonziColors.TextSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(75, 80, 90).forEach { pct ->
                FilterChip(
                    selected = threshold == pct,
                    onClick = { threshold = pct },
                    label = { Text("$pct%", fontSize = 12.sp) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreditCardEditorSheet(
    existingCard: AccountEntity?,
    onDismiss: () -> Unit,
    onSave: (
        existingCard: AccountEntity?,
        bankName: String,
        last4: String,
        limit: Double?,
        due: Double,
        stmtDay: Int?,
        dueDay: Int?,
        onComplete: (Boolean, String?) -> Unit
    ) -> Unit
) {
    var bank by remember(existingCard?.id) { mutableStateOf(existingCard?.bankName ?: "") }
    var last4 by remember(existingCard?.id) { mutableStateOf(existingCard?.accountNumberMask?.removePrefix("XX") ?: "") }
    var limit by remember(existingCard?.id) { mutableStateOf(existingCard?.creditLimit?.toInt()?.toString() ?: "") }
    var due by remember(existingCard?.id) { mutableStateOf(existingCard?.currentBalance?.toInt()?.toString() ?: "0") }
    var stmtDay by remember(existingCard?.id) { mutableStateOf(existingCard?.statementDay?.toString() ?: "") }
    var dueDay by remember(existingCard?.id) { mutableStateOf(existingCard?.dueDay?.toString() ?: "") }
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    MonziFullBottomSheet(
        title = if (existingCard == null) "Add Credit Card" else "Edit Credit Card",
        subtitle = "Monitor statement due dates & utilization",
        onDismiss = { if (!saving) onDismiss() },
        footer = {
            if (!errorMsg.isNullOrBlank()) {
                Text(errorMsg!!, fontSize = 12.sp, color = MonziColors.Rose, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = {
                    if (bank.isBlank()) {
                        errorMsg = "Bank / Card issuer name is required"
                        return@Button
                    }
                    saving = true
                    onSave(
                        existingCard,
                        bank.trim(),
                        last4.trim(),
                        limit.toDoubleOrNull(),
                        due.toDoubleOrNull() ?: 0.0,
                        stmtDay.toIntOrNull()?.coerceIn(1, 31),
                        dueDay.toIntOrNull()?.coerceIn(1, 31)
                    ) { ok, err ->
                        saving = false
                        if (!ok) errorMsg = err ?: "Could not save credit card"
                    }
                },
                enabled = !saving,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(if (saving) "Saving..." else "Save Credit Card", fontWeight = FontWeight.Bold)
            }
        }
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("HDFC Bank", "ICICI Bank", "Axis Bank", "SBI Card", "Kotak Bank").forEach { preset ->
                FilterChip(selected = bank == preset, onClick = { bank = preset }, label = { Text(preset, fontSize = 11.sp) })
            }
        }
        OutlinedTextField(
            value = bank,
            onValueChange = { bank = it; errorMsg = null },
            label = { Text("Issuer / Bank Name") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = last4,
                onValueChange = { if (it.length <= 4) last4 = it.filter { c -> c.isDigit() } },
                label = { Text("Card Last 4") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = limit,
                onValueChange = { limit = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Credit Limit (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1.2f)
            )
        }
        OutlinedTextField(
            value = due,
            onValueChange = { due = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Current Outstanding / Bill Due (₹)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = stmtDay,
                onValueChange = { stmtDay = it.filter { c -> c.isDigit() }.take(2) },
                label = { Text("Statement Day (1-31)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = dueDay,
                onValueChange = { dueDay = it.filter { c -> c.isDigit() }.take(2) },
                label = { Text("Due Day (1-31)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardPaymentSheet(
    summary: CreditCardSummary,
    bankAccounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onPay: (AccountEntity, AccountEntity?, Double, com.krunal.monzi.data.CreditCardStatementEntity?, (Boolean, String?) -> Unit) -> Unit
) {
    var sourceId by remember { mutableStateOf(bankAccounts.firstOrNull()?.id) }
    var amountText by remember(summary.card.id) {
        mutableStateOf(
            if (summary.totalDueRemaining > 0) summary.totalDueRemaining.toInt().toString()
            else summary.outstandingAmount.toInt().toString()
        )
    }
    var paying by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    MonziFullBottomSheet(
        title = "Pay ${summary.card.bankName} Card Bill",
        subtitle = "Excludes expense double-counting & updates card ledger",
        onDismiss = { if (!paying) onDismiss() },
        footer = {
            if (!errorMsg.isNullOrBlank()) {
                Text(errorMsg!!, fontSize = 12.sp, color = MonziColors.Rose, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = {
                    val amt = amountText.trim().toDoubleOrNull() ?: 0.0
                    if (amt <= 0.0) {
                        errorMsg = "Enter a valid payment amount"
                        return@Button
                    }
                    paying = true
                    onPay(
                        summary.card,
                        bankAccounts.firstOrNull { it.id == sourceId },
                        amt,
                        summary.latestStatement
                    ) { ok, err ->
                        paying = false
                        if (!ok) errorMsg = err ?: "Payment recording failed"
                    }
                },
                enabled = !paying,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MonziColors.Emerald),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(if (paying) "Recording Payment..." else "Record Bill Payment", fontWeight = FontWeight.Bold)
            }
        }
    ) {
        Surface(shape = RoundedCornerShape(14.dp), color = MonziColors.BlueSoft, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Current Card Due", fontSize = 12.sp, color = MonziColors.TextSecondary)
                Text(formatInr(summary.totalDueRemaining), fontWeight = FontWeight.Bold, color = MonziColors.BluePrimary)
            }
        }

        if (summary.totalDueRemaining > 0.0 || summary.minimumDue > 0.0) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (summary.totalDueRemaining > 0.0) {
                    FilterChip(
                        selected = amountText.toDoubleOrNull() == summary.totalDueRemaining,
                        onClick = { amountText = summary.totalDueRemaining.toString() },
                        label = { Text("Total Due (${formatInr(summary.totalDueRemaining)})", fontSize = 11.sp) }
                    )
                }
                if (summary.minimumDue > 0.0) {
                    FilterChip(
                        selected = amountText.toDoubleOrNull() == summary.minimumDue,
                        onClick = { amountText = summary.minimumDue.toString() },
                        label = { Text("Min Due (${formatInr(summary.minimumDue)})", fontSize = 11.sp) }
                    )
                }
            }
        }

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' }; errorMsg = null },
            label = { Text("Payment Amount (₹)") },
            prefix = { Text("₹ ") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Text("Source Bank Account", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MonziColors.TextSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = sourceId == null, onClick = { sourceId = null }, label = { Text("External / Cash", fontSize = 11.sp) })
            bankAccounts.forEach { acc ->
                FilterChip(
                    selected = sourceId == acc.id,
                    onClick = { sourceId = acc.id },
                    label = { Text("${acc.bankName} (${formatInr(acc.currentBalance)})", fontSize = 11.sp) }
                )
            }
        }
    }
}

@Composable
fun StatementDetailHalfSheet(
    summary: CreditCardSummary,
    onDismiss: () -> Unit
) {
    val stmt = summary.latestStatement
    MonziHalfBottomSheet(
        title = "${summary.card.bankName} Statement",
        subtitle = "Card ${summary.card.accountNumberMask}",
        onDismiss = onDismiss
    ) {
        if (stmt != null) {
            Surface(shape = RoundedCornerShape(16.dp), color = MonziColors.SurfaceMuted, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatementLine("Statement Date", formatIstDateOnly(stmt.statementDateMs))
                    StatementLine("Due Date", stmt.dueDateMs?.let { formatIstDateOnly(it) } ?: "Not specified")
                    StatementLine("Total Amount Due", formatInr(stmt.totalDue))
                    StatementLine("Minimum Due", formatInr(stmt.minimumDue))
                    StatementLine("Paid Against Cycle", formatInr(stmt.paidAmount))
                    StatementLine("Remaining Due", formatInr(summary.totalDueRemaining))
                }
            }
        } else {
            Text(
                text = "No credit card statement SMS has been detected for this card yet. Unbilled spends are tracked automatically below.",
                fontSize = 13.sp,
                color = MonziColors.TextSecondary
            )
        }
    }
}

@Composable
private fun StatementLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = MonziColors.TextSecondary)
        Text(value, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MonziColors.TextPrimary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleEditorSheet(
    editingRule: TransactionRuleEntity?,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (existingRule: TransactionRuleEntity?, keyword: String, categoryId: String, priority: Int, (Boolean, String?) -> Unit) -> Unit
) {
    var keyword by remember(editingRule?.id) { mutableStateOf(editingRule?.matchingRule ?: "") }
    var priority by remember(editingRule?.id) { mutableStateOf(editingRule?.priority?.toString() ?: "10") }
    var categoryId by remember(editingRule?.id) { mutableStateOf(editingRule?.categoryId ?: categories.firstOrNull()?.id.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    MonziFullBottomSheet(
        title = if (editingRule == null) "Create Categorization Rule" else "Edit Rule",
        subtitle = "Automatically match merchants during SMS capture",
        onDismiss = { if (!saving) onDismiss() },
        footer = {
            if (!errorMsg.isNullOrBlank()) {
                Text(errorMsg!!, fontSize = 12.sp, color = MonziColors.Rose, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = {
                    val p = priority.trim().toIntOrNull()?.coerceIn(1, 999) ?: 10
                    saving = true
                    onSave(editingRule, keyword.trim(), categoryId, p) { ok, err ->
                        saving = false
                        if (!ok) errorMsg = err ?: "Could not save rule"
                    }
                },
                enabled = !saving && keyword.trim().length >= 2 && categoryId.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(if (saving) "Saving..." else "Save Rule", fontWeight = FontWeight.Bold)
            }
        }
    ) {
        OutlinedTextField(
            value = keyword,
            onValueChange = { keyword = it; errorMsg = null },
            label = { Text("Merchant / SMS Keyword") },
            placeholder = { Text("e.g. SWIGGY, UBER, BESCOM") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = priority,
            onValueChange = { priority = it.filter { c -> c.isDigit() } },
            label = { Text("Priority (higher runs first)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Text("Assign Category", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MonziColors.TextSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.forEach { cat ->
                val vis = categoryVisualFor(cat.name, explicitColorHex = cat.color)
                FilterChip(
                    selected = categoryId == cat.id,
                    onClick = { categoryId = cat.id },
                    leadingIcon = { Icon(vis.icon, contentDescription = null, tint = vis.accentColor, modifier = Modifier.size(15.dp)) },
                    label = { Text(cat.name, fontSize = 11.sp) }
                )
            }
        }
    }
}

@Composable
fun CategoryEditorSheet(
    editingCategory: CategoryEntity?,
    onDismiss: () -> Unit,
    onSave: (existingCategory: CategoryEntity?, name: String, colorHex: String, isIncome: Boolean, (Boolean, String?) -> Unit) -> Unit,
    onDelete: (CategoryEntity, (Boolean, String?) -> Unit) -> Unit
) {
    val colorPresets = listOf("#2563EB", "#10B981", "#F59E0B", "#EC4899", "#8B5CF6", "#EF4444", "#0EA5E9", "#14B8A6")
    var catName by remember(editingCategory?.id) { mutableStateOf(editingCategory?.name ?: "") }
    var catColor by remember(editingCategory?.id) { mutableStateOf(editingCategory?.color ?: colorPresets.first()) }
    var catIsIncome by remember(editingCategory?.id) { mutableStateOf(editingCategory?.isIncome ?: false) }
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val previewVisual = categoryVisualFor(catName.ifBlank { "Other" }, direction = if (catIsIncome) "CREDIT" else "DEBIT", explicitColorHex = catColor)

    MonziFullBottomSheet(
        title = if (editingCategory == null) "Create Category" else "Edit Category",
        subtitle = "Choose icon styling, income/expense type, and color",
        onDismiss = { if (!saving) onDismiss() },
        footer = {
            if (!errorMsg.isNullOrBlank()) {
                Text(errorMsg!!, fontSize = 12.sp, color = MonziColors.Rose, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (editingCategory != null && !editingCategory.name.equals("Uncategorized", ignoreCase = true)) {
                    OutlinedButton(
                        onClick = {
                            saving = true
                            onDelete(editingCategory) { ok, err ->
                                saving = false
                                if (!ok) errorMsg = err ?: "Could not delete category"
                            }
                        },
                        enabled = !saving,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MonziColors.Rose),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Delete", fontWeight = FontWeight.SemiBold)
                    }
                }
                Button(
                    onClick = {
                        saving = true
                        onSave(editingCategory, catName.trim(), catColor, catIsIncome) { ok, err ->
                            saving = false
                            if (!ok) errorMsg = err ?: "Could not save category"
                        }
                    },
                    enabled = !saving && catName.trim().length >= 2,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (saving) "Saving..." else "Save Category", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) {
        Surface(shape = RoundedCornerShape(16.dp), color = MonziColors.SurfaceMuted, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(parseHexColor(catColor).copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(previewVisual.icon, contentDescription = null, tint = parseHexColor(catColor), modifier = Modifier.size(22.dp))
                }
                Column {
                    Text(catName.ifBlank { "Category Preview" }, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MonziColors.TextPrimary)
                    Text(if (catIsIncome) "Income Category" else "Expense Category", fontSize = 11.5.sp, color = MonziColors.TextMuted)
                }
            }
        }
        OutlinedTextField(
            value = catName,
            onValueChange = { catName = it; errorMsg = null },
            label = { Text("Category Name") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Income Category", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MonziColors.TextPrimary)
                Text("Used to group credits separately from expenses", fontSize = 11.sp, color = MonziColors.TextMuted)
            }
            Switch(checked = catIsIncome, onCheckedChange = { catIsIncome = it })
        }
        Text("Color Swatch", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MonziColors.TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            colorPresets.forEach { hex ->
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(parseHexColor(hex))
                        .border(if (catColor == hex) 3.dp else 0.dp, MonziColors.TextPrimary, CircleShape)
                        .clickable { catColor = hex }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportCsvSheet(
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onExport: (startMs: Long?, endMs: Long?, accountId: String?) -> Unit
) {
    var exportDays by remember { mutableStateOf<Int?>(30) }
    var exportAccountId by remember { mutableStateOf<String?>(null) }

    MonziHalfBottomSheet(
        title = "Export Transactions CSV",
        subtitle = "Share ledger-accurate CSV filtered by period and account",
        onDismiss = onDismiss
    ) {
        Text("Date Range", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MonziColors.TextSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null to "All Dates", 30 to "Last 30 Days", 90 to "Last 90 Days").forEach { (days, label) ->
                FilterChip(selected = exportDays == days, onClick = { exportDays = days }, label = { Text(label, fontSize = 11.5.sp) })
            }
        }
        if (accounts.isNotEmpty()) {
            Text("Account Filter", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MonziColors.TextSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = exportAccountId == null, onClick = { exportAccountId = null }, label = { Text("All Accounts", fontSize = 11.sp) })
                accounts.forEach { acc ->
                    FilterChip(selected = exportAccountId == acc.id, onClick = { exportAccountId = acc.id }, label = { Text("${acc.bankName} ${acc.accountNumberMask}", fontSize = 11.sp) })
                }
            }
        }
        Button(
            onClick = {
                val startMs = exportDays?.let { System.currentTimeMillis() - it * 86_400_000L }
                onExport(startMs, null, exportAccountId)
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Generate & Share CSV", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ThemeSelectorSheet(
    currentMode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    MonziHalfBottomSheet(
        title = "Appearance Theme",
        subtitle = "Choose light, obsidian dark, or match your device",
        onDismiss = onDismiss
    ) {
        val options = listOf(
            Triple("SYSTEM", "Match System", Icons.Rounded.PhoneAndroid),
            Triple("LIGHT", "Light Mode", Icons.Rounded.LightMode),
            Triple("DARK", "Obsidian Dark", Icons.Rounded.DarkMode)
        )
        options.forEach { (key, label, icon) ->
            val selected = currentMode.equals(key, ignoreCase = true)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (selected) MonziColors.BlueSoft else MonziColors.SurfaceMuted,
                onClick = { onSelect(key) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = if (selected) MonziColors.BluePrimary else MonziColors.TextSecondary)
                    Text(label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = MonziColors.TextPrimary, modifier = Modifier.weight(1f))
                    if (selected) {
                        Text("Active", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MonziColors.BluePrimary)
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacyInfoSheet(onDismiss: () -> Unit) {
    MonziHalfBottomSheet(
        title = "Privacy & Security",
        subtitle = "Zero third-party SMS brokers",
        onDismiss = onDismiss
    ) {
        Text(
            text = "All bank and UPI SMS parsing runs 100% locally on your device. OTP codes, promotional offers, and non-financial alerts are filtered locally and never uploaded.\n\nCloud backups are encrypted in transit and protected in Supabase with per-user Row Level Security (RLS).",
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
            color = MonziColors.TextSecondary
        )
    }
}

@Composable
fun AboutInfoSheet(onDismiss: () -> Unit) {
    MonziHalfBottomSheet(
        title = "About Monzi",
        subtitle = "Enterprise Personal Finance & SMS Ledger",
        onDismiss = onDismiss
    ) {
        Text(
            text = "Monzi ${BuildConfig.APP_VERSION}\nYour Money. Your Clarity.\n\n• 24-Bank Deterministic On-Device SMS Engine\n• Snapshot & Ledger Balance Reconciliation\n• Credit Card Utilization & Statement Tracking\n• Refund-Aware Budgets & Duplicate Shield\n• Offline-First SQLite/Room + Supabase Cloud Sync",
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
            color = MonziColors.TextSecondary
        )
    }
}
