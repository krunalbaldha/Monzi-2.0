package com.krunal.monzi.data

import com.krunal.monzi.util.IstTime
import com.krunal.monzi.util.MoneyMath
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

enum class AnalyticsPeriod(val label: String) {
    THIS_MONTH("1 Month"),
    PREVIOUS_MONTH("Prev Month"),
    THREE_MONTHS("3 Months"),
    SIX_MONTHS("6 Months"),
    ONE_YEAR("1 Year"),
    CURRENT_YEAR("This Year"),
    FINANCIAL_YEAR("FY"),
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    THIS_WEEK("This Week"),
    CUSTOM("Custom")
}

data class MonthlyCashFlowPoint(
    val monthKey: String,
    val label: String,
    val income: Double,
    val expense: Double
)

data class DailyTrendPoint(
    val dateKey: String,
    val label: String,
    val income: Double,
    val expense: Double
)

data class CategoryBreakdownItem(
    val name: String,
    val amount: Double,
    val percentage: Double,
    val colorHex: String,
    val count: Int
)

data class MerchantRankItem(
    val merchant: String,
    val amount: Double,
    val count: Int
)

data class BankSummaryItem(
    val bankName: String,
    val accountCount: Int,
    val balance: Double,
    val totalCredit: Double,
    val totalDebit: Double,
    val txCount: Int
)

data class TodaySummary(
    val income: Double,
    val expenses: Double,
    val net: Double,
    val transactionCount: Int,
    val topCategory: String?,
    val yesterdayExpenses: Double,
    val expenseDeltaPct: Double?
)

data class DashboardStats(
    val totalBalance: Double,
    val totalAssets: Double,
    val creditOutstanding: Double,
    val netWorth: Double,
    val monthlyIncome: Double,
    val monthlyExpense: Double,
    val netSavings: Double,
    val savingsRate: Double,
    val refundsTotal: Double,
    val cashbackTotal: Double,
    val today: TodaySummary,
    val cashFlow6Months: List<MonthlyCashFlowPoint>,
    val dailyTrend14Days: List<DailyTrendPoint>,
    val categoryBreakdown: List<CategoryBreakdownItem>,
    val bankSummary: List<BankSummaryItem>,
    val balanceSparkline: List<Double> = emptyList(),
    val balanceChangePct: Double? = null,
    val monthlyIncomeChangePct: Double? = null,
    val monthlyExpenseChangePct: Double? = null
)

data class PeriodAnalytics(
    val period: AnalyticsPeriod,
    val subtitle: String,
    val prevLabel: String,
    val totalIncome: Double,
    val totalExpense: Double,
    val netSavings: Double,
    val savingsRate: Double,
    val avgDailySpend: Double,
    val refundsTotal: Double,
    val cashbackTotal: Double,
    val validTxCount: Int,
    val incomeChangePct: Double?,
    val expenseChangePct: Double?,
    val highestExpense: TransactionEntity?,
    val categoryBreakdown: List<CategoryBreakdownItem>,
    val topMerchants: List<MerchantRankItem>,
    val bankSummary: List<BankSummaryItem>,
    val insights: List<String>,
    val activityTrend: List<DailyTrendPoint> = emptyList()
)

object FinanceAnalyticsEngine {

    private val istZone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    fun isExcludedFromTotals(tx: TransactionEntity): Boolean {
        return tx.isExcludedFromTotals ||
            tx.isSelfTransfer ||
            tx.duplicateStatus == "EXACT_DUPLICATE" ||
            tx.transactionType == "TRANSFER" ||
            tx.transactionType == "CC_BILL_PAYMENT" ||
            tx.transactionType == "REFUND" ||
            tx.transactionType == "REVERSAL" ||
            tx.transactionStatus == "FAILED" ||
            tx.transactionStatus == "REVERSED"
    }

    fun isRefund(tx: TransactionEntity): Boolean {
        return (tx.isRefund || tx.transactionType == "REFUND" || tx.transactionType == "REVERSAL" || tx.transactionSource == "REFUND_REVERSAL") &&
            tx.transactionStatus != "FAILED" &&
            tx.duplicateStatus != "EXACT_DUPLICATE"
    }

    fun isCashback(tx: TransactionEntity): Boolean {
        if (tx.direction != "CREDIT") return false
        val cat = (tx.categoryName ?: "").lowercase(Locale.US)
        val merch = (tx.merchant ?: "").lowercase(Locale.US)
        return cat == "cashback" || merch.contains("cashback")
    }

    fun toIstDateKey(millis: Long): String = IstTime.toDateKey(millis)

    fun startOfIstDay(year: Int, month1Based: Int, day: Int): Long {
        val cal = Calendar.getInstance(istZone, Locale.US)
        cal.set(year, month1Based - 1, day, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun endOfIstDay(year: Int, month1Based: Int, day: Int): Long {
        val cal = Calendar.getInstance(istZone, Locale.US)
        cal.set(year, month1Based - 1, day, 23, 59, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }

    fun computeDashboardStats(
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>,
        nowMs: Long = System.currentTimeMillis()
    ): DashboardStats {
        val totalAssets = MoneyMath.sum(accounts.filter { it.accountType != "CREDIT_CARD" }.map { it.currentBalance })
        val creditOutstanding = MoneyMath.sum(accounts.filter { it.accountType == "CREDIT_CARD" }.map { abs(it.currentBalance) })
        val totalBalance = MoneyMath.round2(totalAssets)
        val netWorth = MoneyMath.subtract(totalAssets, creditOutstanding)

        val todayKey = toIstDateKey(nowMs)
        val yesterdayKey = toIstDateKey(nowMs - 86_400_000L)
        val currentMonthKey = todayKey.substring(0, 7)

        val dailyMap = LinkedHashMap<String, DailyTrendPoint>()
        val dayFmt = SimpleDateFormat("d MMM", Locale.US).apply { timeZone = istZone }
        for (i in 13 downTo 0) {
            val ms = nowMs - i * 86_400_000L
            val k = toIstDateKey(ms)
            dailyMap[k] = DailyTrendPoint(k, dayFmt.format(Date(ms)), 0.0, 0.0)
        }

        val monthMap = LinkedHashMap<String, MonthlyCashFlowPoint>()
        val monthFmt = SimpleDateFormat("MMM", Locale.US).apply { timeZone = istZone }
        val monthKeyFmt = SimpleDateFormat("yyyy-MM", Locale.US).apply { timeZone = istZone }
        for (i in 5 downTo 0) {
            val mc = Calendar.getInstance(istZone, Locale.US).apply {
                timeInMillis = nowMs
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, -i)
            }
            val mk = monthKeyFmt.format(mc.time)
            monthMap[mk] = MonthlyCashFlowPoint(mk, monthFmt.format(mc.time), 0.0, 0.0)
        }

        var monthlyIncome = 0.0
        var monthlyGrossExpense = 0.0
        var refundsTotal = 0.0
        var cashbackTotal = 0.0

        var todayIncome = 0.0
        var todayExpenses = 0.0
        var todayTxCount = 0
        var yesterdayExpenses = 0.0
        val todayCategoryTotals = LinkedHashMap<String, Double>()
        val monthlyCatTotals = LinkedHashMap<String, Triple<Double, String, Int>>()
        val monthlyRefundsByCat = LinkedHashMap<String, Double>()

        val txnById = transactions.associateBy { it.localId }
        val bankAccMap = LinkedHashMap<String, BankSummaryItem>()
        val accountIdToBank = HashMap<String, String>()
        for (acc in accounts) {
            accountIdToBank[acc.id] = acc.bankName
            val prev = bankAccMap[acc.bankName] ?: BankSummaryItem(acc.bankName, 0, 0.0, 0.0, 0.0, 0)
            bankAccMap[acc.bankName] = prev.copy(
                accountCount = prev.accountCount + 1,
                balance = MoneyMath.add(prev.balance, if (acc.accountType == "CREDIT_CARD") -abs(acc.currentBalance) else acc.currentBalance)
            )
        }

        for (tx in transactions) {
            if (tx.transactionStatus == "FAILED" || tx.duplicateStatus == "EXACT_DUPLICATE") continue
            val bName = tx.accountId?.let { accountIdToBank[it] } ?: tx.bankName
            val bPrev = bankAccMap[bName] ?: BankSummaryItem(bName, 0, 0.0, 0.0, 0.0, 0)
            bankAccMap[bName] = bPrev.copy(
                totalCredit = MoneyMath.add(bPrev.totalCredit, if (tx.direction == "CREDIT") tx.amount else 0.0),
                totalDebit = MoneyMath.add(bPrev.totalDebit, if (tx.direction == "DEBIT") tx.amount else 0.0),
                txCount = bPrev.txCount + 1
            )

            val dayKey = toIstDateKey(tx.transactionDateMs)
            val monthKey = dayKey.substring(0, 7)

            if (isRefund(tx)) {
                if (monthKey == currentMonthKey) {
                    refundsTotal = MoneyMath.add(refundsTotal, tx.amount)
                    val linkedOrig = tx.linkedTransactionId?.let { txnById[it] }
                    val rCat = linkedOrig?.categoryName ?: tx.categoryName ?: "Uncategorized"
                    monthlyRefundsByCat[rCat] = MoneyMath.add(monthlyRefundsByCat[rCat] ?: 0.0, tx.amount)
                }
                monthMap[monthKey]?.let { mBucket ->
                    monthMap[monthKey] = mBucket.copy(expense = maxOf(0.0, MoneyMath.subtract(mBucket.expense, tx.amount)))
                }
                continue
            }
            if (isExcludedFromTotals(tx)) continue

            val cashbackTx = isCashback(tx)
            if (cashbackTx) {
                if (monthKey == currentMonthKey) cashbackTotal = MoneyMath.add(cashbackTotal, tx.amount)
            }

            if (!cashbackTx) {
                dailyMap[dayKey]?.let { bucket ->
                    dailyMap[dayKey] = if (tx.direction == "CREDIT") {
                        bucket.copy(income = MoneyMath.add(bucket.income, tx.amount))
                    } else {
                        bucket.copy(expense = MoneyMath.add(bucket.expense, tx.amount))
                    }
                }

                monthMap[monthKey]?.let { mBucket ->
                    monthMap[monthKey] = if (tx.direction == "CREDIT") {
                        mBucket.copy(income = MoneyMath.add(mBucket.income, tx.amount))
                    } else {
                        mBucket.copy(expense = MoneyMath.add(mBucket.expense, tx.amount))
                    }
                }
            }

            if (dayKey == todayKey) {
                todayTxCount++
                if (tx.direction == "CREDIT") {
                    if (!cashbackTx) todayIncome = MoneyMath.add(todayIncome, tx.amount)
                } else {
                    todayExpenses = MoneyMath.add(todayExpenses, tx.amount)
                    val catName = tx.categoryName ?: "Uncategorized"
                    todayCategoryTotals[catName] = MoneyMath.add(todayCategoryTotals[catName] ?: 0.0, tx.amount)
                }
            } else if (dayKey == yesterdayKey && tx.direction == "DEBIT") {
                yesterdayExpenses = MoneyMath.add(yesterdayExpenses, tx.amount)
            }

            if (monthKey == currentMonthKey) {
                if (tx.direction == "CREDIT") {
                    if (!cashbackTx) monthlyIncome = MoneyMath.add(monthlyIncome, tx.amount)
                } else {
                    monthlyGrossExpense = MoneyMath.add(monthlyGrossExpense, tx.amount)
                    val catName = tx.categoryName ?: "Uncategorized"
                    val catColor = tx.categoryColor ?: "#94A3B8"
                    val cur = monthlyCatTotals[catName] ?: Triple(0.0, catColor, 0)
                    monthlyCatTotals[catName] = Triple(MoneyMath.add(cur.first, tx.amount), catColor, cur.third + 1)
                }
            }
        }

        // Net expense = Gross expense - refunds this month
        val monthlyExpense = maxOf(0.0, MoneyMath.subtract(monthlyGrossExpense, refundsTotal))
        val netSavings = MoneyMath.subtract(monthlyIncome, monthlyExpense)
        val savingsRate = if (monthlyIncome > 0.0) MoneyMath.round2((netSavings / monthlyIncome) * 100.0) else 0.0
        val expenseDeltaPct = if (yesterdayExpenses > 0.0) {
            MoneyMath.round2(((todayExpenses - yesterdayExpenses) / yesterdayExpenses) * 100.0)
        } else null
        val topCategoryToday = todayCategoryTotals.maxByOrNull { it.value }?.key

        val adjustedCatMap = monthlyCatTotals.entries.mapNotNull { (name, triple) ->
            val refundForCat = monthlyRefundsByCat[name] ?: 0.0
            val netAmt = maxOf(0.0, MoneyMath.subtract(triple.first, refundForCat))
            if (netAmt > 0.0) Triple(name, netAmt, Pair(triple.second, triple.third)) else null
        }
        val catTotalSum = MoneyMath.sum(adjustedCatMap.map { it.second }).coerceAtLeast(1.0)
        val breakdown = adjustedCatMap
            .sortedByDescending { it.second }
            .map { (name, amt, meta) ->
                CategoryBreakdownItem(
                    name = name,
                    amount = amt,
                    percentage = MoneyMath.percentage(amt, catTotalSum),
                    colorHex = meta.first,
                    count = meta.second
                )
            }

        // Previous month totals for MoM comparison
        val prevCal = Calendar.getInstance(istZone, Locale.US).apply {
            timeInMillis = nowMs
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, -1)
        }
        val prevMonthKey = monthKeyFmt.format(prevCal.time)
        val prevMonthFlow = monthMap[prevMonthKey]
        val monthlyIncomeChangePct = if (prevMonthFlow != null && prevMonthFlow.income > 0.0) {
            MoneyMath.round2(((monthlyIncome - prevMonthFlow.income) / prevMonthFlow.income) * 100.0)
        } else null
        val monthlyExpenseChangePct = if (prevMonthFlow != null && prevMonthFlow.expense > 0.0) {
            MoneyMath.round2(((monthlyExpense - prevMonthFlow.expense) / prevMonthFlow.expense) * 100.0)
        } else null

        // Reconstruct 14-day balance trajectory backward from current totalBalance
        val dailyList = dailyMap.values.toList()
        val sparklineReversed = ArrayList<Double>(dailyList.size)
        var runningBal = totalBalance
        for (idx in dailyList.indices.reversed()) {
            sparklineReversed.add(runningBal)
            val pt = dailyList[idx]
            runningBal = MoneyMath.subtract(runningBal, MoneyMath.subtract(pt.income, pt.expense))
        }
        val balanceSparkline = sparklineReversed.reversed()
        val startBal14d = balanceSparkline.firstOrNull() ?: totalBalance
        val balanceChangePct = if (abs(startBal14d) > 0.01 && dailyList.any { it.income > 0.0 || it.expense > 0.0 }) {
            MoneyMath.round2(((totalBalance - startBal14d) / abs(startBal14d)) * 100.0)
        } else null

        return DashboardStats(
            totalBalance = totalBalance,
            totalAssets = MoneyMath.round2(totalAssets),
            creditOutstanding = MoneyMath.round2(creditOutstanding),
            netWorth = netWorth,
            monthlyIncome = MoneyMath.round2(monthlyIncome),
            monthlyExpense = MoneyMath.round2(monthlyExpense),
            netSavings = netSavings,
            savingsRate = savingsRate,
            refundsTotal = MoneyMath.round2(refundsTotal),
            cashbackTotal = MoneyMath.round2(cashbackTotal),
            today = TodaySummary(
                income = MoneyMath.round2(todayIncome),
                expenses = MoneyMath.round2(todayExpenses),
                net = MoneyMath.subtract(todayIncome, todayExpenses),
                transactionCount = todayTxCount,
                topCategory = topCategoryToday,
                yesterdayExpenses = MoneyMath.round2(yesterdayExpenses),
                expenseDeltaPct = expenseDeltaPct
            ),
            cashFlow6Months = monthMap.values.toList(),
            dailyTrend14Days = dailyList,
            categoryBreakdown = breakdown,
            bankSummary = bankAccMap.values.sortedByDescending { it.balance },
            balanceSparkline = balanceSparkline,
            balanceChangePct = balanceChangePct,
            monthlyIncomeChangePct = monthlyIncomeChangePct,
            monthlyExpenseChangePct = monthlyExpenseChangePct
        )
    }

    fun computePeriodAnalytics(
        period: AnalyticsPeriod,
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>,
        customStartMs: Long? = null,
        customEndMs: Long? = null,
        nowMs: Long = System.currentTimeMillis()
    ): PeriodAnalytics {
        val cal = Calendar.getInstance(istZone, Locale.US).apply { timeInMillis = nowMs }
        val curY = cal.get(Calendar.YEAR)
        val curM = cal.get(Calendar.MONTH) + 1
        val curD = cal.get(Calendar.DAY_OF_MONTH)

        val dayFmt = SimpleDateFormat("d MMM", Locale.US).apply { timeZone = istZone }
        val monthYearFmt = SimpleDateFormat("MMMM yyyy", Locale.US).apply { timeZone = istZone }

        val startMs: Long
        val endMs: Long
        val prevStartMs: Long
        val prevEndMs: Long
        val days: Int
        val subtitle: String
        val prevLabel: String

        when (period) {
            AnalyticsPeriod.TODAY -> {
                startMs = startOfIstDay(curY, curM, curD)
                endMs = endOfIstDay(curY, curM, curD)
                prevStartMs = startMs - 86_400_000L
                prevEndMs = endMs - 86_400_000L
                days = 1
                subtitle = "Today (${dayFmt.format(Date(startMs))})"
                prevLabel = "vs Yesterday"
            }
            AnalyticsPeriod.YESTERDAY -> {
                val yCal = Calendar.getInstance(istZone, Locale.US).apply {
                    timeInMillis = nowMs
                    add(Calendar.DAY_OF_MONTH, -1)
                }
                startMs = startOfIstDay(yCal.get(Calendar.YEAR), yCal.get(Calendar.MONTH) + 1, yCal.get(Calendar.DAY_OF_MONTH))
                endMs = endOfIstDay(yCal.get(Calendar.YEAR), yCal.get(Calendar.MONTH) + 1, yCal.get(Calendar.DAY_OF_MONTH))
                prevStartMs = startMs - 86_400_000L
                prevEndMs = endMs - 86_400_000L
                days = 1
                subtitle = "Yesterday (${dayFmt.format(Date(startMs))})"
                prevLabel = "vs Day Before"
            }
            AnalyticsPeriod.THIS_WEEK -> {
                val dow = cal.get(Calendar.DAY_OF_WEEK)
                val offsetFromMon = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
                val wCal = Calendar.getInstance(istZone, Locale.US).apply {
                    timeInMillis = nowMs
                    add(Calendar.DAY_OF_MONTH, -offsetFromMon)
                }
                startMs = startOfIstDay(wCal.get(Calendar.YEAR), wCal.get(Calendar.MONTH) + 1, wCal.get(Calendar.DAY_OF_MONTH))
                endMs = endOfIstDay(curY, curM, curD)
                prevStartMs = startMs - 7 * 86_400_000L
                prevEndMs = startMs - 1L
                days = offsetFromMon + 1
                subtitle = "This Week (${dayFmt.format(Date(startMs))} – Today)"
                prevLabel = "vs Last Week"
            }
            AnalyticsPeriod.PREVIOUS_MONTH -> {
                val pmCal = Calendar.getInstance(istZone, Locale.US).apply {
                    timeInMillis = nowMs
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, -1)
                }
                val pmY = pmCal.get(Calendar.YEAR)
                val pmM = pmCal.get(Calendar.MONTH) + 1
                val pmDays = pmCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                startMs = startOfIstDay(pmY, pmM, 1)
                endMs = endOfIstDay(pmY, pmM, pmDays)

                val pm2Cal = (pmCal.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                prevStartMs = startOfIstDay(pm2Cal.get(Calendar.YEAR), pm2Cal.get(Calendar.MONTH) + 1, 1)
                prevEndMs = endOfIstDay(pm2Cal.get(Calendar.YEAR), pm2Cal.get(Calendar.MONTH) + 1, pm2Cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                days = pmDays
                subtitle = monthYearFmt.format(Date(startMs))
                prevLabel = "vs 2 Months Ago"
            }
            AnalyticsPeriod.THREE_MONTHS -> {
                val sCal = Calendar.getInstance(istZone, Locale.US).apply {
                    timeInMillis = nowMs
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, -2)
                }
                startMs = startOfIstDay(sCal.get(Calendar.YEAR), sCal.get(Calendar.MONTH) + 1, 1)
                endMs = endOfIstDay(curY, curM, curD)
                val psCal = (sCal.clone() as Calendar).apply { add(Calendar.MONTH, -3) }
                prevStartMs = startOfIstDay(psCal.get(Calendar.YEAR), psCal.get(Calendar.MONTH) + 1, 1)
                prevEndMs = startMs - 1L
                days = (((endMs - startMs) / 86_400_000L) + 1).toInt().coerceAtLeast(1)
                subtitle = "Last 3 Months"
                prevLabel = "vs Prior 3 Months"
            }
            AnalyticsPeriod.SIX_MONTHS -> {
                val sCal = Calendar.getInstance(istZone, Locale.US).apply {
                    timeInMillis = nowMs
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, -5)
                }
                startMs = startOfIstDay(sCal.get(Calendar.YEAR), sCal.get(Calendar.MONTH) + 1, 1)
                endMs = endOfIstDay(curY, curM, curD)
                val psCal = (sCal.clone() as Calendar).apply { add(Calendar.MONTH, -6) }
                prevStartMs = startOfIstDay(psCal.get(Calendar.YEAR), psCal.get(Calendar.MONTH) + 1, 1)
                prevEndMs = startMs - 1L
                days = (((endMs - startMs) / 86_400_000L) + 1).toInt().coerceAtLeast(1)
                subtitle = "Last 6 Months"
                prevLabel = "vs Prior 6 Months"
            }
            AnalyticsPeriod.ONE_YEAR -> {
                val sCal = Calendar.getInstance(istZone, Locale.US).apply {
                    timeInMillis = nowMs
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, -11)
                }
                startMs = startOfIstDay(sCal.get(Calendar.YEAR), sCal.get(Calendar.MONTH) + 1, 1)
                endMs = endOfIstDay(curY, curM, curD)
                val psCal = (sCal.clone() as Calendar).apply { add(Calendar.MONTH, -12) }
                prevStartMs = startOfIstDay(psCal.get(Calendar.YEAR), psCal.get(Calendar.MONTH) + 1, 1)
                prevEndMs = startMs - 1L
                days = (((endMs - startMs) / 86_400_000L) + 1).toInt().coerceAtLeast(1)
                subtitle = "Last 12 Months"
                prevLabel = "vs Prior 12 Months"
            }
            AnalyticsPeriod.CURRENT_YEAR -> {
                startMs = startOfIstDay(curY, 1, 1)
                endMs = endOfIstDay(curY, curM, curD)
                prevStartMs = startOfIstDay(curY - 1, 1, 1)
                prevEndMs = endOfIstDay(curY - 1, 12, 31)
                days = (((endMs - startMs) / 86_400_000L) + 1).toInt().coerceAtLeast(1)
                subtitle = "Year $curY"
                prevLabel = "vs ${curY - 1}"
            }
            AnalyticsPeriod.FINANCIAL_YEAR -> {
                val fyStartYear = if (curM >= 4) curY else curY - 1
                startMs = startOfIstDay(fyStartYear, 4, 1)
                val fyMaxEnd = endOfIstDay(fyStartYear + 1, 3, 31)
                val todayEnd = endOfIstDay(curY, curM, curD)
                endMs = minOf(todayEnd, fyMaxEnd)
                prevStartMs = startOfIstDay(fyStartYear - 1, 4, 1)
                prevEndMs = endOfIstDay(fyStartYear, 3, 31)
                days = (((endMs - startMs) / 86_400_000L) + 1).toInt().coerceAtLeast(1)
                subtitle = "FY $fyStartYear-${(fyStartYear + 1) % 100}"
                prevLabel = "vs FY ${fyStartYear - 1}-${fyStartYear % 100}"
            }
            AnalyticsPeriod.CUSTOM -> {
                val s = customStartMs ?: startOfIstDay(curY, curM, 1)
                val e = customEndMs ?: endOfIstDay(curY, curM, curD)
                startMs = minOf(s, e)
                endMs = maxOf(s, e)
                days = (((endMs - startMs) / 86_400_000L) + 1).toInt().coerceAtLeast(1)
                prevEndMs = startMs - 1L
                prevStartMs = startMs - days * 86_400_000L
                subtitle = "${dayFmt.format(Date(startMs))} – ${dayFmt.format(Date(endMs))}"
                prevLabel = "vs Prior Period"
            }
            AnalyticsPeriod.THIS_MONTH -> {
                startMs = startOfIstDay(curY, curM, 1)
                endMs = endOfIstDay(curY, curM, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                val pmCal = Calendar.getInstance(istZone, Locale.US).apply {
                    timeInMillis = nowMs
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, -1)
                }
                prevStartMs = startOfIstDay(pmCal.get(Calendar.YEAR), pmCal.get(Calendar.MONTH) + 1, 1)
                prevEndMs = endOfIstDay(pmCal.get(Calendar.YEAR), pmCal.get(Calendar.MONTH) + 1, pmCal.getActualMaximum(Calendar.DAY_OF_MONTH))
                days = curD.coerceAtLeast(1)
                subtitle = monthYearFmt.format(Date(startMs))
                prevLabel = "vs Previous Month"
            }
        }

        val activeTxns = transactions.filter { it.transactionDateMs in startMs..endMs }
        val prevTxns = transactions.filter { it.transactionDateMs in prevStartMs..prevEndMs }

        var totalIn = 0.0
        var grossOut = 0.0
        var refunds = 0.0
        var cashback = 0.0
        var validCount = 0
        var highestExpense: TransactionEntity? = null

        val catMap = LinkedHashMap<String, Triple<Double, String, Int>>()
        val catRefunds = LinkedHashMap<String, Double>()
        val merchMap = LinkedHashMap<String, Pair<Double, Int>>()
        val bankMap = LinkedHashMap<String, BankSummaryItem>()
        val accountIdToBank = accounts.associate { it.id to it.bankName }

        for (acc in accounts) {
            val prev = bankMap[acc.bankName] ?: BankSummaryItem(acc.bankName, 0, 0.0, 0.0, 0.0, 0)
            bankMap[acc.bankName] = prev.copy(
                accountCount = prev.accountCount + 1,
                balance = MoneyMath.add(prev.balance, if (acc.accountType == "CREDIT_CARD") -abs(acc.currentBalance) else acc.currentBalance)
            )
        }

        val txnById = transactions.associateBy { it.localId }
        for (tx in activeTxns) {
            if (tx.transactionStatus == "FAILED" || tx.duplicateStatus == "EXACT_DUPLICATE") continue
            val bName = tx.accountId?.let { accountIdToBank[it] } ?: tx.bankName
            val bPrev = bankMap[bName] ?: BankSummaryItem(bName, 0, 0.0, 0.0, 0.0, 0)
            bankMap[bName] = bPrev.copy(
                totalCredit = MoneyMath.add(bPrev.totalCredit, if (tx.direction == "CREDIT") tx.amount else 0.0),
                totalDebit = MoneyMath.add(bPrev.totalDebit, if (tx.direction == "DEBIT") tx.amount else 0.0),
                txCount = bPrev.txCount + 1
            )

            if (isRefund(tx)) {
                refunds = MoneyMath.add(refunds, tx.amount)
                val linkedOrig = tx.linkedTransactionId?.let { txnById[it] }
                val catName = linkedOrig?.categoryName ?: tx.categoryName ?: "Uncategorized"
                catRefunds[catName] = MoneyMath.add(catRefunds[catName] ?: 0.0, tx.amount)
                continue
            }
            if (isExcludedFromTotals(tx)) continue

            validCount++
            if (tx.direction == "CREDIT") {
                if (isCashback(tx)) cashback = MoneyMath.add(cashback, tx.amount) else totalIn = MoneyMath.add(totalIn, tx.amount)
            } else {
                grossOut = MoneyMath.add(grossOut, tx.amount)
                if (highestExpense == null || tx.amount > highestExpense.amount) {
                    highestExpense = tx
                }
                val catName = tx.categoryName ?: "Uncategorized"
                val catColor = tx.categoryColor ?: "#94A3B8"
                val prevC = catMap[catName] ?: Triple(0.0, catColor, 0)
                catMap[catName] = Triple(MoneyMath.add(prevC.first, tx.amount), catColor, prevC.third + 1)

                val mName = (tx.merchant ?: "Unknown Merchant").trim()
                val prevM = merchMap[mName] ?: (0.0 to 0)
                merchMap[mName] = MoneyMath.add(prevM.first, tx.amount) to (prevM.second + 1)
            }
        }

        val totalOut = maxOf(0.0, MoneyMath.subtract(grossOut, refunds))

        var prevIn = 0.0
        var prevGrossOut = 0.0
        var prevRefunds = 0.0
        for (tx in prevTxns) {
            if (isRefund(tx)) {
                prevRefunds = MoneyMath.add(prevRefunds, tx.amount)
                continue
            }
            if (isExcludedFromTotals(tx)) continue
            if (tx.direction == "CREDIT") {
                if (!isCashback(tx)) prevIn = MoneyMath.add(prevIn, tx.amount)
            } else {
                prevGrossOut = MoneyMath.add(prevGrossOut, tx.amount)
            }
        }
        val prevOut = maxOf(0.0, MoneyMath.subtract(prevGrossOut, prevRefunds))

        val net = MoneyMath.subtract(totalIn, totalOut)
        val savingsRate = if (totalIn > 0.0) MoneyMath.round2((net / totalIn) * 100.0) else 0.0
        val avgDaily = MoneyMath.round2(totalOut / days)
        val incDelta = if (prevIn > 0.0) MoneyMath.round2(((totalIn - prevIn) / prevIn) * 100.0) else null
        val expDelta = if (prevOut > 0.0) MoneyMath.round2(((totalOut - prevOut) / prevOut) * 100.0) else null

        val adjustedCats = catMap.entries.mapNotNull { (name, triple) ->
            val netAmt = maxOf(0.0, MoneyMath.subtract(triple.first, catRefunds[name] ?: 0.0))
            if (netAmt > 0.0) Triple(name, netAmt, Pair(triple.second, triple.third)) else null
        }
        val catSum = MoneyMath.sum(adjustedCats.map { it.second }).coerceAtLeast(1.0)
        val categories = adjustedCats
            .sortedByDescending { it.second }
            .map { (name, amt, meta) ->
                CategoryBreakdownItem(
                    name = name,
                    amount = amt,
                    percentage = MoneyMath.percentage(amt, catSum),
                    colorHex = meta.first,
                    count = meta.second
                )
            }

        val merchants = merchMap.entries
            .sortedByDescending { it.value.first }
            .take(10)
            .map { MerchantRankItem(it.key, it.value.first, it.value.second) }

        val insights = ArrayList<String>()
        if (categories.isNotEmpty()) {
            val topCat = categories.first()
            insights.add("Highest spending category is ${topCat.name} (${topCat.percentage}% of net outflow).")
        }
        if (expDelta != null) {
            if (expDelta > 10.0) insights.add("Spending is up ${expDelta}% $prevLabel.")
            else if (expDelta < -5.0) insights.add("Spending is down ${abs(expDelta)}% $prevLabel.")
        }
        if (totalIn > 0.0) {
            insights.add("Savings rate is ${savingsRate}% (${if (net >= 0) "surplus" else "deficit"} of ₹${abs(net).roundToInt()}) for $subtitle.")
        }
        if (highestExpense != null && avgDaily > 0.0 && highestExpense.amount > avgDaily * 2.5) {
            insights.add("Unusual spike: ${highestExpense.merchant ?: "Transaction"} of ₹${highestExpense.amount.roundToInt()} is ${(highestExpense.amount / avgDaily).roundToInt()}x your daily average.")
        }
        if (refunds > 0.0 || cashback > 0.0) {
            insights.add("Recovered ₹${MoneyMath.add(refunds, cashback).roundToInt()} via refunds & cashback.")
        }

        // Build activity trend buckets (daily if <= 31 days, monthly if > 31 days)
        val activityBuckets = LinkedHashMap<String, DailyTrendPoint>()
        val useMonthlyBuckets = days > 35
        val bucketKeyFmt = SimpleDateFormat(if (useMonthlyBuckets) "yyyy-MM" else "yyyy-MM-dd", Locale.US).apply { timeZone = istZone }
        val bucketLblFmt = SimpleDateFormat(if (useMonthlyBuckets) "MMM yy" else "d MMM", Locale.US).apply { timeZone = istZone }
        val cursorCal = Calendar.getInstance(istZone, Locale.US).apply { timeInMillis = startMs }
        while (cursorCal.timeInMillis <= endMs) {
            val key = bucketKeyFmt.format(cursorCal.time)
            if (!activityBuckets.containsKey(key)) {
                activityBuckets[key] = DailyTrendPoint(key, bucketLblFmt.format(cursorCal.time), 0.0, 0.0)
            }
            cursorCal.add(if (useMonthlyBuckets) Calendar.MONTH else Calendar.DAY_OF_MONTH, 1)
        }
        for (tx in activeTxns) {
            if (tx.transactionStatus == "FAILED" || tx.duplicateStatus == "EXACT_DUPLICATE") continue
            if (isRefund(tx) || isExcludedFromTotals(tx)) continue
            val bKey = bucketKeyFmt.format(Date(tx.transactionDateMs))
            activityBuckets[bKey]?.let { b ->
                activityBuckets[bKey] = if (tx.direction == "CREDIT") {
                    b.copy(income = MoneyMath.add(b.income, tx.amount))
                } else {
                    b.copy(expense = MoneyMath.add(b.expense, tx.amount))
                }
            }
        }

        return PeriodAnalytics(
            period = period,
            subtitle = subtitle,
            prevLabel = prevLabel,
            totalIncome = MoneyMath.round2(totalIn),
            totalExpense = totalOut,
            netSavings = net,
            savingsRate = savingsRate,
            avgDailySpend = avgDaily,
            refundsTotal = MoneyMath.round2(refunds),
            cashbackTotal = MoneyMath.round2(cashback),
            validTxCount = validCount,
            incomeChangePct = incDelta,
            expenseChangePct = expDelta,
            highestExpense = highestExpense,
            categoryBreakdown = categories,
            topMerchants = merchants,
            bankSummary = bankMap.values.sortedByDescending { it.totalDebit + it.totalCredit },
            insights = insights,
            activityTrend = activityBuckets.values.toList()
        )
    }
}
