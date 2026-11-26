package com.krunal.monzi.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krunal.monzi.data.UserProfile
import com.krunal.monzi.ui.components.MonziCard
import com.krunal.monzi.ui.theme.MonziColors

@Composable
fun LoginScreen(
    initialEmail: String,
    onSignIn: (email: String, password: String, rememberEmail: Boolean, onResult: (Boolean, String?) -> Unit) -> Unit,
    onRecoverPassword: (email: String, onDone: (Boolean, String) -> Unit) -> Unit,
    onNavigateToSignup: () -> Unit
) {
    var email by remember { mutableStateOf(initialEmail) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var rememberEmail by remember { mutableStateOf(true) }
    var submitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var showForgotDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf(initialEmail) }
    var resetFeedback by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BrandLogoHeader(subtitle = "Personal Finance Intelligence • Native Android")

        Spacer(Modifier.height(24.dp))

        MonziCard {
            if (!errorMsg.isNullOrBlank()) {
                ErrorBanner(errorMsg!!)
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address") },
                leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "Toggle password"
                        )
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = rememberEmail,
                        onCheckedChange = { rememberEmail = it }
                    )
                    Text("Remember email", fontSize = 12.sp, color = MonziColors.TextSecondary)
                }
                TextButton(onClick = {
                    resetEmail = email
                    resetFeedback = null
                    showForgotDialog = true
                }) {
                    Text("Forgot Password?", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        errorMsg = "Please enter both email and password."
                        return@Button
                    }
                    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
                        errorMsg = "Please enter a valid email address."
                        return@Button
                    }
                    errorMsg = null
                    submitting = true
                    onSignIn(email.trim(), password, rememberEmail) { ok, err ->
                        submitting = false
                        if (!ok) errorMsg = err ?: "Sign in failed."
                    }
                },
                enabled = !submitting,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MonziColors.BluePrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (submitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Signing In...", fontWeight = FontWeight.Bold)
                } else {
                    Text("Sign In", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("New to Monzi 2.0?", fontSize = 13.sp, color = MonziColors.TextSecondary)
                TextButton(onClick = onNavigateToSignup) {
                    Text("Create Account", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showForgotDialog) {
        var sendingReset by remember { mutableStateOf(false) }
        com.krunal.monzi.ui.components.MonziHalfBottomSheet(
            title = "Reset Password",
            subtitle = "Enter your account email and we will send a password reset link.",
            onDismiss = { if (!sendingReset) showForgotDialog = false }
        ) {
            OutlinedTextField(
                value = resetEmail,
                onValueChange = { resetEmail = it },
                label = { Text("Email Address") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            if (!resetFeedback.isNullOrBlank()) {
                Text(resetFeedback!!, fontSize = 12.sp, color = MonziColors.BluePrimary, fontWeight = FontWeight.SemiBold)
            }
            Button(
                enabled = !sendingReset && resetEmail.trim().contains("@") && resetEmail.trim().contains("."),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                onClick = {
                    sendingReset = true
                    onRecoverPassword(resetEmail.trim()) { _, msg ->
                        sendingReset = false
                        resetFeedback = msg
                    }
                }
            ) {
                Text(if (sendingReset) "Sending..." else "Send Reset Link", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SignupScreen(
    onSignUp: (fullName: String, email: String, password: String, onResult: (Boolean, Boolean, String?) -> Unit) -> Unit,
    onResendVerification: (email: String, onDone: (String) -> Unit) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var verificationSent by remember { mutableStateOf(false) }
    var resendMessage by remember { mutableStateOf<String?>(null) }

    val rules = listOf(
        "At least 8 characters" to (password.length >= 8),
        "One uppercase (A-Z)" to Regex("[A-Z]").containsMatchIn(password),
        "One lowercase (a-z)" to Regex("[a-z]").containsMatchIn(password),
        "One number (0-9)" to Regex("[0-9]").containsMatchIn(password)
    )
    val allRulesValid = rules.all { it.second }
    val passwordsMatch = password.isNotEmpty() && password == confirmPassword

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BrandLogoHeader(subtitle = "Create Your Private Finance Vault")
        Spacer(Modifier.height(24.dp))

        MonziCard {
            if (verificationSent) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MonziColors.EmeraldSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.MarkEmailRead, contentDescription = null, tint = MonziColors.EmeraldDark, modifier = Modifier.size(30.dp))
                    }
                    Text("Check your inbox", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MonziColors.TextPrimary)
                    Text(
                        "We sent a verification link to $email. Confirm your email, then sign in.",
                        fontSize = 13.sp,
                        color = MonziColors.TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    if (!resendMessage.isNullOrBlank()) {
                        Text(resendMessage!!, fontSize = 12.sp, color = MonziColors.BluePrimary, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { onResendVerification(email.trim()) { resendMessage = it } },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Resend Verification Email")
                    }
                    Button(
                        onClick = onNavigateToLogin,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Back to Sign In", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                if (!errorMsg.isNullOrBlank()) {
                    ErrorBanner(errorMsg!!)
                    Spacer(Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full Name") },
                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = null)
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MonziColors.BgCanvas,
                    border = BorderStroke(1.dp, MonziColors.BorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        rules.forEach { (label, valid) ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = if (valid) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (valid) MonziColors.EmeraldDark else MonziColors.TextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    label,
                                    fontSize = 11.sp,
                                    color = if (valid) MonziColors.EmeraldDark else MonziColors.TextMuted,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm Password") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (confirmPassword.isNotEmpty() && !passwordsMatch) {
                    Spacer(Modifier.height(4.dp))
                    Text("Passwords do not match", fontSize = 11.sp, color = MonziColors.RoseDark)
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (fullName.isBlank()) {
                            errorMsg = "Please enter your full name."
                            return@Button
                        }
                        if (!allRulesValid || !passwordsMatch) {
                            errorMsg = "Please fulfill all password requirements."
                            return@Button
                        }
                        errorMsg = null
                        submitting = true
                        onSignUp(fullName.trim(), email.trim(), password) { ok, needsVerify, err ->
                            submitting = false
                            if (!ok) {
                                errorMsg = err ?: "Could not create account."
                            } else if (needsVerify) {
                                verificationSent = true
                            }
                        }
                    },
                    enabled = !submitting && allRulesValid && passwordsMatch,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    if (submitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Creating Account...", fontWeight = FontWeight.Bold)
                    } else {
                        Text("Create Monzi Account", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Already have an account?", fontSize = 13.sp, color = MonziColors.TextSecondary)
                    TextButton(onClick = onNavigateToLogin) {
                        Text("Sign In", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun BiometricLockScreen(
    profile: UserProfile?,
    biometricAvailable: Boolean,
    onRequestBiometricPrompt: () -> Unit,
    onUnlockWithPassword: (String, (Boolean, String?) -> Unit) -> Unit,
    onSignOut: () -> Unit
) {
    var showPasswordField by remember { mutableStateOf(!biometricAvailable) }
    var password by remember { mutableStateOf("") }
    var unlocking by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MonziCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(listOf(MonziColors.BluePrimary, MonziColors.IndigoAccent))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (biometricAvailable) Icons.Rounded.Fingerprint else Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Text(
                    text = profile?.fullName?.ifBlank { "Monzi Vault" } ?: "Monzi Vault",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MonziColors.TextPrimary
                )
                if (!profile?.email.isNullOrBlank()) {
                    Text(profile!!.email, fontSize = 12.sp, color = MonziColors.TextMuted)
                }
                Text(
                    text = "Your personal finance ledger is locked. Verify your identity to continue.",
                    fontSize = 12.sp,
                    color = MonziColors.TextSecondary,
                    textAlign = TextAlign.Center
                )

                if (!errorMsg.isNullOrBlank()) {
                    ErrorBanner(errorMsg!!)
                }

                if (biometricAvailable) {
                    Button(
                        onClick = onRequestBiometricPrompt,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Icon(Icons.Rounded.Fingerprint, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Unlock with Biometrics", fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { showPasswordField = !showPasswordField }) {
                        Text(
                            if (showPasswordField) "Hide Password Option" else "Use Account Password Instead",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (showPasswordField) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Account Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (password.isBlank()) {
                                errorMsg = "Enter your password."
                                return@Button
                            }
                            errorMsg = null
                            unlocking = true
                            onUnlockWithPassword(password) { ok, err ->
                                unlocking = false
                                if (!ok) errorMsg = err ?: "Incorrect password."
                            }
                        },
                        enabled = !unlocking,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(if (unlocking) "Unlocking..." else "Unlock with Password", fontWeight = FontWeight.Bold)
                    }
                }

                TextButton(onClick = onSignOut) {
                    Text("Sign Out", color = MonziColors.RoseDark, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun BrandLogoHeader(subtitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(MonziColors.BluePrimary, MonziColors.IndigoAccent))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Monzi", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = MonziColors.TextPrimary)
            Surface(shape = RoundedCornerShape(8.dp), color = MonziColors.BluePrimary) {
                Text(
                    "2.0",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(subtitle, fontSize = 13.sp, color = MonziColors.TextSecondary)
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MonziColors.RoseSoft,
        border = BorderStroke(1.dp, MonziColors.Rose.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            color = MonziColors.RoseDark,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(12.dp)
        )
    }
}
