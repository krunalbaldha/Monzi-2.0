package com.krunal.monzi.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Decimal-safe monetary arithmetic (FRD Section 17: all money calculations
 * must use decimal-safe arithmetic).
 */
object MoneyMath {
    val ZERO: BigDecimal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)

    fun of(value: Double): BigDecimal =
        BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP)

    fun of(value: String?): BigDecimal {
        if (value.isNullOrBlank()) return ZERO
        return try {
            BigDecimal(value.trim().replace(",", "")).setScale(2, RoundingMode.HALF_UP)
        } catch (_: Exception) {
            ZERO
        }
    }

    fun add(a: Double, b: Double): Double =
        of(a).add(of(b)).setScale(2, RoundingMode.HALF_UP).toDouble()

    fun subtract(a: Double, b: Double): Double =
        of(a).subtract(of(b)).setScale(2, RoundingMode.HALF_UP).toDouble()

    fun sum(values: Iterable<Double>): Double {
        var total = ZERO
        for (v in values) {
            total = total.add(of(v))
        }
        return total.setScale(2, RoundingMode.HALF_UP).toDouble()
    }

    fun percentage(part: Double, whole: Double): Double {
        if (whole <= 0.0) return 0.0
        val p = of(part)
        val w = of(whole)
        return p.multiply(BigDecimal(100))
            .divide(w, 1, RoundingMode.HALF_UP)
            .toDouble()
    }

    fun round2(value: Double): Double = of(value).toDouble()

    fun absDiff(a: Double, b: Double): Double =
        of(a).subtract(of(b)).abs().setScale(2, RoundingMode.HALF_UP).toDouble()
}

/**
 * Standardized Asia/Kolkata (IST) date & billing cycle helpers (FRD Section 17).
 */
object IstTime {
    val ZONE: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    fun nowCalendar(): Calendar = Calendar.getInstance(ZONE, Locale.US)

    fun calendarAt(millis: Long): Calendar =
        Calendar.getInstance(ZONE, Locale.US).apply { timeInMillis = millis }

    fun toDateKey(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = ZONE }.format(Date(millis))

    fun toMonthKey(millis: Long): String = toDateKey(millis).substring(0, 7)

    fun currentMonthKey(nowMs: Long = System.currentTimeMillis()): String = toMonthKey(nowMs)

    fun previousMonthKey(monthKey: String): String {
        val parts = monthKey.split("-")
        if (parts.size != 2) return monthKey
        val cal = Calendar.getInstance(ZONE, Locale.US).apply {
            set(Calendar.YEAR, parts[0].toInt())
            set(Calendar.MONTH, parts[1].toInt() - 1)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, -1)
        }
        return toMonthKey(cal.timeInMillis)
    }

    fun monthRangeMillis(monthKey: String): Pair<Long, Long> {
        val parts = monthKey.split("-")
        val y = parts[0].toInt()
        val m = parts[1].toInt()
        val start = Calendar.getInstance(ZONE, Locale.US).apply {
            set(y, m - 1, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val maxDay = start.getActualMaximum(Calendar.DAY_OF_MONTH)
        val end = Calendar.getInstance(ZONE, Locale.US).apply {
            set(y, m - 1, maxDay, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return start.timeInMillis to end.timeInMillis
    }

    fun greetingForHour(nowMs: Long = System.currentTimeMillis()): String {
        val hour = calendarAt(nowMs).get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "Good Morning"
            hour < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    fun formatBannerDate(nowMs: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("EEEE, d MMM yyyy", Locale("en", "IN"))
            .apply { timeZone = ZONE }
            .format(Date(nowMs))

    fun formatShortDate(millis: Long): String =
        SimpleDateFormat("d MMM", Locale("en", "IN"))
            .apply { timeZone = ZONE }
            .format(Date(millis))

    fun formatDateOnly(millis: Long): String =
        SimpleDateFormat("d MMM yyyy", Locale("en", "IN"))
            .apply { timeZone = ZONE }
            .format(Date(millis))

    /**
     * Start and end of single day in IST (00:00:00.000 to 23:59:59.999).
     * daysAgo = 0 (Today), 1 (Yesterday), 2 (Previous Day / day before yesterday)
     */
    fun dayRangeMillis(daysAgo: Int, nowMs: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val cal = Calendar.getInstance(ZONE, Locale.US).apply {
            timeInMillis = nowMs
            add(Calendar.DAY_OF_YEAR, -daysAgo)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val end = cal.timeInMillis
        return start to end
    }

    /**
     * Range for previous N days (e.g. 7, 15, 30 days) ending at end of today IST.
     */
    fun lastDaysRangeMillis(days: Int, nowMs: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val startCal = Calendar.getInstance(ZONE, Locale.US).apply {
            timeInMillis = nowMs
            add(Calendar.DAY_OF_YEAR, -(days - 1))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance(ZONE, Locale.US).apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return startCal.timeInMillis to endCal.timeInMillis
    }

    /**
     * Range for current month IST (1st 00:00:00.000 to last day 23:59:59.999).
     */
    fun thisMonthRangeMillis(nowMs: Long = System.currentTimeMillis()): Pair<Long, Long> =
        monthRangeMillis(currentMonthKey(nowMs))

    /**
     * Range for previous month IST.
     */
    fun lastMonthRangeMillis(nowMs: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val prevKey = previousMonthKey(currentMonthKey(nowMs))
        return monthRangeMillis(prevKey)
    }

    /**
     * Range for custom dates in IST (start day 00:00:00.000 to end day 23:59:59.999).
     */
    fun customDayRangeMillis(startEpochMs: Long, endEpochMs: Long): Pair<Long, Long> {
        val startCal = Calendar.getInstance(ZONE, Locale.US).apply {
            timeInMillis = startEpochMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance(ZONE, Locale.US).apply {
            timeInMillis = endEpochMs
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return startCal.timeInMillis to endCal.timeInMillis
    }
}

/**
 * Standard predefined options for date-based SMS synchronization (FRD Section 2).
 */
enum class SmsDateFilterOption(val label: String, val description: String) {
    TODAY("Today", "Today's SMS messages"),
    YESTERDAY("Yesterday", "Yesterday's SMS messages"),
    PREVIOUS_DAY("Previous Day", "SMS from the day before yesterday"),
    LAST_7_DAYS("Last 7 Days", "SMS from previous 7 days"),
    LAST_15_DAYS("Last 15 Days", "SMS from previous 15 days"),
    LAST_30_DAYS("Last 30 Days", "SMS from previous 30 days"),
    THIS_MONTH("This Month", "SMS from current month"),
    LAST_MONTH("Last Month", "SMS from previous month"),
    CUSTOM("Custom Date Range", "Select From Date and To Date");

    fun resolveRange(customStartMs: Long? = null, customEndMs: Long? = null): Pair<Long, Long> {
        return when (this) {
            TODAY -> IstTime.dayRangeMillis(0)
            YESTERDAY -> IstTime.dayRangeMillis(1)
            PREVIOUS_DAY -> IstTime.dayRangeMillis(2)
            LAST_7_DAYS -> IstTime.lastDaysRangeMillis(7)
            LAST_15_DAYS -> IstTime.lastDaysRangeMillis(15)
            LAST_30_DAYS -> IstTime.lastDaysRangeMillis(30)
            THIS_MONTH -> IstTime.thisMonthRangeMillis()
            LAST_MONTH -> IstTime.lastMonthRangeMillis()
            CUSTOM -> {
                val start = customStartMs ?: System.currentTimeMillis()
                val end = customEndMs ?: System.currentTimeMillis()
                IstTime.customDayRangeMillis(minOf(start, end), maxOf(start, end))
            }
        }
    }
}
