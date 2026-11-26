package com.krunal.monzi.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krunal.monzi.data.BudgetEntity
import com.krunal.monzi.data.CategoryEntity
import com.krunal.monzi.ui.MonziUiState
import com.krunal.monzi.ui.components.MonziCard
import com.krunal.monzi.ui.components.StatusPill
import com.krunal.monzi.ui.theme.MonziColors
import com.krunal.monzi.ui.theme.formatInr
import com.krunal.monzi.ui.theme.parseHexColor
import com.krunal.monzi.util.IstTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BudgetsScreen(
    state: MonziUiState,
    onBack: () -> Unit,
    onSelectMonth: (String) -> Unit,
    onSaveBudget: (existingId: String?, name: String, categoryId: String?, monthlyLimit: Double, alertThreshold: Int, onResult: (Boolean, String?) -> Unit) -> Unit,
    onDeleteBudget: (String) -> Unit,
    onAcknowledgeAlert: (String) -> Unit
) {
    var showBudgetDialog by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<BudgetEntity?>(null) }
    var deletingBudget by remember { mutableStateOf<BudgetEntity?>(null) }

    val eval = state.budgetEvaluation
    val curMonth = IstTime.currentMonthKey()
    val prevMonth = IstTime.previousMonthKey(curMonth)
    val twoMonthsAgo = IstTime.previousMonthKey(prevMonth)
    val months = listOf(curMonth, prevMonth, twoMonthsAgo)

    val unackedAlerts = state.budgetAlerts.filter { !it.acknowledged }

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
                title = "Monthly Budgets",
                subtitle = "Net spend after refunds • IST",
                onBack = onBack,
                trailing = {
                    Button(
                        onClick = {
                            editingBudget = null
                            showBudgetDialog = true
                        },
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Budget", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // Month selector
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                months.forEach { mk ->
                    FilterChip(
                        selected = state.selectedBudgetMonth == mk,
                        onClick = { onSelectMonth(mk) },
                        label = {
                            Text(
                                text = if (mk == curMonth) "This Month" else mk,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    )
                }
            }
        }

        // Overall Budget Card
        item {
            val util = (eval.overallUtilizationPct / 100.0).toFloat().coerceIn(0f, 1f)
            val barColor = when {
                eval.overallUtilizationPct >= 100.0 -> MonziColors.Rose
                eval.overallUtilizationPct >= 80.0 -> MonziColors.Amber
                else -> MonziColors.Emerald
            }
            MonziCard {
                Text("Overall Monthly Budget (${state.selectedBudgetMonth})", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Net Spent", fontSize = 11.sp, color = MonziColors.TextSecondary)
                        Text(formatInr(eval.overallNetSpent), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Monthly Target", fontSize = 11.sp, color = MonziColors.TextSecondary)
                        Text(
                            if (eval.overallLimit > 0.0) formatInr(eval.overallLimit) else "No limit set",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                if (eval.overallLimit > 0.0) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { util },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)),
                        color = barColor,
                        trackColor = MonziColors.SurfaceMuted
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${eval.overallUtilizationPct.toInt()}% utilized • Remaining: ${formatInr(eval.overallLimit - eval.overallNetSpent)}",
                        fontSize = 11.sp,
                        color = MonziColors.TextSecondary
                    )
                }
            }
        }

        // Alerts
        if (unackedAlerts.isNotEmpty()) {
            item {
                MonziCard(containerColor = MonziColors.AmberSoft, borderColor = MonziColors.Amber.copy(alpha = 0.4f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = MonziColors.AmberDark)
                        Text("Budget Alerts (${unackedAlerts.size})", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = MonziColors.AmberDark)
                    }
                    Spacer(Modifier.height(8.dp))
                    unackedAlerts.forEach { alert ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(alert.message, fontSize = 12.sp, color = MonziColors.TextPrimary, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onAcknowledgeAlert(alert.id) }) {
                                Icon(Icons.Rounded.Check, contentDescription = "Acknowledge", tint = MonziColors.EmeraldDark)
                            }
                        }
                    }
                }
            }
        }

        // Smart Insights
        if (eval.smartInsights.isNotEmpty()) {
            item {
                MonziCard(containerColor = MonziColors.BlueSoft) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = MonziColors.BluePrimary, modifier = Modifier.size(18.dp))
                        Text("Smart Budget Insights", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = MonziColors.BluePrimary)
                    }
                    Spacer(Modifier.height(6.dp))
                    eval.smartInsights.forEach { insight ->
                        Text("• $insight", fontSize = 12.sp, color = MonziColors.TextPrimary, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }

        // Category Budgets
        if (eval.summaries.isEmpty()) {
            item {
                MonziCard {
                    Text("No active budgets", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Create an overall monthly budget or category spending cap to receive alerts before overspending.", fontSize = 12.sp, color = MonziColors.TextSecondary)
                }
            }
        } else {
            items(eval.summaries, key = { it.budget.id }) { summary ->
                val b = summary.budget
                val catColor = parseHexColor(b.categoryColor, MonziColors.BluePrimary)
                val (pillBg, pillText) = when (summary.healthStatus) {
                    "EXCEEDED" -> MonziColors.RoseSoft to MonziColors.RoseDark
                    "WARNING" -> MonziColors.AmberSoft to MonziColors.AmberDark
                    "APPROACHING" -> MonziColors.BlueSoft to MonziColors.BluePrimary
                    else -> MonziColors.EmeraldSoft to MonziColors.EmeraldDark
                }
                MonziCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(catColor))
                            Column {
                                Text(b.name, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                                Text(b.categoryName ?: "Overall Monthly Cap", fontSize = 11.sp, color = MonziColors.TextSecondary)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(summary.healthStatus, pillBg, pillText)
                            IconButton(onClick = {
                                editingBudget = b
                                showBudgetDialog = true
                            }) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit", modifier = Modifier.size(17.dp))
                            }
                            IconButton(onClick = { deletingBudget = b }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MonziColors.RoseDark, modifier = Modifier.size(17.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (summary.utilizationPercent / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(999.dp)),
                        color = pillText,
                        trackColor = MonziColors.SurfaceMuted
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "Spent ${formatInr(summary.netSpent)} / ${formatInr(summary.monthlyLimit)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            "${summary.utilizationPercent.toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = pillText
                        )
                    }
                    if (summary.refundedAmount > 0.0) {
                        Text("Refund offset applied: -${formatInr(summary.refundedAmount)}", fontSize = 10.sp, color = MonziColors.EmeraldDark)
                    }
                    Text(
                        "Projected month-end: ${formatInr(summary.projectedEndOfMonthSpend)} • Alert at ${b.alertThreshold}%",
                        fontSize = 11.sp,
                        color = MonziColors.TextMuted
                    )
                }
            }
        }
    }

    if (showBudgetDialog) {
        com.krunal.monzi.ui.sheets.BudgetEditorSheet(
            existingBudget = editingBudget,
            categories = state.categories,
            onDismiss = { showBudgetDialog = false },
            onSave = { id, name, catId, limit, threshold, cb ->
                onSaveBudget(id ?: editingBudget?.id, name, catId, limit, threshold) { ok, err ->
                    if (ok) showBudgetDialog = false
                    cb(ok, err)
                }
            }
        )
    }

    deletingBudget?.let { b ->
        com.krunal.monzi.ui.components.MonziConfirmDialog(
            title = "Delete Budget?",
            message = "Remove \"${b.name}\" monthly budget and its limit tracking?",
            confirmLabel = "Delete",
            onConfirm = {
                onDeleteBudget(b.id)
                deletingBudget = null
            },
            onDismiss = { deletingBudget = null },
            isDestructive = true
        )
    }
}
