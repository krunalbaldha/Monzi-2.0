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
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krunal.monzi.data.AnalyticsPeriod
import com.krunal.monzi.ui.MonziUiState
import com.krunal.monzi.ui.components.CashFlowBarChart
import com.krunal.monzi.ui.components.CategoryDonutAndBars
import com.krunal.monzi.ui.components.DailyTrendChart
import com.krunal.monzi.ui.components.MonziCard
import com.krunal.monzi.ui.components.SmoothSparkline
import com.krunal.monzi.ui.theme.MonziColors
import com.krunal.monzi.ui.theme.MonziDimens
import com.krunal.monzi.ui.theme.formatCompactInr
import com.krunal.monzi.ui.theme.formatInr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    state: MonziUiState,
    onSelectPeriod: (AnalyticsPeriod, Long?, Long?) -> Unit
) {
    val analytics = state.periodAnalytics
    var showDatePicker by remember { mutableStateOf(false) }
    var cashChartMode by rememberSaveable { mutableStateOf("ACTIVITY") } // "ACTIVITY" or "6M"

    val primaryPeriods = listOf(
        AnalyticsPeriod.THIS_MONTH,
        AnalyticsPeriod.THREE_MONTHS,
        AnalyticsPeriod.SIX_MONTHS,
        AnalyticsPeriod.ONE_YEAR,
        AnalyticsPeriod.CUSTOM
    )

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
        // Header + A. Period Selection
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text(
                        text = "Financial Analytics",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.4).sp,
                        color = MonziColors.TextPrimary
                    )
                    Text(
                        text = "${analytics.subtitle} • ${analytics.validTxCount} valid transaction(s)",
                        fontSize = 12.sp,
                        color = MonziColors.TextMuted
                    )
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(primaryPeriods) { period ->
                        val selected = state.selectedPeriod == period
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (selected) MonziColors.BluePrimary else MonziColors.GlassWhiteStrong,
                            border = if (selected) null else BorderStroke(1.dp, MonziColors.GlassBorder),
                            modifier = Modifier
                                .shadow(if (selected) 6.dp else 2.dp, RoundedCornerShape(999.dp), ambientColor = Color(0x1A2468EB))
                                .clip(RoundedCornerShape(999.dp))
                                .clickable {
                                    if (period == AnalyticsPeriod.CUSTOM) {
                                        showDatePicker = true
                                    } else {
                                        onSelectPeriod(period, null, null)
                                    }
                                }
                        ) {
                            Text(
                                text = period.label,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (selected) Color.White else MonziColors.TextSecondary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // B. Hero Total Spending Glass Card
        item {
            val expDelta = analytics.expenseChangePct
            val sparkVals = remember(analytics.activityTrend) {
                val vals = analytics.activityTrend.map { it.expense }
                if (vals.size >= 2) vals else state.dashboardStats.dailyTrend14Days.map { it.expense }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(MonziDimens.CardRadius), ambientColor = Color(0x402468EB))
                    .clip(RoundedCornerShape(MonziDimens.CardRadius))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF1D4ED8), MonziColors.BluePrimary, Color(0xFF38BDF8))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(MonziDimens.CardRadius))
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1.25f)) {
                        Text(
                            text = "Total Spending",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = formatInr(analytics.totalExpense),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.6).sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(8.dp))
                        if (expDelta != null) {
                            val down = expDelta <= 0.0
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color.White.copy(alpha = 0.22f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (down) Icons.AutoMirrored.Rounded.TrendingDown else Icons.AutoMirrored.Rounded.TrendingUp,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "${if (expDelta > 0) "+" else ""}${expDelta}% ${analytics.prevLabel}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = analytics.subtitle,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.78f)
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    SmoothSparkline(
                        values = sparkVals,
                        lineColor = Color.White,
                        fillBrush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)
                        ),
                        modifier = Modifier
                            .weight(0.75f)
                            .height(54.dp)
                    )
                }
            }
        }

        // B2. Four Important Financial Summary Metrics
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AnalyticsMetricCard(
                        label = "Total Income",
                        value = formatInr(analytics.totalIncome),
                        sub = analytics.incomeChangePct?.let { "${if (it >= 0) "+" else ""}$it% ${analytics.prevLabel}" } ?: "Net inflow",
                        valueColor = MonziColors.Emerald,
                        containerColor = MonziColors.GlassTintedEmerald,
                        modifier = Modifier.weight(1f)
                    )
                    AnalyticsMetricCard(
                        label = "Total Expenses",
                        value = formatInr(analytics.totalExpense),
                        sub = analytics.expenseChangePct?.let { "${if (it >= 0) "+" else ""}$it% ${analytics.prevLabel}" } ?: "Net outflow",
                        valueColor = MonziColors.Rose,
                        containerColor = MonziColors.GlassTintedRose,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AnalyticsMetricCard(
                        label = "Net Savings",
                        value = formatInr(analytics.netSavings),
                        sub = "Savings rate ${analytics.savingsRate}%",
                        valueColor = if (analytics.netSavings >= 0) MonziColors.BluePrimary else MonziColors.Rose,
                        containerColor = MonziColors.GlassTintedBlue,
                        modifier = Modifier.weight(1f)
                    )
                    AnalyticsMetricCard(
                        label = "Avg Daily Spend",
                        value = formatInr(analytics.avgDailySpend),
                        sub = analytics.highestExpense?.let { "Peak: ${it.merchant ?: "Txn"} (${formatCompactInr(it.amount)})" } ?: "Daily average",
                        valueColor = MonziColors.TextPrimary,
                        containerColor = MonziColors.GlassTintedLavender,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (analytics.refundsTotal > 0 || analytics.cashbackTotal > 0) {
                    Text(
                        text = "Refunds offset: ${formatInr(analytics.refundsTotal)} • Cashback: ${formatInr(analytics.cashbackTotal)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MonziColors.Emerald,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }

        // C. Automated Insights
        if (analytics.insights.isNotEmpty()) {
            item {
                MonziCard(
                    containerColor = MonziColors.GlassTintedBlue,
                    borderColor = MonziColors.BluePrimary.copy(alpha = 0.22f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(MonziColors.BluePrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "Automated Insights",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MonziColors.TextPrimary
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        analytics.insights.forEach { line ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(MonziColors.BluePrimary)
                                )
                                Text(
                                    text = line,
                                    fontSize = 13.sp,
                                    color = MonziColors.TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // D. Cash Activity Chart
        item {
            MonziCard(containerColor = MonziColors.GlassWhiteStrong) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Cash Activity",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MonziColors.TextPrimary
                        )
                        Text(
                            text = if (cashChartMode == "ACTIVITY") "Money coming in vs going out" else "6-Month cash flow",
                            fontSize = 11.sp,
                            color = MonziColors.TextMuted
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MonziColors.SurfaceMuted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { cashChartMode = if (cashChartMode == "ACTIVITY") "6M" else "ACTIVITY" }
                    ) {
                        Text(
                            text = if (cashChartMode == "ACTIVITY") "Period ▾" else "6 Months ▾",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MonziColors.TextPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (cashChartMode == "ACTIVITY") {
                    val activityPoints = analytics.activityTrend.ifEmpty { state.dashboardStats.dailyTrend14Days }
                    DailyTrendChart(points = activityPoints)
                } else {
                    CashFlowBarChart(points = state.dashboardStats.cashFlow6Months)
                }
            }
        }

        // E. Spending Categories (Donut + Bars)
        if (analytics.categoryBreakdown.isNotEmpty()) {
            item {
                MonziCard(containerColor = MonziColors.GlassWhiteStrong) {
                    Text(
                        text = "Spending Categories",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MonziColors.TextPrimary
                    )
                    Text(
                        text = "Distribution across ${analytics.categoryBreakdown.size} active categories",
                        fontSize = 11.sp,
                        color = MonziColors.TextMuted
                    )
                    Spacer(Modifier.height(14.dp))
                    CategoryDonutAndBars(items = analytics.categoryBreakdown)
                }
            }
        }

        // Top Merchants
        if (analytics.topMerchants.isNotEmpty()) {
            item {
                MonziCard(containerColor = MonziColors.GlassWhiteStrong) {
                    Text(
                        text = "Top Merchants",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MonziColors.TextPrimary
                    )
                    Spacer(Modifier.height(10.dp))
                    analytics.topMerchants.forEachIndexed { idx, m ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MonziColors.BlueSoft,
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${idx + 1}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MonziColors.BluePrimary
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = m.merchant,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MonziColors.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${m.count} transaction${if (m.count == 1) "" else "s"}",
                                        fontSize = 11.sp,
                                        color = MonziColors.TextMuted
                                    )
                                }
                            }
                            Text(
                                text = formatInr(m.amount),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MonziColors.TextPrimary
                            )
                        }
                    }
                }
            }
        }

        // Bank-Wise Activity
        if (analytics.bankSummary.isNotEmpty()) {
            item {
                MonziCard(containerColor = MonziColors.GlassWhiteStrong) {
                    Text(
                        text = "Bank-Wise Activity",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MonziColors.TextPrimary
                    )
                    Spacer(Modifier.height(10.dp))
                    analytics.bankSummary.forEach { bank ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MonziColors.BgCanvas,
                            border = BorderStroke(1.dp, MonziColors.BorderLight),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        "${bank.bankName} (${bank.txCount} txns)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MonziColors.TextPrimary
                                    )
                                    Text(
                                        formatInr(bank.balance),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MonziColors.BluePrimary
                                    )
                                }
                                Text(
                                    "In: ${formatInr(bank.totalCredit)} • Out: ${formatInr(bank.totalDebit)}",
                                    fontSize = 11.sp,
                                    color = MonziColors.TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val start = pickerState.selectedStartDateMillis
                    val end = pickerState.selectedEndDateMillis?.let { it + 86_399_999L }
                    if (start != null && end != null) {
                        onSelectPeriod(AnalyticsPeriod.CUSTOM, start, end)
                    }
                    showDatePicker = false
                }) {
                    Text("Apply", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(state = pickerState, modifier = Modifier.height(420.dp))
        }
    }
}

@Composable
private fun AnalyticsMetricCard(
    label: String,
    value: String,
    sub: String,
    valueColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    MonziCard(
        modifier = modifier,
        containerColor = containerColor,
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(14.dp),
        shadowElevation = 4.dp
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MonziColors.TextMuted
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = sub,
            fontSize = 10.sp,
            color = MonziColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
