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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krunal.monzi.data.FinanceAnalyticsEngine
import com.krunal.monzi.data.TransactionEntity
import com.krunal.monzi.ui.MonziUiState
import com.krunal.monzi.ui.components.GlassIconButton
import com.krunal.monzi.ui.components.MonziCard
import com.krunal.monzi.ui.components.TransactionRowItem
import com.krunal.monzi.ui.theme.MonziColors
import com.krunal.monzi.ui.theme.MonziDimens
import com.krunal.monzi.ui.theme.formatIstDateOnly
import com.krunal.monzi.util.IstTime
import kotlin.math.abs

enum class TxSortOption(val label: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    AMOUNT_DESC("Highest Amount"),
    AMOUNT_ASC("Lowest Amount")
}

enum class TxDatePreset(val label: String) {
    ALL("All Dates"),
    THIS_MONTH("This Month"),
    LAST_30("Last 30 Days"),
    LAST_90("Last 90 Days"),
    CUSTOM("Custom Range")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionsScreen(
    state: MonziUiState,
    initialAccountFilterId: String?,
    initialReviewOnly: Boolean = false,
    onClearInitialAccountFilter: () -> Unit,
    onClearReviewFilter: () -> Unit = {},
    onSelectTransaction: (TransactionEntity) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var directionFilter by rememberSaveable { mutableStateOf("ALL") }
    var typeFilter by rememberSaveable { mutableStateOf("ALL") }
    var accountFilterId by remember(initialAccountFilterId) { mutableStateOf(initialAccountFilterId ?: "ALL") }
    var categoryFilterId by rememberSaveable { mutableStateOf("ALL") }
    var reviewOnly by remember(initialReviewOnly) { mutableStateOf(initialReviewOnly) }
    var datePreset by rememberSaveable { mutableStateOf(TxDatePreset.ALL) }
    var customStartMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var customEndMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var sortOption by rememberSaveable { mutableStateOf(TxSortOption.DATE_DESC) }

    var showFilterSheet by remember { mutableStateOf(false) }
    var showDateMenu by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }

    val filtered = remember(
        state.transactions,
        searchQuery,
        directionFilter,
        typeFilter,
        accountFilterId,
        categoryFilterId,
        reviewOnly,
        datePreset,
        customStartMs,
        customEndMs,
        sortOption
    ) {
        val q = searchQuery.trim().lowercase()
        val now = System.currentTimeMillis()
        val (minDate, maxDate) = when (datePreset) {
            TxDatePreset.ALL -> null to null
            TxDatePreset.THIS_MONTH -> IstTime.monthRangeMillis(IstTime.currentMonthKey(now))
            TxDatePreset.LAST_30 -> (now - 30L * 86_400_000L) to now
            TxDatePreset.LAST_90 -> (now - 90L * 86_400_000L) to now
            TxDatePreset.CUSTOM -> customStartMs to customEndMs
        }

        val list = state.transactions.filter { tx ->
            if (directionFilter != "ALL" && tx.direction != directionFilter) return@filter false
            if (typeFilter != "ALL" && tx.transactionType != typeFilter) return@filter false
            if (accountFilterId != "ALL" && tx.accountId != accountFilterId) return@filter false
            if (categoryFilterId != "ALL" && tx.categoryId != categoryFilterId) return@filter false
            if (minDate != null && tx.transactionDateMs < minDate) return@filter false
            if (maxDate != null && tx.transactionDateMs > maxDate) return@filter false
            if (reviewOnly) {
                val needsReview = tx.duplicateStatus == "POSSIBLE_DUPLICATE" ||
                    tx.confidenceScore < 0.70 ||
                    tx.categoryName == "Uncategorized" ||
                    tx.transferStatus == "TRANSFER_CANDIDATE"
                if (!needsReview) return@filter false
            }
            if (q.isNotEmpty()) {
                val hay = "${tx.merchant.orEmpty()} ${tx.notes.orEmpty()} ${tx.referenceId.orEmpty()} ${tx.categoryName.orEmpty()} ${tx.bankName} ${tx.accountMask}".lowercase()
                if (!hay.contains(q)) return@filter false
            }
            true
        }
        when (sortOption) {
            TxSortOption.DATE_DESC -> list.sortedByDescending { it.transactionDateMs }
            TxSortOption.DATE_ASC -> list.sortedBy { it.transactionDateMs }
            TxSortOption.AMOUNT_DESC -> list.sortedByDescending { it.amount }
            TxSortOption.AMOUNT_ASC -> list.sortedBy { it.amount }
        }
    }

    val grouped = remember(filtered) {
        val todayKey = FinanceAnalyticsEngine.toIstDateKey(System.currentTimeMillis())
        val yesterdayKey = FinanceAnalyticsEngine.toIstDateKey(System.currentTimeMillis() - 86_400_000L)
        filtered.groupBy { tx ->
            val key = FinanceAnalyticsEngine.toIstDateKey(tx.transactionDateMs)
            when (key) {
                todayKey -> "Today"
                yesterdayKey -> "Yesterday"
                else -> formatIstDateOnly(tx.transactionDateMs)
            }
        }
    }

    val expDelta = state.dashboardStats.monthlyExpenseChangePct

    Column(modifier = Modifier.fillMaxSize()) {
        // Glass Header + Search + Pills
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MonziDimens.ScreenPadding)
                .padding(top = 12.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Transactions",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.4).sp,
                            color = MonziColors.TextPrimary
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MonziColors.BlueSoft
                        ) {
                            Text(
                                text = "${filtered.size}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MonziColors.BluePrimary,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Text(
                        text = "Manage your transactions & SMS entries",
                        fontSize = 12.sp,
                        color = MonziColors.TextMuted
                    )
                }

                GlassIconButton(
                    icon = Icons.Rounded.Tune,
                    contentDescription = "Advanced Filters & Sort",
                    onClick = { showFilterSheet = true },
                    tint = MonziColors.TextPrimary
                )
            }

            // Glass Pill Search Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x122468EB)),
                shape = RoundedCornerShape(18.dp),
                color = MonziColors.GlassWhiteStrong,
                border = BorderStroke(1.dp, MonziColors.GlassBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MonziColors.TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search merchant, bank, UTR, notes...",
                                fontSize = 13.sp,
                                color = MonziColors.TextMuted
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 14.sp,
                                color = MonziColors.TextPrimary,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(MonziColors.BluePrimary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Clear search",
                            tint = MonziColors.TextMuted,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { searchQuery = "" }
                        )
                    }
                }
            }

            // Primary Filter Pills: All, Debit, Credit, Needs Review
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    TxPillChip(
                        label = "All",
                        selected = !reviewOnly && directionFilter == "ALL",
                        onClick = {
                            directionFilter = "ALL"
                            reviewOnly = false
                            onClearReviewFilter()
                        }
                    )
                }
                item {
                    TxPillChip(
                        label = "Debit",
                        selected = !reviewOnly && directionFilter == "DEBIT",
                        onClick = {
                            directionFilter = "DEBIT"
                            reviewOnly = false
                            onClearReviewFilter()
                        }
                    )
                }
                item {
                    TxPillChip(
                        label = "Credit",
                        selected = !reviewOnly && directionFilter == "CREDIT",
                        onClick = {
                            directionFilter = "CREDIT"
                            reviewOnly = false
                            onClearReviewFilter()
                        }
                    )
                }
                item {
                    TxPillChip(
                        label = "Needs Review",
                        selected = reviewOnly,
                        activeColor = MonziColors.Orange,
                        onClick = {
                            reviewOnly = !reviewOnly
                            if (reviewOnly) {
                                directionFilter = "ALL"
                            } else {
                                onClearReviewFilter()
                            }
                        }
                    )
                }
            }

            // Date Range Selector + Active Extra Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (accountFilterId != "ALL") {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MonziColors.BlueSoft,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable {
                                    accountFilterId = "ALL"
                                    onClearInitialAccountFilter()
                                }
                        ) {
                            Text(
                                text = "Account Filtered ×",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MonziColors.BluePrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (categoryFilterId != "ALL" || typeFilter != "ALL") {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MonziColors.BlueSoft,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable {
                                    categoryFilterId = "ALL"
                                    typeFilter = "ALL"
                                }
                        ) {
                            Text(
                                text = "Clear Extra Filters ×",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MonziColors.BluePrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Box {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MonziColors.GlassWhiteStrong,
                        border = BorderStroke(1.dp, MonziColors.GlassBorder),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { showDateMenu = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Rounded.CalendarMonth,
                                contentDescription = null,
                                tint = MonziColors.BluePrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${datePreset.label} ▾",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MonziColors.TextPrimary
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = showDateMenu,
                        onDismissRequest = { showDateMenu = false },
                        modifier = Modifier.background(MonziColors.GlassWhiteStrong)
                    ) {
                        TxDatePreset.entries.forEach { preset ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = preset.label,
                                        fontWeight = if (datePreset == preset) FontWeight.Bold else FontWeight.Medium,
                                        color = if (datePreset == preset) MonziColors.BluePrimary else MonziColors.TextPrimary
                                    )
                                },
                                onClick = {
                                    showDateMenu = false
                                    if (preset == TxDatePreset.CUSTOM) {
                                        showDateRangePicker = true
                                    } else {
                                        datePreset = preset
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            Column(modifier = Modifier.padding(horizontal = MonziDimens.ScreenPadding, vertical = 8.dp)) {
                MonziCard(containerColor = MonziColors.GlassWhiteStrong) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("No matching transactions", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            "Try adjusting your search query, date range, or filters.",
                            fontSize = 12.sp,
                            color = MonziColors.TextMuted
                        )
                        if (directionFilter != "ALL" || typeFilter != "ALL" || accountFilterId != "ALL" ||
                            categoryFilterId != "ALL" || reviewOnly || datePreset != TxDatePreset.ALL || searchQuery.isNotEmpty()
                        ) {
                            TextButton(onClick = {
                                searchQuery = ""
                                directionFilter = "ALL"
                                typeFilter = "ALL"
                                accountFilterId = "ALL"
                                categoryFilterId = "ALL"
                                reviewOnly = false
                                datePreset = TxDatePreset.ALL
                                customStartMs = null
                                customEndMs = null
                                onClearInitialAccountFilter()
                                onClearReviewFilter()
                            }) {
                                Text("Reset All Filters", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = MonziDimens.ScreenPadding,
                    end = MonziDimens.ScreenPadding,
                    top = 4.dp,
                    bottom = 112.dp
                ),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                grouped.forEach { (dateLabel, itemsForDate) ->
                    item(key = "header_$dateLabel") {
                        Text(
                            text = dateLabel.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            color = MonziColors.TextMuted,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp, start = 4.dp)
                        )
                    }
                    items(itemsForDate, key = { it.localId }) { tx ->
                        TransactionRowItem(tx = tx, onClick = { onSelectTransaction(tx) })
                    }
                }

                if (expDelta != null) {
                    item(key = "tx_insight") {
                        Spacer(Modifier.height(4.dp))
                        MonziCard(
                            containerColor = if (expDelta <= 0.0) MonziColors.GlassTintedEmerald else MonziColors.GlassTintedRose,
                            cornerRadius = 20.dp,
                            contentPadding = PaddingValues(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Insights,
                                    contentDescription = null,
                                    tint = if (expDelta <= 0.0) MonziColors.Emerald else MonziColors.Rose,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = if (expDelta <= 0.0) {
                                            "Spending is down ${abs(expDelta).toInt()}% vs last month"
                                        } else {
                                            "Spending is up ${expDelta.toInt()}% vs last month"
                                        },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MonziColors.TextPrimary
                                    )
                                    Text(
                                        text = if (expDelta <= 0.0) "Great pace keeping outflows below prior month." else "Check Financial Analytics for top drivers.",
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
    }

    if (showFilterSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState,
            containerColor = MonziColors.GlassWhiteStrong,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Filter & Sort", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = MonziColors.TextPrimary)

                Text("Sort By", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MonziColors.TextSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TxSortOption.entries.forEach { opt ->
                        FilterChip(
                            selected = sortOption == opt,
                            onClick = { sortOption = opt },
                            label = { Text(opt.label, fontSize = 11.sp) }
                        )
                    }
                }

                Text("Transaction Type", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MonziColors.TextSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "ALL", "EXPENSE", "INCOME", "TRANSFER", "REFUND",
                        "REVERSAL", "CC_PURCHASE", "CC_BILL_PAYMENT",
                        "ATM_WITHDRAWAL", "BANK_CHARGES", "INTEREST"
                    ).forEach { t ->
                        FilterChip(
                            selected = typeFilter == t,
                            onClick = { typeFilter = t },
                            label = { Text(t.replace("_", " "), fontSize = 10.sp) }
                        )
                    }
                }

                if (state.accounts.isNotEmpty()) {
                    Text("Account", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MonziColors.TextSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = accountFilterId == "ALL",
                            onClick = { accountFilterId = "ALL" },
                            label = { Text("All Accounts", fontSize = 11.sp) }
                        )
                        state.accounts.forEach { acc ->
                            FilterChip(
                                selected = accountFilterId == acc.id,
                                onClick = { accountFilterId = acc.id },
                                label = { Text("${acc.bankName} ${acc.accountNumberMask}", fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Text("Category", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MonziColors.TextSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = categoryFilterId == "ALL",
                        onClick = { categoryFilterId = "ALL" },
                        label = { Text("All Categories", fontSize = 11.sp) }
                    )
                    state.categories.forEach { cat ->
                        FilterChip(
                            selected = categoryFilterId == cat.id,
                            onClick = { categoryFilterId = cat.id },
                            label = { Text(cat.name, fontSize = 11.sp) }
                        )
                    }
                }

                Button(
                    onClick = { showFilterSheet = false },
                    shape = RoundedCornerShape(MonziDimens.ButtonRadius),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Apply Filters", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showDateRangePicker) {
        val pickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val start = pickerState.selectedStartDateMillis
                    val end = pickerState.selectedEndDateMillis?.let { it + 86_399_999L }
                    if (start != null && end != null) {
                        customStartMs = start
                        customEndMs = end
                        datePreset = TxDatePreset.CUSTOM
                    }
                    showDateRangePicker = false
                }) {
                    Text("Apply", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(state = pickerState, modifier = Modifier.height(420.dp))
        }
    }
}

@Composable
private fun TxPillChip(
    label: String,
    selected: Boolean,
    activeColor: Color = MonziColors.BluePrimary,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) activeColor else MonziColors.GlassWhiteStrong,
        border = if (selected) null else BorderStroke(1.dp, MonziColors.GlassBorder),
        modifier = Modifier
            .shadow(if (selected) 6.dp else 2.dp, RoundedCornerShape(999.dp), ambientColor = Color(0x162468EB))
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) Color.White else MonziColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp)
        )
    }
}
