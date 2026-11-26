package com.krunal.monzi.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Atm
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krunal.monzi.data.CategoryBreakdownItem
import com.krunal.monzi.data.DailyTrendPoint
import com.krunal.monzi.data.MonthlyCashFlowPoint
import com.krunal.monzi.data.TransactionEntity
import com.krunal.monzi.ui.theme.MonziColors
import com.krunal.monzi.ui.theme.MonziDimens
import com.krunal.monzi.ui.theme.formatCompactInr
import com.krunal.monzi.ui.theme.formatInr
import com.krunal.monzi.ui.theme.formatIstDateTime
import com.krunal.monzi.ui.theme.parseHexColor
import java.util.Locale

data class CategoryVisual(
    val icon: ImageVector,
    val accentColor: Color
)

fun categoryVisualFor(
    categoryName: String?,
    transactionType: String? = null,
    direction: String? = null,
    merchant: String? = null,
    explicitColorHex: String? = null
): CategoryVisual {
    val name = (categoryName ?: "").lowercase(Locale.US)
    val type = (transactionType ?: "").uppercase(Locale.US)
    val merch = (merchant ?: "").lowercase(Locale.US)
    val explicit = explicitColorHex?.let { parseHexColor(it) }

    return when {
        type == "CC_BILL_PAYMENT" -> CategoryVisual(Icons.Rounded.CreditCard, explicit ?: Color(0xFF7C3AED))
        type == "TRANSFER" -> CategoryVisual(Icons.Rounded.SwapHoriz, explicit ?: Color(0xFF6366F1))
        type == "REFUND" -> CategoryVisual(Icons.Rounded.Undo, explicit ?: Color(0xFF059669))
        type == "REVERSAL" -> CategoryVisual(Icons.Rounded.Replay, explicit ?: Color(0xFF059669))
        type == "ATM_WITHDRAWAL" -> CategoryVisual(Icons.Rounded.Atm, explicit ?: Color(0xFFD97706))
        type == "CASH_DEPOSIT" -> CategoryVisual(Icons.Rounded.Savings, explicit ?: Color(0xFF059669))
        type == "INTEREST" -> CategoryVisual(Icons.Rounded.TrendingUp, explicit ?: Color(0xFF10B981))
        type == "BANK_CHARGES" -> CategoryVisual(Icons.Rounded.AccountBalance, explicit ?: Color(0xFFE11D48))

        name.contains("salary") || name.contains("payroll") || name.contains("stipend") ->
            CategoryVisual(Icons.Rounded.Payments, explicit ?: Color(0xFF059669))
        name.contains("freelance") || name.contains("business") || name.contains("consult") ->
            CategoryVisual(Icons.Rounded.Work, explicit ?: Color(0xFF0D9488))
        name.contains("invest") || name.contains("dividend") || name.contains("stock") || name.contains("mutual") ->
            CategoryVisual(Icons.Rounded.TrendingUp, explicit ?: Color(0xFF2563EB))
        name.contains("cashback") || name.contains("reward") ->
            CategoryVisual(Icons.Rounded.CardGiftcard, explicit ?: Color(0xFF10B981))
        name.contains("refund") ->
            CategoryVisual(Icons.Rounded.Undo, explicit ?: Color(0xFF059669))
        name.contains("income") || (direction == "CREDIT" && (name.isBlank() || name.contains("other"))) ->
            CategoryVisual(Icons.Rounded.ArrowDownward, explicit ?: Color(0xFF059669))

        name.contains("food") || name.contains("dining") || name.contains("restaurant") ||
            merch.contains("swiggy") || merch.contains("zomato") ->
            CategoryVisual(Icons.Rounded.Fastfood, explicit ?: Color(0xFFF97316))
        name.contains("grocer") || name.contains("supermarket") ||
            merch.contains("blinkit") || merch.contains("zepto") || merch.contains("bigbasket") ->
            CategoryVisual(Icons.Rounded.ShoppingCart, explicit ?: Color(0xFF16A34A))
        name.contains("fuel") || name.contains("petrol") || name.contains("diesel") ->
            CategoryVisual(Icons.Rounded.LocalGasStation, explicit ?: Color(0xFFEA580C))
        name.contains("travel") || name.contains("transport") || name.contains("cab") ||
            merch.contains("uber") || merch.contains("ola") || merch.contains("irctc") ->
            CategoryVisual(Icons.Rounded.DirectionsCar, explicit ?: Color(0xFF2563EB))
        name.contains("shop") || name.contains("cloth") || name.contains("apparel") ||
            merch.contains("amazon") || merch.contains("flipkart") || merch.contains("myntra") ->
            CategoryVisual(Icons.Rounded.ShoppingBag, explicit ?: Color(0xFFDB2777))
        name.contains("bill") || name.contains("utilit") || name.contains("electric") || name.contains("recharge") ->
            CategoryVisual(Icons.Rounded.ReceiptLong, explicit ?: Color(0xFF7C3AED))
        name.contains("rent") || name.contains("hous") || name.contains("home") ->
            CategoryVisual(Icons.Rounded.Home, explicit ?: Color(0xFF4F46E5))
        name.contains("entertain") || name.contains("movie") || name.contains("gaming") ->
            CategoryVisual(Icons.Rounded.Movie, explicit ?: Color(0xFFE11D48))
        name.contains("subscri") || name.contains("ott") ->
            CategoryVisual(Icons.Rounded.Subscriptions, explicit ?: Color(0xFF9333EA))
        name.contains("health") || name.contains("medic") || name.contains("pharma") || name.contains("hospital") ->
            CategoryVisual(Icons.Rounded.LocalHospital, explicit ?: Color(0xFFEF4444))
        name.contains("educat") || name.contains("tuition") || name.contains("course") ->
            CategoryVisual(Icons.Rounded.School, explicit ?: Color(0xFF0284C7))
        name.contains("insur") ->
            CategoryVisual(Icons.Rounded.Shield, explicit ?: Color(0xFF0D9488))
        name.contains("emi") || name.contains("loan") ->
            CategoryVisual(Icons.Rounded.AccountBalance, explicit ?: Color(0xFFDC2626))
        name.contains("personal") || name.contains("salon") || name.contains("care") ->
            CategoryVisual(Icons.Rounded.Spa, explicit ?: Color(0xFFEC4899))
        name.contains("gift") || name.contains("donat") ->
            CategoryVisual(Icons.Rounded.CardGiftcard, explicit ?: Color(0xFF8B5CF6))
        name.contains("transfer") ->
            CategoryVisual(Icons.Rounded.SwapHoriz, explicit ?: Color(0xFF6366F1))
        name.contains("bank charge") ->
            CategoryVisual(Icons.Rounded.AccountBalance, explicit ?: Color(0xFFE11D48))
        direction == "CREDIT" ->
            CategoryVisual(Icons.Rounded.ArrowDownward, explicit ?: Color(0xFF059669))
        else -> CategoryVisual(Icons.Rounded.Storefront, explicit ?: Color(0xFF2563EB))
    }
}

@Composable
fun MonziAmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = MonziColors.IsDark
    val bgTop = MonziColors.BgCanvas
    val bgBottom = MonziColors.BgCanvasAlt
    val glowPrimary = MonziColors.BluePrimary.copy(alpha = if (isDark) 0.14f else 0.08f)
    val glowSecondary = MonziColors.IndigoAccent.copy(alpha = if (isDark) 0.10f else 0.05f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgTop, bgBottom)))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowPrimary, Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.06f),
                    radius = size.width * 0.72f
                ),
                center = Offset(size.width * 0.85f, size.height * 0.06f),
                radius = size.width * 0.72f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowSecondary, Color.Transparent),
                    center = Offset(size.width * 0.12f, size.height * 0.55f),
                    radius = size.width * 0.65f
                ),
                center = Offset(size.width * 0.12f, size.height * 0.55f),
                radius = size.width * 0.65f
            )
        }
        content()
    }
}

@Composable
fun MonziCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MonziColors.CardSurface,
    borderColor: Color = MonziColors.GlassBorder,
    cornerRadius: Dp = MonziDimens.CardRadius,
    shadowElevation: Dp = if (MonziColors.IsDark) 0.dp else 1.5.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    if (onClick != null) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
            shadowElevation = shadowElevation,
            onClick = onClick
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
            shadowElevation = shadowElevation
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MonziColors.TextPrimary,
    badgeCount: Int = 0
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MonziColors.CardSurfaceStrong)
                .border(1.dp, MonziColors.GlassBorder, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(19.dp)
            )
        }
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MonziColors.Rose),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (badgeCount > 9) "9+" else badgeCount.toString(),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun MonziSubScreenTopBar(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = MonziDimens.ScreenHorizontal, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            GlassIconButton(
                icon = Icons.Rounded.ArrowBackIosNew,
                contentDescription = "Back",
                onClick = onBack
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MonziColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = MonziColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun StatusPill(
    label: String,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    dotColor: Color? = textColor
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
            Text(
                text = label,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun bankCardGradient(bankName: String, accountType: String): Brush {
    val upper = bankName.uppercase(Locale.US)
    val colors = when {
        accountType == "CREDIT_CARD" -> listOf(Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF312E81))
        upper.contains("HDFC") -> listOf(Color(0xFF1D4ED8), Color(0xFF1E3A8A))
        upper.contains("SBI") || upper.contains("STATE BANK") -> listOf(Color(0xFF0284C7), Color(0xFF1D4ED8))
        upper.contains("ICICI") -> listOf(Color(0xFFEA580C), Color(0xFF9A3412))
        upper.contains("AXIS") -> listOf(Color(0xFF9D174D), Color(0xFF6B21A8))
        upper.contains("KOTAK") -> listOf(Color(0xFFDC2626), Color(0xFF881337))
        upper.contains("CANARA") -> listOf(Color(0xFF0891B2), Color(0xFF0369A1))
        else -> listOf(Color(0xFF2563EB), Color(0xFF1E40AF))
    }
    return Brush.linearGradient(colors)
}

enum class TxDisplayStatus(val label: String) {
    COMPLETED("Completed"),
    PENDING("Pending"),
    NEEDS_REVIEW("Review"),
    FAILED("Failed");

    @Composable
    fun bgColor(): Color = when (this) {
        COMPLETED -> MonziColors.EmeraldSoft
        PENDING -> MonziColors.AmberSoft
        NEEDS_REVIEW -> MonziColors.OrangeSoft
        FAILED -> MonziColors.RoseSoft
    }

    @Composable
    fun fgColor(): Color = when (this) {
        COMPLETED -> MonziColors.EmeraldDark
        PENDING -> MonziColors.AmberDark
        NEEDS_REVIEW -> MonziColors.Orange
        FAILED -> MonziColors.RoseDark
    }
}

fun resolveTransactionDisplayStatus(tx: TransactionEntity): TxDisplayStatus {
    return when {
        tx.transactionStatus == "FAILED" || tx.syncStatus == "FAILED" -> TxDisplayStatus.FAILED
        tx.duplicateStatus == "POSSIBLE_DUPLICATE" ||
            tx.transferStatus == "DETECTED" ||
            tx.refundStatus == "DETECTED" ||
            tx.confidenceScore < 0.65 -> TxDisplayStatus.NEEDS_REVIEW
        tx.syncStatus == "PENDING" -> TxDisplayStatus.PENDING
        else -> TxDisplayStatus.COMPLETED
    }
}

@Composable
fun TransactionRowItem(
    tx: TransactionEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showStatusChip: Boolean = true
) {
    val isCredit = tx.direction == "CREDIT"
    val isTransferOrCcPay = tx.transactionType == "TRANSFER" || tx.transactionType == "CC_BILL_PAYMENT"
    val displayStatus = resolveTransactionDisplayStatus(tx)
    val catVisual = categoryVisualFor(
        categoryName = tx.categoryName,
        transactionType = tx.transactionType,
        direction = tx.direction,
        merchant = tx.merchant,
        explicitColorHex = tx.categoryColor
    )
    val badgeAccent = when {
        displayStatus == TxDisplayStatus.FAILED -> MonziColors.Rose
        isTransferOrCcPay -> MonziColors.Violet
        isCredit -> MonziColors.Emerald
        else -> catVisual.accentColor
    }
    val merchantTitle = tx.merchant?.takeIf { it.isNotBlank() }
        ?: if (isCredit) "Received Funds" else "Payment"
    val categoryLabel = tx.categoryName?.takeIf { it.isNotBlank() }
        ?: when (tx.transactionType) {
            "CC_BILL_PAYMENT" -> "Card Bill"
            "TRANSFER" -> "Self Transfer"
            "REFUND", "REVERSAL" -> "Refund"
            else -> if (isCredit) "Income" else "Uncategorized"
        }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MonziColors.CardSurface,
        border = BorderStroke(1.dp, MonziColors.GlassBorder),
        shadowElevation = if (MonziColors.IsDark) 0.dp else 1.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left: Category icon container with direction indicator dot
            Box(modifier = Modifier.size(44.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(badgeAccent.copy(alpha = if (MonziColors.IsDark) 0.18f else 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = catVisual.icon,
                        contentDescription = categoryLabel,
                        tint = badgeAccent,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MonziColors.CardSurface)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                displayStatus == TxDisplayStatus.FAILED -> MonziColors.Rose
                                isTransferOrCcPay -> MonziColors.Violet
                                isCredit -> MonziColors.Emerald
                                else -> MonziColors.Rose
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isTransferOrCcPay -> Icons.Rounded.SwapHoriz
                            isCredit -> Icons.Rounded.ArrowDownward
                            else -> Icons.Rounded.ArrowUpward
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(9.dp)
                    )
                }
            }

            // Middle: Merchant, Category chip + Payment mode, Date + Account
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = merchantTitle,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MonziColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (showStatusChip && displayStatus != TxDisplayStatus.COMPLETED) {
                        StatusPill(
                            label = displayStatus.label,
                            bgColor = displayStatus.bgColor(),
                            textColor = displayStatus.fgColor()
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeAccent.copy(alpha = if (MonziColors.IsDark) 0.16f else 0.10f)
                    ) {
                        Text(
                            text = categoryLabel,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = badgeAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "• ${tx.paymentMode}",
                        fontSize = 11.sp,
                        color = MonziColors.TextSecondary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = formatIstDateTime(tx.transactionDateMs),
                        fontSize = 11.sp,
                        color = MonziColors.TextMuted,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (tx.bankName != "Bank" || tx.accountMask != "XX0000") {
                        Text(
                            text = "• ${tx.bankName} ${tx.accountMask}",
                            fontSize = 11.sp,
                            color = MonziColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Right: Amount & type/tag + chevron
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                val amountPrefix = when {
                    displayStatus == TxDisplayStatus.FAILED -> ""
                    isTransferOrCcPay -> ""
                    isCredit -> "+"
                    else -> "-"
                }
                val amountColor = when {
                    displayStatus == TxDisplayStatus.FAILED -> MonziColors.TextMuted
                    isTransferOrCcPay -> MonziColors.Violet
                    isCredit -> MonziColors.Emerald
                    else -> MonziColors.TextPrimary
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$amountPrefix${formatInr(tx.amount)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = amountColor,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MonziColors.TextMuted.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                val typeTag = when {
                    tx.transactionType == "TRANSFER" -> "Self Transfer"
                    tx.transactionType == "CC_BILL_PAYMENT" -> "Card Payment"
                    tx.transactionType == "REFUND" -> "Refund Credit"
                    tx.transactionType == "REVERSAL" -> "Reversal"
                    tx.duplicateStatus == "EXACT_DUPLICATE" -> "Duplicate"
                    tx.transactionSource == "INCOMING_SMS" || tx.transactionSource == "HISTORICAL_SMS" -> "SMS"
                    else -> "Manual"
                }
                Text(
                    text = typeTag,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MonziColors.TextMuted,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonziFullBottomSheet(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    scrollable: Boolean = true,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MonziColors.GlassWhiteStrong,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = MonziColors.BorderLight)
        },
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = MonziColors.TextPrimary
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = MonziColors.TextMuted
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = MonziColors.TextMuted
                    )
                }
            }
            HorizontalDivider(color = MonziColors.DividerSubtle, modifier = Modifier.padding(top = 6.dp))

            val bodyModifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .let { if (scrollable) it.verticalScroll(rememberScrollState()) else it }
                .padding(horizontal = 20.dp, vertical = 14.dp)

            Column(
                modifier = bodyModifier,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )

            if (footer != null) {
                HorizontalDivider(color = MonziColors.DividerSubtle)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = footer
                )
            } else {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonziHalfBottomSheet(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MonziColors.GlassWhiteStrong,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = MonziColors.BorderLight)
        },
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MonziColors.TextPrimary
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = MonziColors.TextMuted
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = MonziColors.TextMuted
                    )
                }
            }
            content()
        }
    }
}

@Composable
fun MonziConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = true,
    isBusy: Boolean = false
) {
    val accentColor = if (isDestructive) MonziColors.Rose else MonziColors.BluePrimary
    val softColor = if (isDestructive) MonziColors.RoseSoft else MonziColors.BlueSoft

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        containerColor = MonziColors.GlassWhiteStrong,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(softColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MonziColors.TextPrimary,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = message,
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                color = MonziColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isBusy,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text(confirmLabel, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text("Cancel", color = MonziColors.TextSecondary, fontWeight = FontWeight.Medium)
            }
        }
    )
}

@Composable
fun MonziEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    MonziCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MonziColors.BlueSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MonziColors.BluePrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MonziColors.TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = MonziColors.TextMuted,
                textAlign = TextAlign.Center
            )
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                Spacer(Modifier.height(2.dp))
                OutlinedButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MonziColors.BluePrimary.copy(alpha = 0.35f))
                ) {
                    Text(actionLabel, color = MonziColors.BluePrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun SmoothSparkline(
    values: List<Double>,
    lineColor: Color,
    fillBrush: Brush = Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.25f), Color.Transparent)),
    modifier: Modifier = Modifier
) {
    val clean = if (values.size >= 2) values else listOf(0.0, 0.0)
    val minVal = clean.minOrNull() ?: 0.0
    val maxVal = clean.maxOrNull() ?: 0.0
    val span = (maxVal - minVal).takeIf { it > 0.001 } ?: 1.0

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val pts = clean.mapIndexed { idx, v ->
            val x = (idx.toFloat() / (clean.size - 1).coerceAtLeast(1)) * w
            val norm = ((v - minVal) / span).toFloat()
            val y = h - (norm * h * 0.78f) - h * 0.11f
            Offset(x, y)
        }

        val strokePath = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            for (i in 0 until pts.size - 1) {
                val p0 = pts[i]
                val p1 = pts[i + 1]
                val midX = (p0.x + p1.x) / 2f
                cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
            }
        }

        val fillPath = Path().apply {
            addPath(strokePath)
            lineTo(pts.last().x, h)
            lineTo(pts.first().x, h)
            close()
        }

        drawPath(path = fillPath, brush = fillBrush)
        drawPath(
            path = strokePath,
            color = lineColor,
            style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun SpendingTrendBarChart(
    labels: List<String>,
    values: List<Double>,
    modifier: Modifier = Modifier,
    barColor: Color = MonziColors.BluePrimary
) {
    if (values.isEmpty()) return
    val maxVal = values.maxOrNull()?.coerceAtLeast(100.0) ?: 100.0
    val gridColor = MonziColors.DividerSubtle

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
        ) {
            val chartH = size.height
            val slot = size.width / values.size.coerceAtLeast(1)
            val barW = (slot * 0.48f).coerceIn(6f, 24f)

            for (step in 1..3) {
                val y = chartH * (1f - step / 4f)
                drawLine(color = gridColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1.5f)
            }
            drawLine(color = gridColor, start = Offset(0f, chartH), end = Offset(size.width, chartH), strokeWidth = 2f)

            values.forEachIndexed { idx, v ->
                val cx = slot * idx + slot / 2f
                val h = ((v / maxVal).toFloat() * (chartH - 10f)).coerceAtLeast(if (v > 0.0) 5f else 0f)
                if (h > 0f) {
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(cx - barW / 2f, chartH - h),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                }
            }
        }
        if (labels.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(labels.first(), fontSize = 10.sp, color = MonziColors.TextMuted)
                if (labels.size > 2) {
                    Text(labels[labels.size / 2], fontSize = 10.sp, color = MonziColors.TextMuted)
                }
                Text(labels.last(), fontSize = 10.sp, color = MonziColors.TextMuted)
            }
        }
    }
}

@Composable
fun CashFlowBarChart(
    points: List<MonthlyCashFlowPoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return
    val maxVal = points.maxOf { maxOf(it.income, it.expense) }.coerceAtLeast(100.0)
    val totalIncome = points.sumOf { it.income }
    val totalExpense = points.sumOf { it.expense }
    val gridColor = MonziColors.DividerSubtle
    val emeraldColor = MonziColors.Emerald
    val roseColor = MonziColors.Rose

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendDot(emeraldColor, "Inflow (${formatCompactInr(totalIncome)})")
                LegendDot(roseColor, "Outflow (${formatCompactInr(totalExpense)})")
            }
        }
        Spacer(Modifier.height(14.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
        ) {
            val chartH = size.height
            val slot = size.width / points.size.coerceAtLeast(1)
            val singleBarW = (slot * 0.24f).coerceIn(8f, 22f)

            for (step in 1..3) {
                val y = chartH * (1f - step / 4f)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.5f
                )
            }
            drawLine(
                color = gridColor,
                start = Offset(0f, chartH),
                end = Offset(size.width, chartH),
                strokeWidth = 2f
            )

            points.forEachIndexed { idx, pt ->
                val cx = slot * idx + slot / 2f
                val incH = ((pt.income / maxVal).toFloat() * (chartH - 12f)).coerceAtLeast(if (pt.income > 0) 6f else 0f)
                val expH = ((pt.expense / maxVal).toFloat() * (chartH - 12f)).coerceAtLeast(if (pt.expense > 0) 6f else 0f)
                if (incH > 0f) {
                    drawRoundRect(
                        color = emeraldColor,
                        topLeft = Offset(cx - singleBarW - 2f, chartH - incH),
                        size = Size(singleBarW, incH),
                        cornerRadius = CornerRadius(7f, 7f)
                    )
                }
                if (expH > 0f) {
                    drawRoundRect(
                        color = roseColor,
                        topLeft = Offset(cx + 2f, chartH - expH),
                        size = Size(singleBarW, expH),
                        cornerRadius = CornerRadius(7f, 7f)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            points.forEach { pt ->
                Text(
                    text = pt.label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MonziColors.TextSecondary
                )
            }
        }
    }
}

@Composable
fun DailyTrendChart(
    points: List<DailyTrendPoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return
    val maxVal = points.maxOf { maxOf(it.income, it.expense) }.coerceAtLeast(100.0)
    val gridColor = MonziColors.DividerSubtle
    val emeraldColor = MonziColors.Emerald
    val blueColor = MonziColors.BluePrimary

    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendDot(emeraldColor, "Credits")
            LegendDot(blueColor, "Debits")
        }
        Spacer(Modifier.height(10.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(126.dp)
        ) {
            val chartH = size.height
            val slot = size.width / points.size.coerceAtLeast(1)
            val barW = (slot * 0.30f).coerceIn(5f, 16f)

            drawLine(
                color = gridColor,
                start = Offset(0f, chartH),
                end = Offset(size.width, chartH),
                strokeWidth = 2f
            )

            points.forEachIndexed { idx, pt ->
                val cx = slot * idx + slot / 2f
                val incH = ((pt.income / maxVal).toFloat() * (chartH - 8f)).coerceAtLeast(if (pt.income > 0) 5f else 0f)
                val expH = ((pt.expense / maxVal).toFloat() * (chartH - 8f)).coerceAtLeast(if (pt.expense > 0) 5f else 0f)
                if (incH > 0f) {
                    drawRoundRect(
                        color = emeraldColor,
                        topLeft = Offset(cx - barW - 1.5f, chartH - incH),
                        size = Size(barW, incH),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                }
                if (expH > 0f) {
                    drawRoundRect(
                        color = blueColor,
                        topLeft = Offset(cx + 1.5f, chartH - expH),
                        size = Size(barW, expH),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(points.first().label, fontSize = 10.sp, color = MonziColors.TextMuted)
            Text(points[points.size / 2].label, fontSize = 10.sp, color = MonziColors.TextMuted)
            Text(points.last().label, fontSize = 10.sp, color = MonziColors.TextMuted)
        }
    }
}

@Composable
fun CategoryDonutAndBars(
    items: List<CategoryBreakdownItem>,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val total = items.sumOf { it.amount }.coerceAtLeast(1.0)
    val topItems = items.take(6)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(116.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(108.dp)) {
                    var startAngle = -90f
                    val strokeWidth = 24f
                    topItems.forEach { item ->
                        val sweep = ((item.amount / total) * 360.0).toFloat()
                        drawArc(
                            color = parseHexColor(item.colorHex),
                            startAngle = startAngle,
                            sweepAngle = (sweep - 3f).coerceAtLeast(2f),
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        startAngle += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Net Spend", fontSize = 10.sp, color = MonziColors.TextMuted)
                    Text(
                        text = formatCompactInr(total),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MonziColors.TextPrimary
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                topItems.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColor(item.colorHex))
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = item.name,
                                fontSize = 12.sp,
                                color = MonziColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "${item.percentage.toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MonziColors.TextSecondary
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.take(8).forEach { item ->
                val color = parseHexColor(item.colorHex)
                val catVis = categoryVisualFor(item.name, explicitColorHex = item.colorHex)
                val animatedPct by animateFloatAsState(
                    targetValue = (item.percentage / 100.0).toFloat().coerceIn(0.03f, 1f),
                    animationSpec = tween(durationMillis = 450),
                    label = "cat_bar"
                )
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(color.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = catVis.icon,
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = "${item.name} (${item.count})",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MonziColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "${formatInr(item.amount)} (${item.percentage.toInt()}%)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MonziColors.TextPrimary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MonziColors.SurfaceMuted)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedPct)
                                .height(7.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(color)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(text = label, fontSize = 11.sp, color = MonziColors.TextSecondary, fontWeight = FontWeight.SemiBold)
    }
}
