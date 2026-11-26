package com.krunal.monzi.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krunal.monzi.BankSmsParser
import com.krunal.monzi.data.AccountEntity
import com.krunal.monzi.ui.MonziUiState
import com.krunal.monzi.ui.components.MonziCard
import com.krunal.monzi.ui.components.MonziSubScreenTopBar
import com.krunal.monzi.ui.components.StatusPill
import com.krunal.monzi.ui.components.bankCardGradient
import com.krunal.monzi.ui.theme.MonziColors
import com.krunal.monzi.ui.theme.MonziDimens
import com.krunal.monzi.ui.theme.formatCompactInr
import com.krunal.monzi.ui.theme.formatInr
import com.krunal.monzi.ui.theme.formatIstDateTime
import kotlin.math.abs

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AccountsScreen(
    state: MonziUiState,
    onBack: (() -> Unit)? = null,
    onOpenAddAccount: () -> Unit,
    onOpenCreditCards: () -> Unit = {},
    onViewAccountTransactions: (AccountEntity) -> Unit,
    onUpdateAccount: (AccountEntity, String, String?, String, String, Double, (Boolean, String?) -> Unit) -> Unit = { acc, _, _, _, _, bal, cb -> cb(true, null) },
    onUpdateAccountBalance: (AccountEntity, Double) -> Unit,
    onAddAdjustment: (AccountEntity, Double, String, () -> Unit) -> Unit = { _, _, _, done -> done() },
    onDeleteAccount: (AccountEntity) -> Unit
) {
    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var editBankName by remember { mutableStateOf("") }
    var editAccountName by remember { mutableStateOf("") }
    var editLast4 by remember { mutableStateOf("") }
    var editAccountType by remember { mutableStateOf("SAVINGS") }
    var editBalanceText by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf<String?>(null) }
    var editSaving by remember { mutableStateOf(false) }

    var adjustingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var adjAmountText by remember { mutableStateOf("") }
    var adjReasonText by remember { mutableStateOf("") }
    var deletingAccount by remember { mutableStateOf<AccountEntity?>(null) }

    val stats = state.dashboardStats
    val reconciledById = remember(state.reconciledAccounts) {
        state.reconciledAccounts.associateBy { it.account.id }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = MonziDimens.ScreenPadding,
            end = MonziDimens.ScreenPadding,
            top = 12.dp,
            bottom = 112.dp
        ),
        verticalArrangement = Arrangement.spacedBy(MonziDimens.CardSpacing)
    ) {
        // Header
        item {
            val addBtn = @Composable {
                Button(
                    onClick = onOpenAddAccount,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MonziColors.BluePrimary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Account", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (onBack != null) {
                MonziSubScreenTopBar(
                    title = "Accounts & Cards",
                    subtitle = "${state.accounts.size} linked account(s)",
                    onBack = onBack,
                    trailing = addBtn
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Accounts & Cards",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.4).sp,
                            color = MonziColors.TextPrimary
                        )
                        Text(
                            text = "${state.accounts.size} linked account(s)",
                            fontSize = 12.sp,
                            color = MonziColors.TextMuted
                        )
                    }
                    addBtn()
                }
            }
        }

        // A. Net Worth Summary
        if (state.accounts.isNotEmpty()) {
            item {
                MonziCard(containerColor = MonziColors.GlassWhiteStrong) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Net Worth Summary",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MonziColors.TextPrimary
                        )
                        TextButton(onClick = onOpenCreditCards, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("Credit Cards ▸", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NetWorthTile(
                            label = "Total Assets",
                            value = formatInr(stats.totalAssets),
                            icon = Icons.Rounded.AccountBalance,
                            tint = MonziColors.Emerald,
                            iconBg = MonziColors.EmeraldSoft,
                            modifier = Modifier.weight(1f)
                        )
                        NetWorthTile(
                            label = "Credit Dues",
                            value = formatInr(stats.creditOutstanding),
                            icon = Icons.Rounded.CreditCard,
                            tint = MonziColors.Rose,
                            iconBg = MonziColors.RoseSoft,
                            modifier = Modifier.weight(1f)
                        )
                        NetWorthTile(
                            label = "Net Worth",
                            value = formatInr(stats.netWorth),
                            icon = Icons.Rounded.AccountBalanceWallet,
                            tint = if (stats.netWorth >= 0) MonziColors.BluePrimary else MonziColors.AmberDark,
                            iconBg = MonziColors.BlueSoft,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (state.accounts.isEmpty()) {
            item {
                MonziCard(containerColor = MonziColors.GlassWhiteStrong) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.CreditCard,
                            contentDescription = null,
                            tint = MonziColors.BluePrimary,
                            modifier = Modifier.size(40.dp)
                        )
                        Text("No Bank Accounts Linked", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Add your bank accounts manually or scan your SMS inbox for automatic discovery.",
                            fontSize = 12.sp,
                            color = MonziColors.TextMuted
                        )
                        Button(
                            onClick = onOpenAddAccount,
                            shape = RoundedCornerShape(MonziDimens.ButtonRadius)
                        ) {
                            Text("Add First Account", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            items(state.accounts, key = { it.id }) { acc ->
                val rec = reconciledById[acc.id]
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // B. Bank Account Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(12.dp, RoundedCornerShape(MonziDimens.CardRadius), ambientColor = Color(0x302468EB))
                            .clip(RoundedCornerShape(MonziDimens.CardRadius))
                            .background(bankCardGradient(acc.bankName, acc.accountType))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(MonziDimens.CardRadius))
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = acc.bankName,
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${acc.accountType.replace("_", " ")} • •••• ${acc.accountNumberMask.takeLast(4)}",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color.White.copy(alpha = 0.22f)
                                ) {
                                    Text(
                                        text = acc.balanceSource.replace("_", " "),
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column {
                                    Text(
                                        text = "CURRENT BALANCE",
                                        color = Color.White.copy(alpha = 0.72f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = formatInr(acc.currentBalance),
                                        color = Color.White,
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = if (!acc.lastSyncedAtIso.isNullOrBlank()) {
                                            "Synced ${formatIstDateTime(BankSmsParser.parseIsoToMillis(acc.lastSyncedAtIso))}"
                                        } else {
                                            "Local Ledger"
                                        },
                                        color = Color.White.copy(alpha = 0.76f),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }

                    // D. Explicit Balance Reconciliation Card (NEVER hide discrepancies!)
                    if (rec != null) {
                        val hasDiscrepancy = rec.reconciliationStatus == "DISCREPANCY_DETECTED" || abs(rec.discrepancyAmount) > 0.01
                        val statusColor = when (rec.reconciliationStatus) {
                            "RECONCILED" -> MonziColors.Emerald
                            "DISCREPANCY_DETECTED" -> MonziColors.Rose
                            "NEEDS_ACCOUNT_MAPPING" -> MonziColors.Orange
                            else -> MonziColors.BluePrimary
                        }
                        val statusBg = when (rec.reconciliationStatus) {
                            "RECONCILED" -> MonziColors.EmeraldSoft
                            "DISCREPANCY_DETECTED" -> MonziColors.RoseSoft
                            "NEEDS_ACCOUNT_MAPPING" -> MonziColors.OrangeSoft
                            else -> MonziColors.BlueSoft
                        }

                        MonziCard(
                            containerColor = if (hasDiscrepancy) MonziColors.GlassTintedRose else MonziColors.GlassWhiteStrong,
                            borderColor = if (hasDiscrepancy) MonziColors.Rose.copy(alpha = 0.35f) else MonziColors.GlassBorder,
                            cornerRadius = 20.dp,
                            contentPadding = PaddingValues(14.dp),
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (hasDiscrepancy) Icons.Rounded.Warning else Icons.Rounded.Balance,
                                        contentDescription = null,
                                        tint = statusColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Balance Reconciliation",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MonziColors.TextPrimary
                                    )
                                }
                                StatusPill(
                                    label = rec.reconciliationStatus.replace("_", " "),
                                    bgColor = statusBg,
                                    textColor = statusColor
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ReconciliationValueBox(
                                    label = "Bank-Reported",
                                    value = rec.latestSnapshot?.reportedBalance?.let { formatInr(it) } ?: "No SMS",
                                    valueColor = MonziColors.TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                ReconciliationValueBox(
                                    label = "Calc. Ledger",
                                    value = formatInr(rec.calculatedBalance),
                                    valueColor = MonziColors.BluePrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                ReconciliationValueBox(
                                    label = "Discrepancy",
                                    value = if (rec.latestSnapshot == null && acc.accountType != "CREDIT_CARD") {
                                        "—"
                                    } else {
                                        "${if (rec.discrepancyAmount > 0) "+" else ""}${formatInr(rec.discrepancyAmount)}"
                                    },
                                    valueColor = if (hasDiscrepancy) MonziColors.Rose else MonziColors.Emerald,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // C. Account Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (acc.accountType == "CREDIT_CARD") onOpenCreditCards() else onViewAccountTransactions(acc)
                            },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (acc.accountType == "CREDIT_CARD") "Card Details" else "View Txns",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                adjustingAccount = acc
                                adjAmountText = if (rec != null && abs(rec.discrepancyAmount) > 0.01) {
                                    rec.discrepancyAmount.toString()
                                } else ""
                                adjReasonText = "Bank SMS snapshot reconciliation"
                            },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Text("Reconcile", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                editingAccount = acc
                                editBankName = acc.bankName
                                editAccountName = acc.accountName.orEmpty()
                                editLast4 = acc.accountNumberMask.removePrefix("XX").takeLast(4)
                                editAccountType = acc.accountType
                                editBalanceText = acc.currentBalance.toString()
                                editError = null
                                editSaving = false
                            },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp)
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit Account", modifier = Modifier.size(15.dp))
                        }
                        OutlinedButton(
                            onClick = { deletingAccount = acc },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MonziColors.Rose),
                            border = BorderStroke(1.dp, MonziColors.Rose.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp)
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove Account", modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = onOpenAddAccount,
                    shape = RoundedCornerShape(MonziDimens.ButtonRadius),
                    border = BorderStroke(1.dp, MonziColors.GlassBorderSubtle),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Link Another Account", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }

    editingAccount?.let { acc ->
        com.krunal.monzi.ui.sheets.EditAccountSheet(
            account = acc,
            onDismiss = { editingAccount = null },
            onSave = { updated, cb ->
                onUpdateAccount(
                    acc,
                    updated.bankName,
                    updated.accountName,
                    updated.accountNumberMask.removePrefix("XX"),
                    updated.accountType,
                    updated.currentBalance
                ) { ok, err ->
                    cb(ok, err)
                    if (ok) editingAccount = null
                }
            }
        )
    }

    deletingAccount?.let { acc ->
        com.krunal.monzi.ui.components.MonziConfirmDialog(
            title = "Remove ${acc.bankName}?",
            message = "Remove ${acc.bankName} (${acc.accountNumberMask})? Transactions linked to this account will also be deleted and balances will recalculate.",
            confirmLabel = "Remove",
            onConfirm = {
                onDeleteAccount(acc)
                deletingAccount = null
            },
            onDismiss = { deletingAccount = null },
            isDestructive = true
        )
    }

    adjustingAccount?.let { acc ->
        val rec = reconciledById[acc.id]
        if (rec != null) {
            com.krunal.monzi.ui.sheets.ReconcileAccountSheet(
                summary = rec,
                onDismiss = { adjustingAccount = null },
                onCreateAdjustment = { _, amt, reason ->
                    if (amt != 0.0) {
                        onAddAdjustment(acc, amt, reason) {
                            adjustingAccount = null
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun NetWorthTile(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    iconBg: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MonziColors.BgCanvas,
        border = BorderStroke(1.dp, MonziColors.BorderLight)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
            }
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = MonziColors.TextMuted,
                maxLines = 1
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReconciliationValueBox(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MonziColors.BgCanvas,
        border = BorderStroke(1.dp, MonziColors.BorderLight)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = MonziColors.TextMuted,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
