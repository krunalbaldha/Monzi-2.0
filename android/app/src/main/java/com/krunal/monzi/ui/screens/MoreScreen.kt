package com.krunal.monzi.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krunal.monzi.ui.MonziUiState
import com.krunal.monzi.ui.components.MonziCard
import com.krunal.monzi.ui.theme.MonziColors
import com.krunal.monzi.ui.theme.MonziDimens
import com.krunal.monzi.ui.theme.formatCompactInr
import com.krunal.monzi.util.MoneyMath

@Composable
fun MoreScreen(
    state: MonziUiState,
    onOpenAccounts: () -> Unit,
    onOpenCreditCards: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenSmsCenter: () -> Unit,
    onOpenChecklist: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val profile = state.profile
    val initialChar = profile?.fullName?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "M"
    val totalCcOutstanding = MoneyMath.sum(state.creditCardSummaries.map { it.outstandingAmount })

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
        item {
            Column {
                Text(
                    text = "More",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp,
                    color = MonziColors.TextPrimary
                )
                Text(
                    text = "Accounts, budgets, SMS engine & settings",
                    fontSize = 12.sp,
                    color = MonziColors.TextMuted
                )
            }
        }

        // Profile Summary Shortcut
        item {
            MonziCard(
                containerColor = MonziColors.GlassWhiteStrong,
                onClick = onOpenSettings
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(MonziColors.BluePrimary, MonziColors.CyanAccent)))
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(initialChar, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile?.fullName?.ifBlank { "Monzi User" } ?: "Monzi User",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MonziColors.TextPrimary
                        )
                        Text(
                            text = profile?.email ?: "",
                            fontSize = 12.sp,
                            color = MonziColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MonziColors.TextMuted
                    )
                }
            }
        }

        // Core Financial Tools
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MoreMenuTile(
                    title = "Accounts & Cards",
                    subtitle = "${state.accounts.size} linked • Net Worth ${formatCompactInr(state.dashboardStats.netWorth)}",
                    icon = Icons.Rounded.AccountBalanceWallet,
                    iconBg = MonziColors.BlueSoft,
                    iconTint = MonziColors.BluePrimary,
                    onClick = onOpenAccounts
                )
                MoreMenuTile(
                    title = "Credit Cards",
                    subtitle = if (state.creditCardSummaries.isEmpty()) "Track card limits & billing statements"
                    else "${state.creditCardSummaries.size} card(s) • Outstanding ${formatCompactInr(totalCcOutstanding)}",
                    icon = Icons.Rounded.CreditCard,
                    iconBg = MonziColors.GlassTintedLavender,
                    iconTint = MonziColors.IndigoAccent,
                    onClick = onOpenCreditCards
                )
                MoreMenuTile(
                    title = "Monthly Budgets",
                    subtitle = if (state.budgetEvaluation.summaries.isEmpty()) "Set category & monthly caps"
                    else "${state.budgetEvaluation.summaries.size} active • ${state.budgetEvaluation.overallUtilizationPct.toInt()}% utilized",
                    icon = Icons.Rounded.PieChart,
                    iconBg = MonziColors.EmeraldSoft,
                    iconTint = MonziColors.Emerald,
                    onClick = onOpenBudgets
                )
                MoreMenuTile(
                    title = "SMS Sync Center",
                    subtitle = if (state.hasSmsPermissions) "Active • ${state.pendingSyncCount} queued" else "Grant SMS access to auto-capture",
                    icon = Icons.AutoMirrored.Rounded.Message,
                    iconBg = MonziColors.AmberSoft,
                    iconTint = MonziColors.AmberDark,
                    onClick = onOpenSmsCenter
                )
                MoreMenuTile(
                    title = "Setup & Automation Checklist",
                    subtitle = "Permissions, battery whitelist & cloud sync health",
                    icon = Icons.Rounded.Checklist,
                    iconBg = MonziColors.BlueSoft,
                    iconTint = MonziColors.BluePrimary,
                    onClick = onOpenChecklist
                )
                MoreMenuTile(
                    title = "Settings",
                    subtitle = "Security, SMS engine, rules, CSV export & about",
                    icon = Icons.Rounded.Settings,
                    iconBg = MonziColors.SurfaceMuted,
                    iconTint = MonziColors.TextPrimary,
                    onClick = onOpenSettings
                )
            }
        }
    }
}

@Composable
private fun MoreMenuTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    MonziCard(
        containerColor = MonziColors.GlassWhiteStrong,
        cornerRadius = 22.dp,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        shadowElevation = 5.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = iconBg,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(21.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MonziColors.TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MonziColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MonziColors.TextMuted
            )
        }
    }
}
