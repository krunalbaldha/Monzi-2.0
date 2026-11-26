package com.krunal.monzi.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.Rule
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krunal.monzi.BuildConfig
import com.krunal.monzi.data.TransactionRuleEntity
import com.krunal.monzi.ui.MonziUiState
import com.krunal.monzi.ui.components.MonziCard
import com.krunal.monzi.ui.components.MonziSubScreenTopBar
import com.krunal.monzi.ui.components.StatusPill
import com.krunal.monzi.ui.theme.MonziColors
import com.krunal.monzi.ui.theme.MonziDimens
import com.krunal.monzi.ui.theme.formatIstDateTime
import com.krunal.monzi.ui.theme.parseHexColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: MonziUiState,
    biometricAvailable: Boolean,
    isBatteryOptimizedIgnored: Boolean,
    onBack: (() -> Unit)? = null,
    onUpdateProfileName: (String, (Boolean, String?) -> Unit) -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onToggleBudgetAlerts: (Boolean) -> Unit = {},
    onLockSession: () -> Unit,
    onSignOut: () -> Unit,
    onGrantSmsPermission: () -> Unit,
    onRequestBatteryWhitelist: () -> Unit,
    onScanInbox: (Int, Boolean) -> Unit,
    onOpenSmsCenter: () -> Unit,
    onSyncNow: () -> Unit,
    onRegenerateSyncToken: () -> Unit,
    onCreateRule: (String, String, Int, (Boolean, String?) -> Unit) -> Unit,
    onUpdateRule: (TransactionRuleEntity, String, String, Int, (Boolean, String?) -> Unit) -> Unit = { _, _, _, _, cb -> cb(true, null) },
    onToggleRule: (TransactionRuleEntity, Boolean) -> Unit,
    onDeleteRule: (TransactionRuleEntity) -> Unit,
    onCreateCategory: (String, String, Boolean, (Boolean, String?) -> Unit) -> Unit,
    onUpdateCategory: (com.krunal.monzi.data.CategoryEntity, String, String, Boolean, (Boolean, String?) -> Unit) -> Unit = { _, _, _, _, cb -> cb(true, null) },
    onDeleteCategory: (com.krunal.monzi.data.CategoryEntity, (Boolean, String?) -> Unit) -> Unit = { _, cb -> cb(true, null) },
    onExportCsv: (Long?, Long?, String?) -> Unit,
    onResetAllData: ((Boolean, String?) -> Unit) -> Unit,
    onChangeThemeMode: (String) -> Unit = {}
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val profile = state.profile

    var editingName by remember { mutableStateOf(false) }
    var draftName by remember(profile?.fullName) { mutableStateOf(profile?.fullName ?: "") }
    var profileError by remember { mutableStateOf<String?>(null) }
    var showToken by remember { mutableStateOf(false) }

    var showRuleDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<TransactionRuleEntity?>(null) }
    var ruleKeyword by remember { mutableStateOf("") }
    var ruleCategoryId by remember(state.categories) { mutableStateOf(state.categories.firstOrNull()?.id ?: "") }
    var ruleError by remember { mutableStateOf<String?>(null) }
    var ruleSaving by remember { mutableStateOf(false) }

    var showCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<com.krunal.monzi.data.CategoryEntity?>(null) }
    var catName by remember { mutableStateOf("") }
    var catColor by remember { mutableStateOf("#2468EB") }
    var catIsIncome by remember { mutableStateOf(false) }
    var catError by remember { mutableStateOf<String?>(null) }
    var catSaving by remember { mutableStateOf(false) }

    var showExportDialog by remember { mutableStateOf(false) }
    var exportDays by remember { mutableStateOf<Int?>(null) }
    var exportAccountId by remember { mutableStateOf<String?>(null) }

    var showResetDialog by remember { mutableStateOf(false) }
    var resetConfirmText by remember { mutableStateOf("") }
    var resetInProgress by remember { mutableStateOf(false) }
    var resetError by remember { mutableStateOf<String?>(null) }

    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val colorPresets = listOf("#2468EB", "#059669", "#F59E0B", "#E11D48", "#8B5CF6", "#EC4899", "#0EA5E9")
    val initialChar = profile?.fullName?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "M"

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
            if (onBack != null) {
                MonziSubScreenTopBar(
                    title = "Settings",
                    subtitle = "Secure. Personal. In Control.",
                    onBack = onBack
                )
            } else {
                Column {
                    Text(
                        text = "Settings",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.4).sp,
                        color = MonziColors.TextPrimary
                    )
                    Text(
                        text = "Secure. Personal. In Control.",
                        fontSize = 12.sp,
                        color = MonziColors.TextMuted
                    )
                }
            }
        }

        // A. Profile
        item {
            SectionLabel("PROFILE")
            Spacer(Modifier.height(6.dp))
            MonziCard(containerColor = MonziColors.GlassWhiteStrong) {
                if (editingName) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = draftName,
                                onValueChange = { draftName = it; profileError = null },
                                label = { Text("Full Name") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                if (draftName.trim().length < 2) {
                                    profileError = "Name must be at least 2 characters"
                                } else {
                                    onUpdateProfileName(draftName.trim()) { ok, err ->
                                        if (ok) editingName = false else profileError = err ?: "Update failed"
                                    }
                                }
                            }) {
                                Icon(Icons.Rounded.Check, contentDescription = "Save", tint = MonziColors.Emerald)
                            }
                        }
                        if (!profileError.isNullOrBlank()) {
                            Text(profileError!!, fontSize = 11.sp, color = MonziColors.Rose)
                        }
                    }
                } else {
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
                        OutlinedButton(
                            onClick = { editingName = true },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit Profile", modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Edit", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // B. Security
        item {
            SectionLabel("SECURITY")
            Spacer(Modifier.height(6.dp))
            MonziCard(containerColor = MonziColors.GlassWhiteStrong) {
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
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(MonziColors.BlueSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = MonziColors.BluePrimary, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("Biometric App Lock", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MonziColors.TextPrimary)
                            Text(
                                text = if (biometricAvailable) "Unlock with fingerprint or face" else "No biometric hardware enrolled",
                                fontSize = 11.sp,
                                color = MonziColors.TextMuted
                            )
                        }
                    }
                    Switch(
                        checked = profile?.biometricEnabled == true,
                        onCheckedChange = { onToggleBiometric(it) },
                        enabled = biometricAvailable || profile?.biometricEnabled == true
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onLockSession,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Lock Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MonziColors.Rose),
                        border = BorderStroke(1.dp, MonziColors.Rose.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sign Out", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // C. SMS Engine & Background Sync
        item {
            SectionLabel("SMS ENGINE & BACKGROUND SYNC")
            Spacer(Modifier.height(6.dp))
            MonziCard(containerColor = MonziColors.GlassWhiteStrong) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(MonziColors.EmeraldSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Message, contentDescription = null, tint = MonziColors.Emerald, modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Text("SMS Engine & Sync", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Last sync: ${formatIstDateTime(state.syncState.lastSyncAt)}", fontSize = 11.sp, color = MonziColors.TextMuted)
                        }
                    }
                    StatusPill(
                        label = if (state.hasSmsPermissions) "Active" else "Needed",
                        bgColor = if (state.hasSmsPermissions) MonziColors.EmeraldSoft else MonziColors.AmberSoft,
                        textColor = if (state.hasSmsPermissions) MonziColors.Emerald else MonziColors.AmberDark
                    )
                }

                Spacer(Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MonziColors.BgCanvas,
                    border = BorderStroke(1.dp, MonziColors.BorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Background Sync: ${if (isBatteryOptimizedIgnored) "Unrestricted (24/7)" else "Battery Optimized"}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isBatteryOptimizedIgnored) MonziColors.Emerald else MonziColors.AmberDark
                        )
                        Text(
                            text = "Scanned ${state.syncState.scannedCount} • Detected ${state.syncState.detectedCount} • Pending ${state.pendingSyncCount}",
                            fontSize = 11.sp,
                            color = MonziColors.TextMuted
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!state.hasSmsPermissions) {
                        Button(
                            onClick = onGrantSmsPermission,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant SMS Permissions", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    if (!isBatteryOptimizedIgnored) {
                        OutlinedButton(
                            onClick = onRequestBatteryWhitelist,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Disable Battery Optimization", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { onScanInbox(1000, false) },
                            enabled = state.hasSmsPermissions && !state.isScanningInbox,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Inbox, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (state.isScanningInbox) "Scanning..." else "Scan Inbox", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { onScanInbox(1500, true) },
                            enabled = state.hasSmsPermissions && !state.isScanningInbox,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Full Re-Scan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = onOpenSmsCenter,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("SMS Sync Center", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = onSyncNow,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Sync Now", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.Key, contentDescription = null, tint = MonziColors.Emerald, modifier = Modifier.size(16.dp))
                    Text("Device Sync Token", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MonziColors.TextSecondary)
                }
                Spacer(Modifier.height(6.dp))
                val tok = profile?.deviceSyncToken.orEmpty()
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MonziColors.BgCanvas,
                    border = BorderStroke(1.dp, MonziColors.BorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                tok.isBlank() -> "Provisioning..."
                                showToken -> tok
                                else -> "${tok.take(8)}••••••••••••${tok.takeLast(6)}"
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(if (showToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(17.dp))
                        }
                        IconButton(onClick = { if (tok.isNotBlank()) clipboard.setText(AnnotatedString(tok)) }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(17.dp))
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onRegenerateSyncToken) {
                        Text("Regenerate Token", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    if (state.devices.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Rounded.Smartphone, contentDescription = null, modifier = Modifier.size(14.dp), tint = MonziColors.TextMuted)
                            Text("${state.devices.size} device(s)", fontSize = 11.sp, color = MonziColors.TextMuted)
                        }
                    }
                }
            }
        }

        // D. Automation
        item {
            SectionLabel("AUTOMATION")
            Spacer(Modifier.height(6.dp))
            MonziCard(containerColor = MonziColors.GlassWhiteStrong) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.Rule, contentDescription = null, tint = MonziColors.BluePrimary, modifier = Modifier.size(20.dp))
                        Column {
                            Text("Auto-Categorization Rules (${state.rules.size})", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Keyword & merchant matching", fontSize = 11.sp, color = MonziColors.TextMuted)
                        }
                    }
                    TextButton(onClick = {
                        editingRule = null
                        ruleKeyword = ""
                        ruleCategoryId = state.categories.firstOrNull()?.id ?: ""
                        ruleError = null
                        showRuleDialog = true
                    }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Merchant Matching & Transaction Processing Rules status cards
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MonziColors.BgCanvas,
                    border = BorderStroke(1.dp, MonziColors.BorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Storefront, contentDescription = null, tint = MonziColors.Emerald, modifier = Modifier.size(16.dp))
                            Text("Merchant Matching: 35+ built-in UPI & bank rules active", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MonziColors.TextPrimary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MonziColors.BluePrimary, modifier = Modifier.size(16.dp))
                            Text("Transaction Processing Rules: Duplicate, refund & self-transfer engine active", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MonziColors.TextPrimary)
                        }
                    }
                }

                if (state.rules.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.rules.forEach { rule ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MonziColors.BgCanvas,
                                border = BorderStroke(1.dp, MonziColors.BorderLight),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("\"${rule.matchingRule}\"", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            "→ ${rule.categoryName ?: "Category"} (Priority ${rule.priority})",
                                            fontSize = 11.sp,
                                            color = MonziColors.TextSecondary
                                        )
                                    }
                                    Switch(checked = rule.enabled, onCheckedChange = { onToggleRule(rule, it) })
                                    IconButton(onClick = {
                                        editingRule = rule
                                        ruleKeyword = rule.matchingRule
                                        ruleCategoryId = rule.categoryId
                                        ruleError = null
                                        showRuleDialog = true
                                    }) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "Edit Rule", tint = MonziColors.BluePrimary, modifier = Modifier.size(17.dp))
                                    }
                                    IconButton(onClick = { onDeleteRule(rule) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Delete Rule", tint = MonziColors.Rose, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Categories (${state.categories.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = {
                        editingCategory = null
                        catName = ""
                        catColor = "#2468EB"
                        catIsIncome = false
                        catError = null
                        showCategoryDialog = true
                    }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Text("New", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.categories.forEach { cat ->
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MonziColors.BgCanvas,
                            border = BorderStroke(1.dp, MonziColors.BorderLight),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable {
                                    editingCategory = cat
                                    catName = cat.name
                                    catColor = cat.color
                                    catIsIncome = cat.isIncome
                                    catError = null
                                    showCategoryDialog = true
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val vis = com.krunal.monzi.ui.components.categoryVisualFor(cat.name, direction = if (cat.isIncome) "CREDIT" else "DEBIT", explicitColorHex = cat.color)
                                Icon(vis.icon, contentDescription = null, tint = parseHexColor(cat.color), modifier = Modifier.size(14.dp))
                                Text(cat.name, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                if (cat.isIncome) {
                                    Text("IN", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MonziColors.EmeraldDark)
                                }
                            }
                        }
                    }
                }
            }
        }

        // E. Additional Settings
        item {
            SectionLabel("ADDITIONAL SETTINGS")
            Spacer(Modifier.height(6.dp))
            MonziCard(containerColor = MonziColors.GlassWhiteStrong) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Appearance Theme", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            when (state.themeMode.uppercase()) {
                                "DARK" -> "Obsidian Dark"
                                "LIGHT" -> "Light Mode"
                                else -> "Match System"
                            },
                            fontSize = 11.sp,
                            color = MonziColors.TextMuted
                        )
                    }
                    OutlinedButton(onClick = { showThemeDialog = true }, shape = RoundedCornerShape(12.dp)) {
                        Text("Change", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.Notifications, contentDescription = null, tint = MonziColors.AmberDark, modifier = Modifier.size(20.dp))
                        Column {
                            Text("Notifications & Alerts", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Budget thresholds & transaction alerts", fontSize = 11.sp, color = MonziColors.TextMuted)
                        }
                    }
                    Switch(checked = state.budgetAlertsEnabled, onCheckedChange = onToggleBudgetAlerts)
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { showExportDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Data Backup & Restore (Export CSV)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showPrivacyDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.PrivacyTip, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Privacy", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { showAboutDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("About Monzi", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        resetConfirmText = ""
                        resetError = null
                        resetInProgress = false
                        showResetDialog = true
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MonziColors.Rose),
                    border = BorderStroke(1.dp, MonziColors.Rose.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reset All Financial Data", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showRuleDialog) {
        com.krunal.monzi.ui.sheets.RuleEditorSheet(
            editingRule = editingRule,
            categories = state.categories,
            onDismiss = { showRuleDialog = false },
            onSave = { existing, kw, catId, prio, cb ->
                if (existing == null) {
                    onCreateRule(kw, catId, prio) { ok, err ->
                        if (ok) showRuleDialog = false
                        cb(ok, err)
                    }
                } else {
                    onUpdateRule(existing, kw, catId, prio) { ok, err ->
                        if (ok) showRuleDialog = false
                        cb(ok, err)
                    }
                }
            }
        )
    }

    if (showCategoryDialog) {
        com.krunal.monzi.ui.sheets.CategoryEditorSheet(
            editingCategory = editingCategory,
            onDismiss = { showCategoryDialog = false },
            onSave = { existing, name, color, isInc, cb ->
                if (existing == null) {
                    onCreateCategory(name, color, isInc) { ok, err ->
                        if (ok) showCategoryDialog = false
                        cb(ok, err)
                    }
                } else {
                    onUpdateCategory(existing, name, color, isInc) { ok, err ->
                        if (ok) showCategoryDialog = false
                        cb(ok, err)
                    }
                }
            },
            onDelete = { cat, cb ->
                onDeleteCategory(cat) { ok, err ->
                    if (ok) showCategoryDialog = false
                    cb(ok, err)
                }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { if (!resetInProgress) showResetDialog = false },
            containerColor = MonziColors.GlassWhiteStrong,
            title = { Text("Reset All Data?", fontWeight = FontWeight.Bold, color = MonziColors.Rose) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This permanently deletes all transactions, accounts, budgets, rules, and SMS logs. Type RESET to confirm.",
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = resetConfirmText,
                        onValueChange = { resetConfirmText = it; resetError = null },
                        placeholder = { Text("Type RESET") },
                        singleLine = true,
                        enabled = !resetInProgress,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!resetError.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MonziColors.RoseSoft,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = resetError!!,
                                fontSize = 12.sp,
                                color = MonziColors.RoseDark,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (resetConfirmText.trim().equals("RESET", ignoreCase = true)) {
                            resetError = null
                            resetInProgress = true
                            onResetAllData { ok, err ->
                                resetInProgress = false
                                if (ok) {
                                    showResetDialog = false
                                } else {
                                    resetError = err ?: "Failed to reset data. Please try again."
                                }
                            }
                        }
                    },
                    enabled = !resetInProgress && resetConfirmText.trim().equals("RESET", ignoreCase = true),
                    colors = ButtonDefaults.buttonColors(containerColor = MonziColors.Rose)
                ) { Text(if (resetInProgress) "Resetting..." else "Confirm Reset") }
            },
            dismissButton = {
                TextButton(enabled = !resetInProgress, onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showExportDialog) {
        com.krunal.monzi.ui.sheets.ExportCsvSheet(
            accounts = state.accounts,
            onDismiss = { showExportDialog = false },
            onExport = { s, e, accId ->
                onExportCsv(s, e, accId)
                showExportDialog = false
            }
        )
    }

    if (showPrivacyDialog) {
        com.krunal.monzi.ui.sheets.PrivacyInfoSheet(onDismiss = { showPrivacyDialog = false })
    }

    if (showAboutDialog) {
        com.krunal.monzi.ui.sheets.AboutInfoSheet(onDismiss = { showAboutDialog = false })
    }

    if (showThemeDialog) {
        com.krunal.monzi.ui.sheets.ThemeSelectorSheet(
            currentMode = state.themeMode,
            onSelect = { mode ->
                onChangeThemeMode(mode)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = MonziColors.TextMuted,
        modifier = Modifier.padding(start = 4.dp)
    )
}
