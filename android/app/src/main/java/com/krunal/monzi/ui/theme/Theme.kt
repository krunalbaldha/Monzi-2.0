package com.krunal.monzi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

@Immutable
data class MonziColorPalette(
    val isDark: Boolean,
    val bgCanvas: Color,
    val bgCanvasAlt: Color,
    val glassWhite: Color,
    val glassWhiteStrong: Color,
    val glassBorder: Color,
    val cardSurface: Color,
    val cardSurfaceStrong: Color,
    val surfaceMuted: Color,
    val surfaceElevated: Color,
    val bluePrimary: Color,
    val blueDark: Color,
    val blueSoft: Color,
    val indigoAccent: Color,
    val cyanSoft: Color,
    val emerald: Color,
    val emeraldDark: Color,
    val emeraldSoft: Color,
    val rose: Color,
    val roseDark: Color,
    val roseSoft: Color,
    val amber: Color,
    val amberDark: Color,
    val amberSoft: Color,
    val orange: Color,
    val orangeSoft: Color,
    val violet: Color,
    val violetSoft: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val borderLight: Color,
    val dividerSubtle: Color,
    val darkSlate: Color,
    val darkSlateAlt: Color
)

val LightMonziPalette = MonziColorPalette(
    isDark = false,
    bgCanvas = Color(0xFFF6F8FC),
    bgCanvasAlt = Color(0xFFEDF2FA),
    glassWhite = Color(0xF5FFFFFF),
    glassWhiteStrong = Color(0xFFFFFFFF),
    glassBorder = Color(0xFFE3EAF4),
    cardSurface = Color(0xFFFFFFFF),
    cardSurfaceStrong = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFF1F5F9),
    surfaceElevated = Color(0xFFF8FAFC),
    bluePrimary = Color(0xFF2563EB),
    blueDark = Color(0xFF1D4ED8),
    blueSoft = Color(0xFFEBF2FF),
    indigoAccent = Color(0xFF4F46E5),
    cyanSoft = Color(0xFFECFEFF),
    emerald = Color(0xFF059669),
    emeraldDark = Color(0xFF047857),
    emeraldSoft = Color(0xFFD1FAE5),
    rose = Color(0xFFE11D48),
    roseDark = Color(0xFFBE123C),
    roseSoft = Color(0xFFFFE4E6),
    amber = Color(0xFFD97706),
    amberDark = Color(0xFFB45309),
    amberSoft = Color(0xFFFEF3C7),
    orange = Color(0xFFEA580C),
    orangeSoft = Color(0xFFFFEDD5),
    violet = Color(0xFF7C3AED),
    violetSoft = Color(0xFFF3E8FF),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textMuted = Color(0xFF64748B),
    borderLight = Color(0xFFE2E8F0),
    dividerSubtle = Color(0xFFEEF2F7),
    darkSlate = Color(0xFF0F172A),
    darkSlateAlt = Color(0xFF1E293B)
)

val DarkMonziPalette = MonziColorPalette(
    isDark = true,
    bgCanvas = Color(0xFF090D16),
    bgCanvasAlt = Color(0xFF0F1624),
    glassWhite = Color(0xFF131B2B),
    glassWhiteStrong = Color(0xFF182235),
    glassBorder = Color(0xFF25324B),
    cardSurface = Color(0xFF131B2B),
    cardSurfaceStrong = Color(0xFF1A2438),
    surfaceMuted = Color(0xFF1E293B),
    surfaceElevated = Color(0xFF1E2A40),
    bluePrimary = Color(0xFF60A5FA),
    blueDark = Color(0xFF3B82F6),
    blueSoft = Color(0xFF172554),
    indigoAccent = Color(0xFF818CF8),
    cyanSoft = Color(0xFF083344),
    emerald = Color(0xFF34D399),
    emeraldDark = Color(0xFF10B981),
    emeraldSoft = Color(0xFF064E3B),
    rose = Color(0xFFFB7185),
    roseDark = Color(0xFFF43F5E),
    roseSoft = Color(0xFF4C0519),
    amber = Color(0xFFFBBF24),
    amberDark = Color(0xFFF59E0B),
    amberSoft = Color(0xFF451A03),
    orange = Color(0xFFFB923C),
    orangeSoft = Color(0xFF431407),
    violet = Color(0xFFA78BFA),
    violetSoft = Color(0xFF2E1065),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFFCBD5E1),
    textMuted = Color(0xFF94A3B8),
    borderLight = Color(0xFF26334A),
    dividerSubtle = Color(0xFF1E293B),
    darkSlate = Color(0xFF0B1120),
    darkSlateAlt = Color(0xFF172033)
)

val LocalMonziColors = staticCompositionLocalOf { LightMonziPalette }

object MonziColors {
    val current: MonziColorPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalMonziColors.current

    val IsDark: Boolean @Composable @ReadOnlyComposable get() = current.isDark
    val BgCanvas: Color @Composable @ReadOnlyComposable get() = current.bgCanvas
    val BgCanvasAlt: Color @Composable @ReadOnlyComposable get() = current.bgCanvasAlt
    val GlassWhite: Color @Composable @ReadOnlyComposable get() = current.glassWhite
    val GlassWhiteStrong: Color @Composable @ReadOnlyComposable get() = current.glassWhiteStrong
    val GlassBorder: Color @Composable @ReadOnlyComposable get() = current.glassBorder
    val CardSurface: Color @Composable @ReadOnlyComposable get() = current.cardSurface
    val CardSurfaceStrong: Color @Composable @ReadOnlyComposable get() = current.cardSurfaceStrong
    val SurfaceMuted: Color @Composable @ReadOnlyComposable get() = current.surfaceMuted
    val SurfaceElevated: Color @Composable @ReadOnlyComposable get() = current.surfaceElevated

    val BluePrimary: Color @Composable @ReadOnlyComposable get() = current.bluePrimary
    val BlueDark: Color @Composable @ReadOnlyComposable get() = current.blueDark
    val BlueSoft: Color @Composable @ReadOnlyComposable get() = current.blueSoft
    val IndigoAccent: Color @Composable @ReadOnlyComposable get() = current.indigoAccent
    val LavenderAccent: Color @Composable @ReadOnlyComposable get() = current.violet
    val CyanAccent: Color @Composable @ReadOnlyComposable get() = Color(0xFF06B6D4)
    val CyanSoft: Color @Composable @ReadOnlyComposable get() = current.cyanSoft
    val GlassBorderSubtle: Color @Composable @ReadOnlyComposable get() = current.glassBorder.copy(alpha = 0.7f)
    val GlassTintedRose: Color @Composable @ReadOnlyComposable get() = current.roseSoft.copy(alpha = 0.85f)
    val GlassTintedBlue: Color @Composable @ReadOnlyComposable get() = current.blueSoft.copy(alpha = 0.9f)
    val GlassTintedLavender: Color @Composable @ReadOnlyComposable get() = current.violetSoft.copy(alpha = 0.9f)
    val GlassTintedEmerald: Color @Composable @ReadOnlyComposable get() = current.emeraldSoft.copy(alpha = 0.9f)

    val Emerald: Color @Composable @ReadOnlyComposable get() = current.emerald
    val EmeraldDark: Color @Composable @ReadOnlyComposable get() = current.emeraldDark
    val EmeraldSoft: Color @Composable @ReadOnlyComposable get() = current.emeraldSoft

    val Rose: Color @Composable @ReadOnlyComposable get() = current.rose
    val RoseDark: Color @Composable @ReadOnlyComposable get() = current.roseDark
    val RoseSoft: Color @Composable @ReadOnlyComposable get() = current.roseSoft

    val Amber: Color @Composable @ReadOnlyComposable get() = current.amber
    val AmberDark: Color @Composable @ReadOnlyComposable get() = current.amberDark
    val AmberSoft: Color @Composable @ReadOnlyComposable get() = current.amberSoft

    val Orange: Color @Composable @ReadOnlyComposable get() = current.orange
    val OrangeSoft: Color @Composable @ReadOnlyComposable get() = current.orangeSoft

    val Violet: Color @Composable @ReadOnlyComposable get() = current.violet
    val VioletSoft: Color @Composable @ReadOnlyComposable get() = current.violetSoft

    val TextPrimary: Color @Composable @ReadOnlyComposable get() = current.textPrimary
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = current.textSecondary
    val TextMuted: Color @Composable @ReadOnlyComposable get() = current.textMuted

    val BorderLight: Color @Composable @ReadOnlyComposable get() = current.borderLight
    val DividerSubtle: Color @Composable @ReadOnlyComposable get() = current.dividerSubtle
    val DarkSlate: Color @Composable @ReadOnlyComposable get() = current.darkSlate
    val DarkSlateAlt: Color @Composable @ReadOnlyComposable get() = current.darkSlateAlt
}

object MonziDimens {
    val ScreenHorizontal = 16.dp
    val ScreenPadding = 16.dp
    val SectionSpacing = 18.dp
    val CardSpacing = 14.dp
    val CardRadius = 22.dp
    val InnerRadius = 16.dp
    val ButtonRadius = 14.dp
    val PillRadius = 999.dp
    val BottomNavClearance = 98.dp
}

private fun buildTypography(palette: MonziColorPalette): Typography = Typography(
    displaySmall = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.8).sp,
        lineHeight = 38.sp,
        color = palette.textPrimary
    ),
    headlineMedium = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
        lineHeight = 30.sp,
        color = palette.textPrimary
    ),
    headlineSmall = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
        lineHeight = 26.sp,
        color = palette.textPrimary
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp,
        color = palette.textPrimary
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
        color = palette.textPrimary
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp,
        color = palette.textSecondary
    ),
    labelMedium = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
        color = palette.textMuted
    )
)

@Composable
fun MonziTheme(
    themeMode: String = "SYSTEM",
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode.uppercase(Locale.US)) {
        "DARK" -> true
        "LIGHT" -> false
        else -> systemDark
    }
    val palette = if (useDark) DarkMonziPalette else LightMonziPalette
    val colorScheme = if (useDark) {
        darkColorScheme(
            primary = palette.bluePrimary,
            onPrimary = Color(0xFF06152B),
            primaryContainer = palette.blueSoft,
            onPrimaryContainer = palette.bluePrimary,
            secondary = palette.indigoAccent,
            onSecondary = Color.White,
            background = palette.bgCanvas,
            onBackground = palette.textPrimary,
            surface = palette.cardSurfaceStrong,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceMuted,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.borderLight,
            error = palette.rose
        )
    } else {
        lightColorScheme(
            primary = palette.bluePrimary,
            onPrimary = Color.White,
            primaryContainer = palette.blueSoft,
            onPrimaryContainer = palette.bluePrimary,
            secondary = palette.indigoAccent,
            onSecondary = Color.White,
            background = palette.bgCanvas,
            onBackground = palette.textPrimary,
            surface = palette.cardSurfaceStrong,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceMuted,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.borderLight,
            error = palette.rose
        )
    }

    CompositionLocalProvider(LocalMonziColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = buildTypography(palette),
            content = content
        )
    }
}

private val indianLocale = Locale("en", "IN")

fun formatInr(amount: Double, decimals: Int = 0): String {
    val nf = NumberFormat.getCurrencyInstance(indianLocale)
    nf.maximumFractionDigits = decimals
    nf.minimumFractionDigits = decimals
    return nf.format(amount)
}

fun formatCompactInr(amount: Double): String {
    val absVal = abs(amount)
    val sign = if (amount < 0) "-" else ""
    return when {
        absVal >= 10_000_000.0 -> String.format(Locale.US, "%s₹%.2fCr", sign, absVal / 10_000_000.0)
        absVal >= 100_000.0 -> String.format(Locale.US, "%s₹%.2fL", sign, absVal / 100_000.0)
        absVal >= 1_000.0 -> String.format(Locale.US, "%s₹%.1fK", sign, absVal / 1_000.0)
        else -> formatInr(amount, 0)
    }
}

fun formatIstDateTime(millis: Long): String {
    val sdf = SimpleDateFormat("d MMM, hh:mm a", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    }
    return sdf.format(Date(millis))
}

fun formatIstDateOnly(millis: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    }
    return sdf.format(Date(millis))
}

fun parseHexColor(hex: String?, fallback: Color = Color(0xFF2563EB)): Color {
    if (hex.isNullOrBlank()) return fallback
    return try {
        val cleaned = hex.trim().removePrefix("#")
        when (cleaned.length) {
            6 -> Color(("FF$cleaned").toLong(16))
            8 -> Color(cleaned.toLong(16))
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    }
}
