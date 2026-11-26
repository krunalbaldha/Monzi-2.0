package com.krunal.monzi.data

import android.content.Context
import android.os.Build
import java.util.UUID

class AuthSessionManager(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveSession(session: UserSession) {
        prefs.edit().apply {
            putString(KEY_USER_ID, session.userId)
            putString(KEY_EMAIL, session.email)
            putString(KEY_ACCESS_TOKEN, session.accessToken)
            putString(KEY_REFRESH_TOKEN, session.refreshToken)
            putLong(KEY_EXPIRES_AT, session.expiresAtEpochSec)
            putBoolean(KEY_EMAIL_CONFIRMED, session.emailConfirmed)
            apply()
        }
    }

    fun getSession(): UserSession? {
        val userId = prefs.getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val access = prefs.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        val refresh = prefs.getString(KEY_REFRESH_TOKEN, null).orEmpty()
        val email = prefs.getString(KEY_EMAIL, null).orEmpty()
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        val confirmed = prefs.getBoolean(KEY_EMAIL_CONFIRMED, false)
        return UserSession(
            userId = userId,
            email = email,
            accessToken = access,
            refreshToken = refresh,
            expiresAtEpochSec = expiresAt,
            emailConfirmed = confirmed
        )
    }

    fun saveProfile(profile: UserProfile) {
        prefs.edit().apply {
            putString(KEY_FULL_NAME, profile.fullName)
            putString(KEY_EMAIL, profile.email)
            putString(KEY_CURRENCY, profile.currency)
            putString(KEY_SMS_PERMISSION, profile.smsPermission)
            putBoolean(KEY_BIOMETRIC_ENABLED, profile.biometricEnabled)
            if (profile.deviceSyncToken.isNotBlank()) {
                putString(KEY_DEVICE_SYNC_TOKEN, profile.deviceSyncToken)
            }
            apply()
        }
    }

    fun getCachedProfile(): UserProfile? {
        val userId = prefs.getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        return UserProfile(
            id = userId,
            fullName = prefs.getString(KEY_FULL_NAME, null)
                ?: prefs.getString(KEY_EMAIL, "")?.substringBefore("@").orEmpty().ifBlank { "Monzi User" },
            email = prefs.getString(KEY_EMAIL, "").orEmpty(),
            currency = prefs.getString(KEY_CURRENCY, "INR") ?: "INR",
            smsPermission = prefs.getString(KEY_SMS_PERMISSION, "prompt") ?: "prompt",
            biometricEnabled = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false),
            deviceSyncToken = prefs.getString(KEY_DEVICE_SYNC_TOKEN, "") ?: ""
        )
    }

    fun getUserId(): String = prefs.getString(KEY_USER_ID, "").orEmpty()

    fun getDeviceSyncToken(): String = prefs.getString(KEY_DEVICE_SYNC_TOKEN, "").orEmpty()

    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun isSessionLocked(): Boolean = prefs.getBoolean(KEY_SESSION_LOCKED, false)

    fun setSessionLocked(locked: Boolean) {
        prefs.edit().putBoolean(KEY_SESSION_LOCKED, locked).apply()
    }

    fun getRememberedEmail(): String = prefs.getString(KEY_REMEMBERED_EMAIL, "").orEmpty()

    fun setRememberedEmail(email: String?) {
        if (email.isNullOrBlank()) {
            prefs.edit().remove(KEY_REMEMBERED_EMAIL).apply()
        } else {
            prefs.edit().putString(KEY_REMEMBERED_EMAIL, email.trim()).apply()
        }
    }

    fun getOrCreateDeviceIdentifier(): String {
        val existing = prefs.getString(KEY_DEVICE_IDENTIFIER, null)
        if (!existing.isNullOrBlank()) return existing
        val model = (Build.MODEL ?: "android").replace(Regex("[^A-Za-z0-9_-]"), "-")
        val generated = "android-$model-${UUID.randomUUID().toString().take(8)}"
        prefs.edit().putString(KEY_DEVICE_IDENTIFIER, generated).apply()
        return generated
    }

    fun wasSmsPermissionGranted(): Boolean = prefs.getBoolean(KEY_SMS_WAS_GRANTED, false)

    fun setSmsPermissionGranted(granted: Boolean) {
        prefs.edit().putBoolean(KEY_SMS_WAS_GRANTED, granted).apply()
    }

    fun wasSmsPermissionRequested(): Boolean = prefs.getBoolean(KEY_SMS_PERMISSION_REQUESTED, false)

    fun setSmsPermissionRequested(requested: Boolean) {
        prefs.edit().putBoolean(KEY_SMS_PERMISSION_REQUESTED, requested).apply()
    }

    fun isBudgetAlertsEnabled(): Boolean = prefs.getBoolean(KEY_BUDGET_ALERTS_ENABLED, true)

    fun setBudgetAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BUDGET_ALERTS_ENABLED, enabled).apply()
    }

    fun isPendingCloudReset(): Boolean = prefs.getBoolean(KEY_PENDING_CLOUD_RESET, false)

    fun setPendingCloudReset(pending: Boolean) {
        prefs.edit().putBoolean(KEY_PENDING_CLOUD_RESET, pending).apply()
    }

    fun getThemeMode(): String = prefs.getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM"

    fun setThemeMode(mode: String) {
        val clean = if (mode in listOf("SYSTEM", "LIGHT", "DARK")) mode else "SYSTEM"
        prefs.edit().putString(KEY_THEME_MODE, clean).apply()
    }

    fun clearSession() {
        val remembered = getRememberedEmail()
        val deviceId = prefs.getString(KEY_DEVICE_IDENTIFIER, null)
        prefs.edit().clear().apply()
        prefs.edit().apply {
            if (remembered.isNotBlank()) putString(KEY_REMEMBERED_EMAIL, remembered)
            if (!deviceId.isNullOrBlank()) putString(KEY_DEVICE_IDENTIFIER, deviceId)
            apply()
        }
    }

    /**
     * Ensures a valid non-expired access token, refreshing automatically via Supabase if < 90s left.
     */
    fun getValidAccessToken(api: SupabaseApiClient): String? {
        val session = getSession() ?: return null
        val nowSec = System.currentTimeMillis() / 1000L
        if (session.expiresAtEpochSec - nowSec > 90L) {
            return session.accessToken
        }
        if (session.refreshToken.isBlank()) {
            return session.accessToken
        }
        return when (val refreshed = api.refreshSession(session.refreshToken)) {
            is ApiResult.Success -> {
                saveSession(refreshed.data)
                refreshed.data.accessToken
            }
            is ApiResult.Error -> {
                if (nowSec < session.expiresAtEpochSec) session.accessToken else null
            }
        }
    }

    companion object {
        const val PREFS_NAME = "monzi_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "user_email"
        private const val KEY_FULL_NAME = "full_name"
        private const val KEY_CURRENCY = "currency"
        private const val KEY_SMS_PERMISSION = "sms_permission"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at_epoch_sec"
        private const val KEY_EMAIL_CONFIRMED = "email_confirmed"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_SESSION_LOCKED = "session_locked"
        private const val KEY_DEVICE_SYNC_TOKEN = "device_sync_token"
        private const val KEY_REMEMBERED_EMAIL = "remembered_email"
        private const val KEY_DEVICE_IDENTIFIER = "device_identifier"
        private const val KEY_SMS_WAS_GRANTED = "sms_was_granted"
        private const val KEY_SMS_PERMISSION_REQUESTED = "sms_permission_requested"
        private const val KEY_BUDGET_ALERTS_ENABLED = "budget_alerts_enabled"
        private const val KEY_PENDING_CLOUD_RESET = "pending_cloud_reset"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}
