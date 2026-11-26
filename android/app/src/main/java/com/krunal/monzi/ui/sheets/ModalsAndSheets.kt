package com.krunal.monzi.ui.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krunal.monzi.BankSmsParser
import com.krunal.monzi.InboxScanResult
import com.krunal.monzi.ReprocessResult
import com.krunal.monzi.data.AccountEntity
import com.krunal.monzi.data.CategoryEntity
import com.krunal.monzi.data.TransactionEntity
import com.krunal.monzi.ui.MonziUiState
import com.krunal.monzi.ui.components.MonziConfirmDialog
import com.krunal.monzi.ui.components.MonziFullBottomSheet
import com.krunal.monzi.ui.components.StatusPill
import com.krunal.monzi.ui.components.categoryVisualFor
import com.krunal.monzi.ui.components.resolveTransactionDisplayStatus
import com.krunal.monzi.ui.theme.MonziColors
import com.krunal.monzi.ui.theme.formatInr
import com.krunal.monzi.ui.theme.formatIstDateOnly
import com.krunal.monzi.ui.theme.formatIstDateTime
import com.krunal.monzi.ui.theme.parseHexColor
import com.krunal.monzi.util.IstTime
import com.krunal.monzi.util.SmsDateFilterOption

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionSheet(
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (
        amount: Double,
        direction: String,
        merchant: String,
        accountId: String?,
        categoryId: String?,
        paymentMode: String,
        notes: String?,
        dateMs: Long,
        onComplete: (Boolean, String?) -> Unit
    ) -> Unit
) {
    var direction by remember { mutableStateOf("DEBIT") }
    var amountText by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var paymentMode by remember { mutableStateOf("UPI") }
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id) }
    var selectedDateMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val matchingCategories = remember(categories, direction) {
        val wantIncome = direction == "CREDIT"
        categories.filter { it.isIncome == wantIncome }.ifEmpty { categories }
    }
    var selectedCategoryId by remember(direction, matchingCategories) {
        mutableStateOf(matchingCategories.firstOrNull()?.id ?: categories.firstOrNull()?.id)
    }
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    MonziFullBottomSheet(
        title = "New Transaction",
        subtitle = "Record an expense or income in IST",
        onDismiss = { if (!saving) onDismiss() },
        footer = {
            if (!errorMsg.isNullOrBlank()) {
                Text(
                    text = errorMsg!!,
                    color = MonziColors.Rose,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Button(
                onClick = {
                    val amt = amountText.trim().toDoubleOrNull()
                    if (amt == null || amt <= 0.0) {
                        errorMsg = "Enter a valid amount greater than ₹0"
                        return@Button
                    }
                    if (merchant.isBlank()) {
                        errorMsg = "Enter merchant, recipient, or income source"
                        return@Button
                    }
                    saving = true
                    errorMsg = null
                    onSave(
                        amt,
                        direction,
                        merchant.trim(),
                        selectedAccountId,
                        selectedCategoryId,
                        paymentMode,
                        notes.trim().ifBlank { null },
                        selectedDateMs
                    ) { ok, err ->
                        saving = false
                        if (!ok) errorMsg = err ?: "Could not save transaction"
                    }
                },
                enabled = !saving,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (direction == "CREDIT") MonziColors.Emerald else MonziColors.BluePrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (saving) "Saving..." else if (direction == "CREDIT") "Save Income" else "Save Expense",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) {
        // Direction segmented pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MonziColors.SurfaceMuted)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("DEBIT" to "Expense (-)", "CREDIT" to "Income (+)").forEach { (dir, label) ->
                val selected = direction == dir
                val bg = when {
                    selected && dir == "CREDIT" -> MonziColors.Emerald
                    selected -> MonziColors.BluePrimary
                    else -> Color.Transparent
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(11.dp))
                        .background(bg)
                        .clickable { direction = dir }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (selected) Color.White else MonziColors.TextSecondary
                    )
                }
            }
        }

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' }; errorMsg = null },
            label = { Text("Amount (₹)") },
            prefix = { Text("₹ ", fontWeight = FontWeight.Bold, color = MonziColors.TextSecondary) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = merchant,
            onValueChange = { merchant = it; errorMsg = null },
            label = { Text(if (direction == "CREDIT") "Payer / Employer / Source" else "Merchant / Recipient") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // Date selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Transaction Date",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MonziColors.TextSecondary
            )
            OutlinedButton(
                onClick = { showDatePicker = true },
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(formatIstDateOnly(selectedDateMs), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Grouped Categories with crisp icons
        Text(
            text = if (direction == "CREDIT") "Income Categories" else "Expense Categories",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MonziColors.TextSecondary
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            matchingCategories.forEach { cat ->
                val vis = categoryVisualFor(cat.name, direction = direction, explicitColorHex = cat.color)
                FilterChip(
                    selected = selectedCategoryId == cat.id,
                    onClick = { selectedCategoryId = cat.id },
                    leadingIcon = {
                        Icon(
                            imageVector = vis.icon,
                            contentDescription = null,
                            tint = vis.accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text(cat.name, fontSize = 11.5.sp) }
                )
            }
        }

        if (accounts.isNotEmpty()) {
            Text("Account", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MonziColors.TextSecondary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                accounts.forEach { acc ->
                    FilterChip(
                        selected = selectedAccountId == acc.id,
                        onClick = { selectedAccountId = acc.id },
                        label = { Text("${acc.bankName} ${acc.accountNumberMask}", fontSize = 11.sp) }
                    )
                }
            }
        }

        Text("Payment Mode", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MonziColors.TextSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("UPI", "DEBIT_CARD", "CREDIT_CARD", "CASH", "NETBANKING", "IMPS").forEach { mode ->
                FilterChip(
                    selected = paymentMode == mode,
                    onClick = { paymentMode = mode },
                    label = { Text(mode.replace("_", " "), fontSize = 11.sp) }
                )
            }
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes (Optional)") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showDatePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { picked ->
                        // Anchor to midday IST so timezone shifts never move calendar date
                        selectedDateMs = picked + 12 * 3600_000L - (picked % 86_400_000L)
                    }
                    showDatePicker = false
                }) { Text("Select") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = dpState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionDetailSheet(
    transaction: TransactionEntity,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity> = emptyList(),
    onDismiss: () -> Unit,
    onUpdate: (
        categoryId: String?,
        notes: String?,
        merchant: String?,
        transactionType: String?,
        amount: Double?,
        direction: String?,
        accountId: String?,
        transactionDateMs: Long?,
        onComplete: (Boolean, String?) -> Unit
    ) -> Unit,
    onConfirmSeparate: () -> Unit = {},
    onConfirmTransfer: (Boolean) -> Unit = {},
    onDelete: () -> Unit
) {
    var merchant by remember(transaction.localId) { mutableStateOf(transaction.merchant ?: "") }
    var amountText by remember(transaction.localId) { mutableStateOf(transaction.amount.toString()) }
    var notes by remember(transaction.localId) { mutableStateOf(transaction.notes ?: "") }
    var txType by remember(transaction.localId) { mutableStateOf(transaction.transactionType) }
    var direction by remember(transaction.localId) { mutableStateOf(transaction.direction) }
    var selectedAccountId by remember(transaction.localId) { mutableStateOf(transaction.accountId) }
    var selectedDateMs by remember(transaction.localId) { mutableStateOf(transaction.transactionDateMs) }
    var selectedCatId by remember(transaction.localId) {
        mutableStateOf(
            transaction.categoryId ?: categories.firstOrNull {
                it.name.equals(transaction.categoryName, ignoreCase = true)
            }?.id
        )
    }
    var showAllCategories by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val displayStatus = resolveTransactionDisplayStatus(transaction)
    val relevantCategories = remember(categories, direction, showAllCategories) {
        if (showAllCategories) {
            categories
        } else {
            val wantIncome = direction == "CREDIT"
            val matched = categories.filter { it.isIncome == wantIncome }
            if (matched.isNotEmpty()) matched else categories
        }
    }

    MonziFullBottomSheet(
        title = "Transaction Details",
        subtitle = "${formatIstDateTime(selectedDateMs)} • ${transaction.bankName} ${transaction.accountMask}",
        onDismiss = { if (!saving) onDismiss() },
        footer = {
            if (!errorMsg.isNullOrBlank()) {
                Text(errorMsg!!, fontSize = 12.sp, color = MonziColors.Rose, fontWeight = FontWeight.SemiBold)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    enabled = !saving,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MonziColors.Rose.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MonziColors.Rose),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = {
                        val parsedAmt = amountText.trim().toDoubleOrNull()
                        if (parsedAmt == null || parsedAmt <= 0.0) {
                            errorMsg = "Enter a valid amount greater than ₹0"
                            return@Button
                        }
                        saving = true
                        errorMsg = null
                        onUpdate(
                            selectedCatId,
                            notes.trim().ifBlank { null },
                            merchant.trim().ifBlank { null },
                            txType,
                            parsedAmt,
                            direction,
                            selectedAccountId,
                            selectedDateMs
                        ) { ok, err ->
                            saving = false
                            if (!ok) errorMsg = err ?: "Could not save changes"
                        }
                    },
                    enabled = !saving,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (saving) "Saving..." else "Save Changes", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) {
        // Summary Hero Card
        val heroColor = when {
            txType == "TRANSFER" || txType == "CC_BILL_PAYMENT" -> MonziColors.VioletSoft
            direction == "CREDIT" -> MonziColors.EmeraldSoft
            else -> MonziColors.BlueSoft
        }
        val heroFg = when {
            txType == "TRANSFER" || txType == "CC_BILL_PAYMENT" -> MonziColors.Violet
            direction == "CREDIT" -> MonziColors.EmeraldDark
            else -> MonziColors.BluePrimary
        }
        val previewAmount = amountText.toDoubleOrNull() ?: transaction.amount
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = heroColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = (if (direction == "CREDIT") "+" else "-") + formatInr(previewAmount),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = heroFg
                    )
                    Text(
                        text = transaction.paymentMode,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MonziColors.TextSecondary
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusPill(
                        label = displayStatus.label,
                        bgColor = displayStatus.bgColor(),
                        textColor = displayStatus.fgColor()
                    )
                    StatusPill(
                        label = transaction.transactionSource.replace("_", " "),
                        bgColor = MonziColors.CardSurfaceStrong,
                        textColor = MonziColors.TextSecondary
                    )
                    StatusPill(
                        label = "Conf ${(transaction.confidenceScore * 100).toInt()}%",
                        bgColor = if (transaction.confidenceScore >= 0.70) MonziColors.EmeraldSoft else MonziColors.AmberSoft,
                        textColor = if (transaction.confidenceScore >= 0.70) MonziColors.EmeraldDark else MonziColors.AmberDark
                    )
                }
            }
        }

        if (transaction.duplicateStatus == "POSSIBLE_DUPLICATE" ||
            transaction.transferStatus == "DETECTED" ||
            transaction.refundStatus == "DETECTED"
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MonziColors.AmberSoft,
                border = BorderStroke(1.dp, MonziColors.Amber.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.Warning, contentDescription = null, tint = MonziColors.AmberDark, modifier = Modifier.size(16.dp))
                        Text(
                            text = when {
                                transaction.duplicateStatus == "POSSIBLE_DUPLICATE" -> "Possible Duplicate Detected"
                                transaction.transferStatus == "DETECTED" -> "Detected Potential Self Transfer"
                                else -> "Detected Refund / Reversal Match"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MonziColors.AmberDark
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (transaction.duplicateStatus == "POSSIBLE_DUPLICATE") {
                            OutlinedButton(
                                onClick = onConfirmSeparate,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Keep as Separate", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (transaction.transferStatus == "DETECTED") {
                            OutlinedButton(
                                onClick = { onConfirmTransfer(true) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Confirm Transfer", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { onConfirmTransfer(false) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Not Transfer", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Editable Fields
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' }; errorMsg = null },
                label = { Text("Amount (₹)") },
                prefix = { Text("₹ ") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = { showDatePicker = true },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .height(56.dp)
                    .padding(top = 4.dp)
            ) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(formatIstDateOnly(selectedDateMs), fontSize = 11.5.sp)
            }
        }

        OutlinedTextField(
            value = merchant,
            onValueChange = { merchant = it },
            label = { Text("Merchant / Payee / Source") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes / Memo") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Text("Classification", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MonziColors.TextSecondary)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "EXPENSE" to "Expense (-)",
                "INCOME" to "Income (+)",
                "TRANSFER" to "Self Transfer",
                "REFUND" to "Refund",
                "CC_BILL_PAYMENT" to "CC Bill Pay"
            ).forEach { (typeKey, label) ->
                FilterChip(
                    selected = txType == typeKey,
                    onClick = {
                        txType = typeKey
                        when (typeKey) {
                            "INCOME", "REFUND" -> direction = "CREDIT"
                            "EXPENSE" -> direction = "DEBIT"
                        }
                    },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }

        if (accounts.isNotEmpty()) {
            Text("Account", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MonziColors.TextSecondary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                accounts.forEach { acc ->
                    FilterChip(
                        selected = selectedAccountId == acc.id,
                        onClick = { selectedAccountId = acc.id },
                        label = { Text("${acc.bankName} ${acc.accountNumberMask}", fontSize = 11.sp) }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (showAllCategories) "All Categories"
                else if (direction == "CREDIT") "Income Categories" else "Expense Categories",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MonziColors.TextSecondary
            )
            TextButton(onClick = { showAllCategories = !showAllCategories }) {
                Text(if (showAllCategories) "Filter by Type" else "Show All", fontSize = 11.5.sp)
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            relevantCategories.forEach { cat ->
                val vis = categoryVisualFor(cat.name, direction = direction, explicitColorHex = cat.color)
                FilterChip(
                    selected = selectedCatId == cat.id,
                    onClick = { selectedCatId = cat.id },
                    leadingIcon = {
                        Icon(
                            imageVector = vis.icon,
                            contentDescription = null,
                            tint = vis.accentColor,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    label = { Text(cat.name, fontSize = 11.sp) }
                )
            }
        }

        if (!transaction.notes.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MonziColors.SurfaceMuted,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "TRANSACTION SOURCE & AUDIT (${transaction.sender ?: transaction.bankName})",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MonziColors.TextMuted
                    )
                    Text(
                        text = transaction.notes,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MonziColors.TextSecondary
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { picked ->
                        selectedDateMs = picked + 12 * 3600_000L - (picked % 86_400_000L)
                    }
                    showDatePicker = false
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = dpState)
        }
    }

    if (showDeleteConfirm) {
        MonziConfirmDialog(
            title = "Delete Transaction?",
            message = "Permanently remove ${transaction.merchant ?: "this transaction"} (${formatInr(transaction.amount)})? Your linked account balance and monthly reports will be updated automatically.",
            confirmLabel = "Delete",
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false },
            isDestructive = true
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddAccountSheet(
    onDismiss: () -> Unit,
    onSave: (
        bankName: String,
        last4: String,
        type: String,
        balance: Double,
        onComplete: (Boolean, String?) -> Unit
    ) -> Unit
) {
    var bankName by remember { mutableStateOf("") }
    var last4 by remember { mutableStateOf("") }
    var accType by remember { mutableStateOf("SAVINGS") }
    var balanceText by remember { mutableStateOf("0") }
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    MonziFullBottomSheet(
        title = "Add Bank Account or Card",
        subtitle = "Track balances and SMS messages separately",
        onDismiss = { if (!saving) onDismiss() },
        footer = {
            if (!errorMsg.isNullOrBlank()) {
                Text(errorMsg!!, color = MonziColors.Rose, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = {
                    val bal = balanceText.trim().toDoubleOrNull() ?: 0.0
                    saving = true
                    errorMsg = null
                    onSave(bankName, last4, accType, bal) { ok, err ->
                        saving = false
                        if (!ok) errorMsg = err ?: "Could not save account"
                    }
                },
                enabled = !saving && bankName.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(if (saving) "Creating..." else "Create Account", fontWeight = FontWeight.Bold)
            }
        }
    ) {
        Text("Popular Banks", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MonziColors.TextSecondary)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("HDFC Bank", "ICICI Bank", "SBI", "Axis Bank", "Kotak Bank", "IDFC First", "Cash").forEach { bank ->
                FilterChip(
                    selected = bankName.equals(bank, ignoreCase = true),
                    onClick = { bankName = bank },
                    label = { Text(bank, fontSize = 11.sp) }
                )
            }
        }

        OutlinedTextField(
            value = bankName,
            onValueChange = { bankName = it; errorMsg = null },
            label = { Text("Bank or Issuer Name") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = last4,
                onValueChange = { if (it.length <= 4) last4 = it.filter { c -> c.isDigit() } },
                label = { Text("Last 4 Digits") },
                placeholder = { Text("1234") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = balanceText,
                onValueChange = { balanceText = it.filter { c -> c.isDigit() || c == '.' || c == '-' } },
                label = { Text(if (accType == "CREDIT_CARD") "Current Due (₹)" else "Current Balance (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1.3f)
            )
        }

        Text("Account Type", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MonziColors.TextSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "SAVINGS" to "Savings",
                "CURRENT" to "Current",
                "CREDIT_CARD" to "Credit Card",
                "WALLET" to "Wallet",
                "CASH" to "Cash"
            ).forEach { (typeKey, label) ->
                FilterChip(
                    selected = accType == typeKey,
                    onClick = { accType = typeKey },
                    label = { Text(label, fontSize = 11.5.sp) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SmsSyncCenterSheet(
    state: MonziUiState,
    onDismiss: () -> Unit,
    onRequestSmsPermission: () -> Unit,
    onScanInbox: (Int, Boolean) -> Unit,
    onScanDateRange: (startDateMs: Long, endDateMs: Long, label: String, limit: Int, (InboxScanResult) -> Unit) -> Unit = { _, _, _, _, _ -> },
    onReprocessDateRange: (startDateMs: Long, endDateMs: Long, (ReprocessResult) -> Unit) -> Unit = { _, _, _ -> },
    onPreviewCount: (startDateMs: Long, endDateMs: Long, (Int) -> Unit) -> Unit = { _, _, _ -> },
    onIngestSingle: (String, (Boolean, String) -> Unit) -> Unit,
    onIngestBulk: (List<String>, (Int, Int) -> Unit) -> Unit,
    onRetryFailedSync: () -> Unit = {}
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    var singleInput by remember { mutableStateOf("") }
    var bulkInput by remember { mutableStateOf("") }
    var singleFeedback by remember { mutableStateOf<String?>(null) }

    var selectedOption by remember { mutableStateOf(SmsDateFilterOption.LAST_7_DAYS) }
    var customStartMs by remember { mutableStateOf(IstTime.dayRangeMillis(0).first) }
    var customEndMs by remember { mutableStateOf(IstTime.dayRangeMillis(0).second) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    var previewSmsCount by remember { mutableStateOf<Int?>(null) }
    var isPreviewing by remember { mutableStateOf(false) }
    var syncSummary by remember { mutableStateOf(state.lastScanSummary) }
    var reprocessSummary by remember { mutableStateOf(state.lastReprocessSummary) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val activeRange = remember(selectedOption, customStartMs, customEndMs) {
        val (s, e) = selectedOption.resolveRange(customStartMs, customEndMs)
        validationError = if (selectedOption == SmsDateFilterOption.CUSTOM && customEndMs < customStartMs) {
            "To Date cannot be earlier than From Date"
        } else null
        s to e
    }

    val inspection = remember(singleInput) {
        if (singleInput.isBlank()) null else BankSmsParser.inspect("", singleInput)
    }

    MonziFullBottomSheet(
        title = "SMS Sync & Reprocessing",
        subtitle = if (state.hasSmsPermissions) "IST date-range filtering • On-device deterministic engine"
        else "Grant SMS permissions to read bank transaction alerts",
        onDismiss = onDismiss
    ) {
        // Permission banner
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (state.hasSmsPermissions) MonziColors.EmeraldSoft else MonziColors.AmberSoft,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.hasSmsPermissions) "SMS Inbox Ready" else "Permission Required",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (state.hasSmsPermissions) MonziColors.EmeraldDark else MonziColors.AmberDark
                    )
                    Text(
                        text = "${state.syncState.detectedCount} processed • ${state.pendingSyncCount} pending upload",
                        fontSize = 11.sp,
                        color = MonziColors.TextSecondary
                    )
                }
                if (!state.hasSmsPermissions) {
                    Button(
                        onClick = onRequestSmsPermission,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MonziColors.AmberDark)
                    ) {
                        Text("Allow SMS", fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        }

        val tabs = listOf("Date Range", "Paste SMS", "Bulk", "Logs", "Queue")
        ScrollableTabRow(
            selectedTabIndex = tabIndex,
            containerColor = Color.Transparent,
            edgePadding = 0.dp
        ) {
            tabs.forEachIndexed { idx, label ->
                Tab(
                    selected = tabIndex == idx,
                    onClick = { tabIndex = idx },
                    text = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                )
            }
        }

        when (tabIndex) {
            0 -> {
                Text("Select Period (IST)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MonziColors.TextSecondary)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SmsDateFilterOption.entries.forEach { opt ->
                        FilterChip(
                            selected = selectedOption == opt,
                            onClick = {
                                selectedOption = opt
                                previewSmsCount = null
                                if (opt == SmsDateFilterOption.CUSTOM) showDateRangePicker = true
                            },
                            label = { Text(opt.label, fontSize = 11.sp) }
                        )
                    }
                }

                val (sMs, eMs) = activeRange
                val fromStr = IstTime.toDateKey(sMs)
                val toStr = IstTime.toDateKey(eMs)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MonziColors.BlueSoft,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Selected Range (IST)", fontSize = 11.sp, color = MonziColors.TextSecondary)
                            Text(
                                text = "$fromStr  →  $toStr",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MonziColors.BluePrimary
                            )
                            previewSmsCount?.let { cnt ->
                                Text(
                                    text = "$cnt SMS found on device for this window",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MonziColors.EmeraldDark
                                )
                            }
                        }
                        if (selectedOption == SmsDateFilterOption.CUSTOM) {
                            OutlinedButton(
                                onClick = { showDateRangePicker = true },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Change Dates", fontSize = 11.sp)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    isPreviewing = true
                                    onPreviewCount(sMs, eMs) { count ->
                                        isPreviewing = false
                                        previewSmsCount = count
                                    }
                                },
                                enabled = state.hasSmsPermissions && !isPreviewing && !state.isScanningInbox,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(if (isPreviewing) "..." else "Preview", fontSize = 11.sp)
                            }
                        }
                    }
                }

                if (selectedOption == SmsDateFilterOption.CUSTOM) {
                    OutlinedButton(
                        onClick = {
                            isPreviewing = true
                            onPreviewCount(sMs, eMs) { count ->
                                isPreviewing = false
                                previewSmsCount = count
                            }
                        },
                        enabled = state.hasSmsPermissions && validationError == null && !isPreviewing && !state.isScanningInbox,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isPreviewing) "Counting SMS..." else "Preview Sync Count", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (!validationError.isNullOrBlank()) {
                    Text(validationError!!, color = MonziColors.Rose, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            syncSummary = null
                            reprocessSummary = null
                            onScanDateRange(sMs, eMs, "${selectedOption.label} ($fromStr..$toStr)", 5000) { res ->
                                syncSummary = res
                            }
                        },
                        enabled = state.hasSmsPermissions && !state.isScanningInbox && validationError == null,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                    ) {
                        Text(if (state.isScanningInbox) "Syncing..." else "Sync Selected Range", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            syncSummary = null
                            reprocessSummary = null
                            onReprocessDateRange(sMs, eMs) { res ->
                                reprocessSummary = res
                            }
                        },
                        enabled = state.hasSmsPermissions && !state.isScanningInbox && validationError == null,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(0.9f)
                            .height(48.dp)
                    ) {
                        Text("Reprocess", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                syncSummary?.let { sum ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (sum.status == "FAILED") MonziColors.RoseSoft else MonziColors.EmeraldSoft,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    text = if (sum.status == "FAILED") "Sync Failed" else "Sync Completed",
                                    fontWeight = FontWeight.Bold,
                                    color = if (sum.status == "FAILED") MonziColors.RoseDark else MonziColors.EmeraldDark
                                )
                                StatusPill(
                                    label = sum.status,
                                    bgColor = MonziColors.CardSurfaceStrong,
                                    textColor = if (sum.status == "FAILED") MonziColors.RoseDark else MonziColors.EmeraldDark
                                )
                            }
                            Text("Range: ${sum.dateRangeLabel}", fontSize = 11.sp, color = MonziColors.TextSecondary)
                            SummaryMetricRow("Total SMS Found", "${sum.scanned}")
                            SummaryMetricRow("Successfully Processed", "${sum.financialDetected}")
                            SummaryMetricRow("Newly Imported Transactions", "${sum.newlyQueued}", highlight = true)
                            SummaryMetricRow("Duplicate SMS Skipped", "${sum.duplicateSkipped}")
                            SummaryMetricRow("Failed Transactions", "${sum.failureCount}")
                            SummaryMetricRow("Ignored / Unsupported SMS", "${sum.ignoredCount}")
                        }
                    }
                }

                reprocessSummary?.let { rep ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (rep.status == "FAILED") MonziColors.RoseSoft else MonziColors.BlueSoft,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (rep.status == "FAILED") "Reprocess Failed" else "Reprocess Completed",
                                fontWeight = FontWeight.Bold,
                                color = if (rep.status == "FAILED") MonziColors.RoseDark else MonziColors.BluePrimary
                            )
                            SummaryMetricRow("Total Evaluated", "${rep.totalExamined}")
                            SummaryMetricRow("Transactions Updated", "${rep.updatedCount}", highlight = true)
                            SummaryMetricRow("Newly Discovered", "${rep.newlyDiscovered}")
                            SummaryMetricRow("Duplicates Skipped", "${rep.duplicateCount}")
                            SummaryMetricRow("Reclassified", "${rep.reclassifiedCount}")
                        }
                    }
                }

                OutlinedButton(
                    onClick = { onScanInbox(1500, true) },
                    enabled = state.hasSmsPermissions && !state.isScanningInbox,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Full Inbox Rescan (Latest 1,500 SMS)", fontSize = 12.sp)
                }
            }

            1 -> {
                OutlinedTextField(
                    value = singleInput,
                    onValueChange = { singleInput = it; singleFeedback = null },
                    label = { Text("Paste Bank or UPI SMS") },
                    placeholder = { Text("e.g. HDFC Bank: Rs 49,800.00 credited via NEFT...") },
                    minLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                inspection?.let { res ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (res.status == "VALID") MonziColors.EmeraldSoft else MonziColors.SurfaceMuted,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Preview: ${res.status}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (res.status == "VALID") MonziColors.EmeraldDark else MonziColors.TextSecondary
                            )
                            res.parsed?.let { p ->
                                Text(
                                    text = "${p.bankName} • ${p.transactionType} (${p.direction}) • ${formatInr(p.amount)} • ${p.transactionCategory}",
                                    fontSize = 12.sp,
                                    color = MonziColors.TextPrimary
                                )
                            }
                        }
                    }
                }
                if (!singleFeedback.isNullOrBlank()) {
                    Text(singleFeedback!!, fontSize = 12.sp, color = MonziColors.BluePrimary, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = {
                        onIngestSingle(singleInput) { ok, msg ->
                            singleFeedback = msg
                            if (ok) singleInput = ""
                        }
                    },
                    enabled = singleInput.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Parse & Import SMS", fontWeight = FontWeight.Bold)
                }
            }

            2 -> {
                OutlinedTextField(
                    value = bulkInput,
                    onValueChange = { bulkInput = it },
                    label = { Text("Paste Multiple SMS (One per line)") },
                    minLines = 5,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        val lines = bulkInput.lines().filter { it.isNotBlank() }
                        onIngestBulk(lines) { _, _ -> bulkInput = "" }
                    },
                    enabled = bulkInput.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Process Bulk Batch", fontWeight = FontWeight.Bold)
                }
            }

            3 -> {
                if (state.smsLogs.isEmpty()) {
                    Text("No SMS processing logs recorded yet.", fontSize = 12.sp, color = MonziColors.TextMuted)
                } else {
                    state.smsLogs.take(25).forEach { log ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MonziColors.SurfaceMuted,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(log.sender?.ifBlank { "SMS" } ?: "SMS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MonziColors.TextPrimary)
                                    Text(
                                        listOfNotNull(
                                            log.extractedBank,
                                            log.extractedAmount?.let { formatInr(it) },
                                            log.reason
                                        ).joinToString(" • ").ifBlank { log.extractedType ?: "SMS Entry" },
                                        fontSize = 11.sp,
                                        color = MonziColors.TextSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                StatusPill(
                                    label = log.status,
                                    bgColor = if (log.status == "PROCESSED") MonziColors.EmeraldSoft else MonziColors.CardSurfaceStrong,
                                    textColor = if (log.status == "PROCESSED") MonziColors.EmeraldDark else MonziColors.TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            4 -> {
                if (state.syncQueueItems.isEmpty()) {
                    Text("Cloud sync queue is empty and up to date.", fontSize = 12.sp, color = MonziColors.EmeraldDark, fontWeight = FontWeight.SemiBold)
                } else {
                    if (state.syncQueueItems.any { it.syncStatus == "FAILED" }) {
                        Button(
                            onClick = onRetryFailedSync,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Retry Failed Sync Items")
                        }
                    }
                    state.syncQueueItems.take(25).forEach { q ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MonziColors.SurfaceMuted,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${q.entityType} • ${q.operation}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(q.lastError ?: "Queued (${q.retryCount} retries)", fontSize = 11.sp, color = MonziColors.TextMuted)
                                }
                                StatusPill(
                                    label = q.syncStatus,
                                    bgColor = if (q.syncStatus == "FAILED") MonziColors.RoseSoft else MonziColors.AmberSoft,
                                    textColor = if (q.syncStatus == "FAILED") MonziColors.RoseDark else MonziColors.AmberDark
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDateRangePicker) {
        val drState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = customStartMs,
            initialSelectedEndDateMillis = customEndMs
        )
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val s = drState.selectedStartDateMillis
                    val e = drState.selectedEndDateMillis ?: s
                    if (s != null && e != null) {
                        val (rangeStart, rangeEnd) = IstTime.customDayRangeMillis(
                            s + 12 * 3600_000L - (s % 86_400_000L),
                            e + 12 * 3600_000L - (e % 86_400_000L)
                        )
                        customStartMs = rangeStart
                        customEndMs = rangeEnd
                    }
                    showDateRangePicker = false
                }) { Text("Apply Range") }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(state = drState, modifier = Modifier.height(420.dp))
        }
    }
}

@Composable
private fun SummaryMetricRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = MonziColors.TextSecondary)
        Text(
            text = value,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (highlight) MonziColors.EmeraldDark else MonziColors.TextPrimary
        )
    }
}

@Composable
fun SetupChecklistSheet(
    state: MonziUiState,
    isBatteryOptimizedIgnored: Boolean,
    onDismiss: () -> Unit,
    onGrantSmsPermission: () -> Unit,
    onRequestBatteryWhitelist: () -> Unit,
    onScanInbox: () -> Unit,
    onSyncNow: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val syncToken = state.profile?.deviceSyncToken

    MonziFullBottomSheet(
        title = "Device Setup & Health",
        subtitle = "Keep SMS tracking & cloud sync reliable",
        onDismiss = onDismiss
    ) {
        ChecklistItemRow(
            icon = Icons.Rounded.Inbox,
            title = "SMS Read & Receive Permission",
            subtitle = if (state.hasSmsPermissions) "Granted • Automatic bank capture active" else "Required for reading bank & UPI alerts",
            done = state.hasSmsPermissions,
            actionLabel = if (state.hasSmsPermissions) null else "Grant",
            onAction = onGrantSmsPermission
        )
        ChecklistItemRow(
            icon = Icons.Rounded.BatteryChargingFull,
            title = "Unrestricted Background Battery",
            subtitle = if (isBatteryOptimizedIgnored) "Exempted • Live background sync reliable" else "Recommended so OS doesn't delay SMS capture",
            done = isBatteryOptimizedIgnored,
            actionLabel = if (isBatteryOptimizedIgnored) null else "Allow",
            onAction = onRequestBatteryWhitelist
        )
        ChecklistItemRow(
            icon = Icons.Rounded.Schedule,
            title = "Historical SMS Scan",
            subtitle = "${state.syncState.detectedCount} processed transactions recorded",
            done = state.syncState.detectedCount > 0,
            actionLabel = "Scan Now",
            onAction = onScanInbox
        )
        ChecklistItemRow(
            icon = Icons.Rounded.CloudDone,
            title = "Supabase Cloud Sync",
            subtitle = if (state.pendingSyncCount == 0) "All items synced (${state.accounts.size} accounts)" else "${state.pendingSyncCount} items waiting in queue",
            done = state.pendingSyncCount == 0,
            actionLabel = if (state.pendingSyncCount > 0) "Sync Now" else null,
            onAction = onSyncNow
        )

        if (!syncToken.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MonziColors.SurfaceMuted,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Device Sync Token", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MonziColors.TextMuted)
                        Text(syncToken.take(20) + "...", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MonziColors.TextPrimary)
                    }
                    IconButton(onClick = { clipboard.setText(AnnotatedString(syncToken)) }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy token", tint = MonziColors.BluePrimary)
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MonziColors.BlueSoft,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Shield, contentDescription = null, tint = MonziColors.BluePrimary, modifier = Modifier.size(18.dp))
                Text(
                    text = "OTPs, spam, and promotional SMS never leave your device.",
                    fontSize = 11.5.sp,
                    color = MonziColors.TextPrimary
                )
            }
        }
    }
}

@Composable
private fun ChecklistItemRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    done: Boolean,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MonziColors.CardSurfaceStrong,
        border = BorderStroke(1.dp, MonziColors.GlassBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (done) MonziColors.EmeraldSoft else MonziColors.AmberSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (done) Icons.Rounded.CheckCircle else icon,
                    contentDescription = null,
                    tint = if (done) MonziColors.EmeraldDark else MonziColors.AmberDark,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = MonziColors.TextPrimary)
                Text(subtitle, fontSize = 11.5.sp, color = MonziColors.TextMuted)
            }
            if (!actionLabel.isNullOrBlank()) {
                OutlinedButton(onClick = onAction, shape = RoundedCornerShape(10.dp)) {
                    Text(actionLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
