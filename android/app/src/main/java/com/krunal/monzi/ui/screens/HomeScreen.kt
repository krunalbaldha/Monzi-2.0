package com.krunal.monzi.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krunal.monzi.data.AccountEntity
import com.krunal.monzi.data.TransactionEntity
import com.krunal.monzi.ui.MonziUiState
import com.krunal.monzi.ui.components.GlassIconButton
import com.krunal.monzi.ui.components.MonziCard
import com.krunal.monzi.ui.components.SmoothSparkline
import com.krunal.monzi.ui.components.SpendingTrendBarChart
import com.krunal.monzi.ui.components.StatusPill
import com.krunal.monzi.ui.components.TransactionRowItem
import com.krunal.monzi.ui.components.bankCardGradient
import com.krunal.monzi.ui.theme.MonziColors
import com.krunal.monzi.ui.theme.MonziDimens
import com.krunal.monzi.ui.theme.formatCompactInr
import com.krunal.monzi.ui.theme.formatInr
import com.krunal.monzi.util.IstTime
import com.krunal.monzi.util.MoneyMath
import kotlin.math.abs

@Composable
fun HomeScreen(
    state: MonziUiState,
    onRefresh: () -> Unit,
    onOpenChecklist: () -> Unit,
    onOpenSmsCenter: () -> Unit,
    onSyncQueueNow: () -> Unit,
    onOpenAddTransaction: () -> Unit,
    onOpenAddAccount: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenBudgets: () -> Unit = {},
    onOpenCreditCards: () -> Unit = {},
    onOpenReviewTransactions: () -> Unit = onNavigateToTransactions,
    onAcknowledgeAlert: (String) -> Unit = {},
    onSelectTransaction: (TransactionEntity) -> Unit
) {
    val stats = state.dashboardStats
    var balanceVisible by rememberSaveable { mutableStateOf(true) }
    var trendMode by rememberSaveable { mutableStateOf("14D") } // "14D" or "6M"
    var showAlertsDialog by remember { mutableStateOf(false) }

    val unackedAlerts = state.budgetAlerts.filter { !it.acknowledged }
    val reviewItemsCount = remember(state.transactions) {
        state.transactions.count {
            it.duplicateStatus == "POSSIBLE_DUPLICATE" ||
                it.confidenceScore < 0.70 ||
                it.categoryName == "Uncategorized" ||
                it.transferStatus == "TRANSFER_CANDIDATE"
        }
    }
    val firstName = state.profile?.fullName?.trim()?.split(" ")?.firstOrNull().orEmpty().ifBlank { "there" }
    val initialChar = firstName.firstOrNull()?.uppercaseChar()?.toString() ?: "M"

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
        // A. Header: Greeting, Date, Notification Icon, Profile Shortcut
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${IstTime.greetingForHour()},",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MonziColors.TextSecondary
                        )
                        Text(
                            text = firstName,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp,
                            color = MonziColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = IstTime.formatBannerDate(),
                            fontSize = 12.sp,
                            color = MonziColors.TextMuted,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassIconButton(
                            icon = Icons.Rounded.Refresh,
                            contentDescription = "Sync & Refresh",
                            onClick = onRefresh,
                            tint = if (state.isSyncing) MonziColors.BluePrimary else MonziColors.TextSecondary
                        )
                        GlassIconButton(
                            icon = Icons.Rounded.Notifications,
                            contentDescription = "Notifications & Alerts",
                            onClick = { showAlertsDialog = true },
                            tint = if (unackedAlerts.isNotEmpty()) MonziColors.AmberDark else MonziColors.TextPrimary,
                            badgeCount = unackedAlerts.size
                        )
                        // Profile Shortcut Avatar
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .shadow(6.dp, CircleShape, ambientColor = Color(0x33059669))
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(MonziColors.BluePrimary, Color(0xFF0EA5E9))
                                    )
                                )
                                .border(2.dp, Color.White, CircleShape)
                                .clickable(onClick = onOpenSettings),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initialChar,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                // Live sync & Setup Checklist Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatusPill(
                        label = when {
                            !state.isOnline -> "Offline • Cached"
                            state.isSyncing -> "Syncing..."
                            state.hasSmsPermissions -> "SMS Live • Synced"
                            else -> "SMS Paused"
                        },
                        bgColor = when {
                            !state.isOnline -> MonziColors.AmberSoft
                            state.hasSmsPermissions -> MonziColors.EmeraldSoft
                            else -> MonziColors.BlueSoft
                        },
                        textColor = when {
                            !state.isOnline -> MonziColors.AmberDark
                            state.hasSmsPermissions -> MonziColors.Emerald
                            else -> MonziColors.BluePrimary
                        },
                        dotColor = when {
                            !state.isOnline -> MonziColors.Amber
                            state.hasSmsPermissions -> MonziColors.Emerald
                            else -> MonziColors.BluePrimary
                        }
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MonziColors.GlassWhiteStrong,
                        border = BorderStroke(1.dp, MonziColors.GlassBorder),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(onClick = onOpenChecklist)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Checklist,
                                contentDescription = null,
                                tint = MonziColors.BluePrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "Setup",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MonziColors.BluePrimary
                            )
                        }
                    }
                }
            }
        }

        // B. Total Balance Glass Card (Balance, Visibility Toggle, Sparkline, % Change)
        item {
            val changePct = stats.balanceChangePct
            val isPositive = (changePct ?: 0.0) >= 0.0
            MonziCard(
                containerColor = MonziColors.GlassWhiteStrong,
                contentPadding = PaddingValues(20.dp)
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
                        Text(
                            text = "Total Balance",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MonziColors.TextSecondary
                        )
                        IconButton(
                            onClick = { balanceVisible = !balanceVisible },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (balanceVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                contentDescription = if (balanceVisible) "Hide balance" else "Show balance",
                                tint = MonziColors.TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (changePct != null) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (isPositive) MonziColors.EmeraldSoft else MonziColors.RoseSoft
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPositive) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
                                    contentDescription = null,
                                    tint = if (isPositive) MonziColors.Emerald else MonziColors.Rose,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "${if (isPositive) "+" else ""}${changePct}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPositive) MonziColors.Emerald else MonziColors.Rose
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text(
                            text = if (balanceVisible) formatInr(stats.totalBalance) else "₹ ••••••",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.8).sp,
                            color = MonziColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${state.accounts.size} linked account${if (state.accounts.size == 1) "" else "s"} • Today ${if (balanceVisible) formatCompactInr(stats.today.expenses) else "••"} spent",
                            fontSize = 11.sp,
                            color = MonziColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    SmoothSparkline(
                        values = stats.balanceSparkline,
                        lineColor = if (isPositive) MonziColors.Emerald else MonziColors.Rose,
                        fillBrush = Brush.verticalGradient(
                            colors = listOf(
                                (if (isPositive) MonziColors.Emerald else MonziColors.Rose).copy(alpha = 0.24f),
                                Color.Transparent
                            )
                        ),
                        modifier = Modifier
                            .weight(0.8f)
                            .height(48.dp)
                    )
                }
            }
        }

        // C. Quick Actions (4 supported Monzi shortcuts)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionTile(
                    label = "Add Txn",
                    icon = Icons.Rounded.Add,
                    bgColor = MonziColors.GlassTintedBlue,
                    iconTint = MonziColors.BluePrimary,
                    onClick = onOpenAddTransaction,
                    modifier = Modifier.weight(1f)
                )
                QuickActionTile(
                    label = "Scan SMS",
                    icon = Icons.AutoMirrored.Rounded.Message,
                    bgColor = MonziColors.GlassTintedLavender,
                    iconTint = MonziColors.LavenderAccent,
                    onClick = onOpenSmsCenter,
                    modifier = Modifier.weight(1f)
                )
                QuickActionTile(
                    label = "Budgets",
                    icon = Icons.Rounded.PieChart,
                    bgColor = MonziColors.GlassTintedEmerald,
                    iconTint = MonziColors.Emerald,
                    onClick = onOpenBudgets,
                    modifier = Modifier.weight(1f)
                )
                QuickActionTile(
                    label = "Accounts",
                    icon = Icons.Rounded.AccountBalanceWallet,
                    bgColor = MonziColors.GlassWhiteStrong,
                    iconTint = MonziColors.TextPrimary,
                    onClick = onNavigateToAccounts,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // D. Quick Financial Overview (4 compact indicators)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quick Overview",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MonziColors.TextPrimary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(onClick = onNavigateToAnalytics)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "This Month",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MonziColors.TextMuted
                        )
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MonziColors.TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickOverviewTile(
                        label = "Income",
                        value = if (balanceVisible) formatCompactInr(stats.monthlyIncome) else "••••",
                        subText = stats.monthlyIncomeChangePct?.let { "${if (it >= 0) "+" else ""}${it.toInt()}%" } ?: "Month",
                        subColor = MonziColors.Emerald,
                        icon = Icons.Rounded.ArrowDownward,
                        iconBg = Color(0xFFD1FAE5),
                        cardBg = MonziColors.GlassTintedEmerald,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToAnalytics
                    )
                    QuickOverviewTile(
                        label = "Expense",
                        value = if (balanceVisible) formatCompactInr(stats.monthlyExpense) else "••••",
                        subText = stats.monthlyExpenseChangePct?.let { "${if (it >= 0) "+" else ""}${it.toInt()}%" } ?: "Month",
                        subColor = MonziColors.Rose,
                        icon = Icons.Rounded.ArrowUpward,
                        iconBg = Color(0xFFFFE4E6),
                        cardBg = MonziColors.GlassTintedRose,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToAnalytics
                    )
                    QuickOverviewTile(
                        label = "Savings",
                        value = if (balanceVisible) formatCompactInr(stats.netSavings) else "••••",
                        subText = "${stats.savingsRate.toInt()}% rate",
                        subColor = MonziColors.BluePrimary,
                        icon = Icons.Rounded.Savings,
                        iconBg = Color(0xFFDBEAFE),
                        cardBg = MonziColors.GlassTintedBlue,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToAnalytics
                    )
                    QuickOverviewTile(
                        label = "Net Worth",
                        value = if (balanceVisible) formatCompactInr(stats.netWorth) else "••••",
                        subText = if (stats.creditOutstanding > 0.0) "-${formatCompactInr(stats.creditOutstanding)} CC" else "Assets",
                        subColor = MonziColors.LavenderAccent,
                        icon = Icons.Rounded.AccountBalance,
                        iconBg = Color(0xFFEDE9FE),
                        cardBg = MonziColors.GlassTintedLavender,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToAccounts
                    )
                }
            }
        }

        // E. Spending Trend Card
        item {
            val chartLabels = if (trendMode == "14D") {
                stats.dailyTrend14Days.map { it.label }
            } else {
                stats.cashFlow6Months.map { it.label }
            }
            val chartValues = if (trendMode == "14D") {
                stats.dailyTrend14Days.map { it.expense }
            } else {
                stats.cashFlow6Months.map { it.expense }
            }

            MonziCard(containerColor = MonziColors.GlassWhiteStrong) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Spending Trend",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MonziColors.TextPrimary
                        )
                        Text(
                            text = if (trendMode == "14D") "Daily outflow (last 14 days)" else "Monthly outflow (last 6 months)",
                            fontSize = 11.sp,
                            color = MonziColors.TextMuted
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MonziColors.SurfaceMuted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { trendMode = if (trendMode == "14D") "6M" else "14D" }
                    ) {
                        Text(
                            text = if (trendMode == "14D") "14 Days ▾" else "6 Months ▾",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MonziColors.TextPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                SpendingTrendBarChart(
                    labels = chartLabels,
                    values = chartValues
                )
            }
        }

        // Pending Sync Banner (shown only when items are queued)
        if (state.pendingSyncCount > 0) {
            item {
                MonziCard(
                    containerColor = MonziColors.AmberSoft.copy(alpha = 0.9f),
                    borderColor = MonziColors.Amber.copy(alpha = 0.35f),
                    cornerRadius = 20.dp,
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${state.pendingSyncCount} item(s) waiting to sync",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MonziColors.AmberDark
                            )
                            Text(
                                "Captured locally by SMS engine • Ready for cloud sync",
                                fontSize = 11.sp,
                                color = MonziColors.TextSecondary
                            )
                        }
                        Button(
                            onClick = onSyncQueueNow,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MonziColors.AmberDark),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Sync Now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Needs Review Banner (when duplicates / low confidence / uncategorized exist)
        if (reviewItemsCount > 0) {
            item {
                MonziCard(
                    containerColor = MonziColors.OrangeSoft.copy(alpha = 0.92f),
                    borderColor = MonziColors.Orange.copy(alpha = 0.35f),
                    cornerRadius = 20.dp,
                    contentPadding = PaddingValues(14.dp),
                    onClick = onOpenReviewTransactions
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = MonziColors.Orange,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    "$reviewItemsCount transaction(s) need review",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MonziColors.Orange
                                )
                                Text(
                                    "Possible duplicates, transfers, or uncategorized",
                                    fontSize = 11.sp,
                                    color = MonziColors.TextSecondary
                                )
                            }
                        }
                        Text(
                            "Review",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MonziColors.BluePrimary
                        )
                    }
                }
            }
        }

        // Budgets & Credit Cards Quick Glass Row
        item {
            val budgetEval = state.budgetEvaluation
            val ccCards = state.creditCardSummaries
            val totalCcOutstanding = MoneyMath.sum(ccCards.map { it.outstandingAmount })

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MonziCard(
                    modifier = Modifier.weight(1f),
                    cornerRadius = 22.dp,
                    contentPadding = PaddingValues(14.dp),
                    onClick = onOpenBudgets
                ) {
                    Text("Monthly Budgets", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MonziColors.TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (budgetEval.summaries.isEmpty()) "Set limits"
                        else "${budgetEval.overallUtilizationPct.toInt()}% used",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (budgetEval.overallUtilizationPct >= 100.0) MonziColors.Rose else MonziColors.BluePrimary
                    )
                    Spacer(Modifier.height(6.dp))
                    if (budgetEval.overallLimit > 0.0) {
                        LinearProgressIndicator(
                            progress = { (budgetEval.overallUtilizationPct / 100.0).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(999.dp)),
                            color = if (budgetEval.overallUtilizationPct >= 100.0) MonziColors.Rose else MonziColors.Emerald,
                            trackColor = MonziColors.SurfaceMuted
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${formatCompactInr(budgetEval.overallNetSpent)} / ${formatCompactInr(budgetEval.overallLimit)}",
                            fontSize = 10.sp,
                            color = MonziColors.TextMuted
                        )
                    } else {
                        Text(
                            "${budgetEval.summaries.size} active rule(s)",
                            fontSize = 11.sp,
                            color = MonziColors.TextMuted
                        )
                    }
                }

                MonziCard(
                    modifier = Modifier.weight(1f),
                    cornerRadius = 22.dp,
                    contentPadding = PaddingValues(14.dp),
                    onClick = onOpenCreditCards
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.CreditCard,
                            contentDescription = null,
                            tint = MonziColors.IndigoAccent,
                            modifier = Modifier.size(15.dp)
                        )
                        Text("Credit Cards", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MonziColors.TextPrimary)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (balanceVisible) formatCompactInr(totalCcOutstanding) else "••••",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (totalCcOutstanding > 0.0) MonziColors.Rose else MonziColors.Emerald
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (ccCards.isEmpty()) "Track card bills" else "${ccCards.size} card(s) tracked",
                        fontSize = 11.sp,
                        color = MonziColors.TextMuted
                    )
                }
            }
        }

        // Linked Accounts Carousel
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Accounts & Cards (${state.accounts.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MonziColors.TextPrimary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onOpenAddAccount) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = onNavigateToAccounts) {
                            Text("View All", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (state.accounts.isEmpty()) {
                    MonziCard(onClick = onOpenAddAccount) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Rounded.CreditCard, contentDescription = null, tint = MonziColors.BluePrimary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("No Accounts Linked Yet", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Add an account or scan your SMS inbox to auto-discover.", fontSize = 12.sp, color = MonziColors.TextMuted)
                            }
                        }
                    }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.accounts, key = { it.id }) { acc ->
                            CompactAccountCard(
                                account = acc,
                                balanceVisible = balanceVisible,
                                onClick = onNavigateToAccounts
                            )
                        }
                    }
                }
            }
        }

        // Recent Transactions
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Recent Transactions",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MonziColors.TextPrimary
                )
                TextButton(onClick = onNavigateToTransactions) {
                    Text("View All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (state.transactions.isEmpty()) {
            item {
                MonziCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("No transactions recorded yet", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            "Scan your SMS inbox or tap + below to add a transaction.",
                            fontSize = 12.sp,
                            color = MonziColors.TextMuted
                        )
                        Button(
                            onClick = onOpenSmsCenter,
                            shape = RoundedCornerShape(MonziDimens.ButtonRadius)
                        ) {
                            Text("Open SMS Sync Center", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            items(state.transactions.take(6), key = { it.localId }) { tx ->
                TransactionRowItem(tx = tx, onClick = { onSelectTransaction(tx) })
            }
        }
    }

    if (showAlertsDialog) {
        com.krunal.monzi.ui.components.MonziHalfBottomSheet(
            title = "Budget & Finance Alerts",
            subtitle = "Real-time notifications in IST",
            onDismiss = { showAlertsDialog = false }
        ) {
            if (unackedAlerts.isEmpty()) {
                Text(
                    "No unread alerts right now. All budgets and accounts look healthy.",
                    fontSize = 13.5.sp,
                    color = MonziColors.TextSecondary
                )
            } else {
                unackedAlerts.forEach { alert ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MonziColors.AmberSoft,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(alert.message, fontSize = 12.5.sp, color = MonziColors.TextPrimary, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onAcknowledgeAlert(alert.id) }) {
                                Text("Dismiss", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    label: String,
    icon: ImageVector,
    bgColor: Color,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = modifier
            .shadow(4.dp, shape, ambientColor = Color(0x142468EB), spotColor = Color(0x10111827))
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = bgColor,
        border = BorderStroke(1.dp, MonziColors.GlassBorder)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MonziColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickOverviewTile(
    label: String,
    value: String,
    subText: String,
    subColor: Color,
    icon: ImageVector,
    iconBg: Color,
    cardBg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = modifier
            .shadow(4.dp, shape, ambientColor = Color(0x122468EB), spotColor = Color(0x0D111827))
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = cardBg,
        border = BorderStroke(1.dp, MonziColors.GlassBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
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
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = subColor,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MonziColors.TextMuted,
                maxLines = 1
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MonziColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subText,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = subColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompactAccountCard(
    account: AccountEntity,
    balanceVisible: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(232.dp)
            .shadow(8.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x282468EB))
            .clip(RoundedCornerShape(22.dp))
            .background(bankCardGradient(account.bankName, account.accountType))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = account.bankName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Rounded.AccountBalance,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Column {
                Text(
                    text = "•••• ${account.accountNumberMask.takeLast(4)}",
                    color = Color.White.copy(alpha = 0.78f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (balanceVisible) formatInr(account.currentBalance) else "₹ •••••",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                )
                Text(
                    text = "${account.balanceSource.replace("_", " ")} • ${account.reconciliationStatus.replace("_", " ")}",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
