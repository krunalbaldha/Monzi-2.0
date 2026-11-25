package com.krunal.monzi

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var settingsOverlay: ScrollView
    private lateinit var btnNativeSettings: ImageButton
    private lateinit var btnCloseSettings: Button
    private lateinit var tvStatus: TextView
    private lateinit var etServerUrl: EditText
    private lateinit var etSyncToken: EditText
    private lateinit var etSecret: EditText
    private lateinit var btnPermission: Button
    private lateinit var btnScanHistory: Button
    private lateinit var btnSaveSettings: Button
    private lateinit var btnReloadWeb: Button

    private val bgExecutor = Executors.newSingleThreadExecutor()

    companion object {
        private const val SMS_PERMISSION_CODE = 101
        private const val DEFAULT_WEB_URL = "https://ara20.vercel.app"
    }

    private val smsSyncedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SyncWorker.ACTION_SMS_SYNCED) {
                runOnUiThread {
                    webView.evaluateJavascript(
                        "window.dispatchEvent(new CustomEvent('monzi:sms-synced'));",
                        null
                    )
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        settingsOverlay = findViewById(R.id.settingsOverlay)
        btnNativeSettings = findViewById(R.id.btnNativeSettings)
        btnCloseSettings = findViewById(R.id.btnCloseSettings)
        tvStatus = findViewById(R.id.tvStatus)
        etServerUrl = findViewById(R.id.etServerUrl)
        etSyncToken = findViewById(R.id.etSyncToken)
        etSecret = findViewById(R.id.etSecret)
        btnPermission = findViewById(R.id.btnPermission)
        btnScanHistory = findViewById(R.id.btnScanHistory)
        btnSaveSettings = findViewById(R.id.btnSaveSettings)
        btnReloadWeb = findViewById(R.id.btnReloadWeb)

        loadSettingsIntoUi()
        configureWebView()
        updatePermissionStatus()

        btnNativeSettings.setOnClickListener {
            loadSettingsIntoUi()
            updatePermissionStatus()
            settingsOverlay.visibility = View.VISIBLE
        }

        btnCloseSettings.setOnClickListener {
            settingsOverlay.visibility = View.GONE
        }

        btnPermission.setOnClickListener {
            requestSmsPermissionsInternal()
        }

        btnSaveSettings.setOnClickListener {
            val prefs = getSharedPreferences(SyncWorker.PREFS_NAME, Context.MODE_PRIVATE)
            val url = etServerUrl.text.toString().trim().ifEmpty { SyncWorker.DEFAULT_SYNC_URL }
            val syncToken = etSyncToken.text.toString().trim()
            val secret = etSecret.text.toString().trim()

            prefs.edit().apply {
                putString("server_url", url)
                putString("device_sync_token", syncToken)
                putString("sync_secret", secret)
                apply()
            }
            Toast.makeText(this, "Companion settings saved", Toast.LENGTH_SHORT).show()
            settingsOverlay.visibility = View.GONE
        }

        btnScanHistory.setOnClickListener {
            scanHistoricalSmsAsync(500, notifyWeb = true)
        }

        btnReloadWeb.setOnClickListener {
            settingsOverlay.visibility = View.GONE
            webView.loadUrl(resolveWebBaseUrl())
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (settingsOverlay.visibility == View.VISIBLE) {
                    settingsOverlay.visibility = View.GONE
                } else if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        webView.loadUrl(resolveWebBaseUrl())
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(SyncWorker.ACTION_SMS_SYNCED)
        ContextCompat.registerReceiver(
            this,
            smsSyncedReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(smsSyncedReceiver)
        } catch (_: IllegalArgumentException) {
            // Receiver already unregistered
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bgExecutor.shutdown()
    }

    private fun resolveWebBaseUrl(): String {
        val prefs = getSharedPreferences(SyncWorker.PREFS_NAME, Context.MODE_PRIVATE)
        val syncUrl = prefs.getString("server_url", SyncWorker.DEFAULT_SYNC_URL) ?: SyncWorker.DEFAULT_SYNC_URL
        return try {
            val parsed = Uri.parse(syncUrl)
            if (!parsed.scheme.isNullOrBlank() && !parsed.authority.isNullOrBlank()) {
                "${parsed.scheme}://${parsed.authority}"
            } else {
                DEFAULT_WEB_URL
            }
        } catch (_: Exception) {
            DEFAULT_WEB_URL
        }
    }

    private fun loadSettingsIntoUi() {
        val prefs = getSharedPreferences(SyncWorker.PREFS_NAME, Context.MODE_PRIVATE)
        etServerUrl.setText(prefs.getString("server_url", SyncWorker.DEFAULT_SYNC_URL))
        etSyncToken.setText(prefs.getString("device_sync_token", ""))
        etSecret.setText(prefs.getString("sync_secret", ""))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = "$userAgentString MonziAndroidApp/2.0"
        }

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return false
                val scheme = url.scheme?.lowercase()
                if (scheme == "http" || scheme == "https") {
                    return false
                }
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, url))
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }

        webView.addJavascriptInterface(MonziNativeBridge(), "MonziNative")
    }

    private fun hasSmsPermissions(): Boolean {
        val hasReceive = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        val hasRead = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        return hasReceive && hasRead
    }

    private fun computePermissionState(): String {
        if (hasSmsPermissions()) return "granted"
        val prefs = getSharedPreferences(SyncWorker.PREFS_NAME, Context.MODE_PRIVATE)
        val requested = prefs.getBoolean("sms_permission_requested", false)
        return if (requested) "denied" else "prompt"
    }

    private fun updatePermissionStatus() {
        if (hasSmsPermissions()) {
            tvStatus.text = "Status: SMS Permissions Active & Listening"
            tvStatus.setTextColor(0xFF16A34A.toInt())
            btnPermission.isEnabled = false
            btnPermission.text = "Permissions Granted"
        } else {
            tvStatus.text = "Status: SMS Permissions Required"
            tvStatus.setTextColor(0xFFDC2626.toInt())
            btnPermission.isEnabled = true
            btnPermission.text = "Grant SMS Permissions"
        }
    }

    private fun requestSmsPermissionsInternal() {
        if (hasSmsPermissions()) {
            updatePermissionStatus()
            notifyWebPermissionState("granted")
            return
        }
        val prefs = getSharedPreferences(SyncWorker.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("sms_permission_requested", true).apply()

        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS
            ),
            SMS_PERMISSION_CODE
        )
    }

    private fun notifyWebPermissionState(state: String) {
        runOnUiThread {
            webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('monzi:sms-permission-changed', { detail: { state: '$state' } }));",
                null
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SMS_PERMISSION_CODE) {
            updatePermissionStatus()
            val state = if (hasSmsPermissions()) "granted" else "denied"
            notifyWebPermissionState(state)
        }
    }

    private fun scanHistoricalSmsAsync(limit: Int, notifyWeb: Boolean) {
        if (!hasSmsPermissions()) {
            runOnUiThread {
                Toast.makeText(this, "Grant SMS permissions first to scan inbox", Toast.LENGTH_SHORT).show()
                if (notifyWeb) {
                    webView.evaluateJavascript(
                        "if (window.onMonziNativeSmsScanComplete) window.onMonziNativeSmsScanComplete(0, 0);",
                        null
                    )
                }
            }
            return
        }

        val safeLimit = limit.coerceIn(1, 2000)
        bgExecutor.execute {
            var scanned = 0
            var detected = 0
            try {
                val uri = Uri.parse("content://sms/inbox")
                val cursor: Cursor? = contentResolver.query(
                    uri,
                    arrayOf("body", "address", "date"),
                    null,
                    null,
                    "date DESC LIMIT $safeLimit"
                )

                cursor?.use {
                    val bodyIdx = it.getColumnIndex("body")
                    val senderIdx = it.getColumnIndex("address")
                    val dateIdx = it.getColumnIndex("date")

                    while (it.moveToNext()) {
                        scanned++
                        val body = if (bodyIdx >= 0) it.getString(bodyIdx) ?: "" else ""
                        val sender = if (senderIdx >= 0) it.getString(senderIdx) ?: "" else ""
                        val date = if (dateIdx >= 0) it.getLong(dateIdx) else System.currentTimeMillis()

                        // Parse in memory only; never store or send raw SMS body
                        val parsed = BankSmsParser.parse(sender, body, date)
                        if (parsed != null) {
                            detected++
                            SyncWorker.enqueueSync(applicationContext, parsed, sender)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MonziHistoryScan", "Error scanning SMS inbox", e)
            }

            runOnUiThread {
                Toast.makeText(
                    this,
                    "Scanned $scanned SMS. Queued $detected financial transactions.",
                    Toast.LENGTH_LONG
                ).show()
                if (notifyWeb) {
                    webView.evaluateJavascript(
                        "if (window.onMonziNativeSmsScanComplete) window.onMonziNativeSmsScanComplete($scanned, $detected); window.dispatchEvent(new CustomEvent('monzi:sms-synced'));",
                        null
                    )
                }
            }
        }
    }

    inner class MonziNativeBridge {

        @JavascriptInterface
        fun isAndroidApp(): Boolean = true

        @JavascriptInterface
        fun getSmsPermissionState(): String = computePermissionState()

        @JavascriptInterface
        fun requestSmsPermission() {
            runOnUiThread {
                requestSmsPermissionsInternal()
            }
        }

        @JavascriptInterface
        fun setUserSyncAuth(serverUrl: String?, syncToken: String?, accessToken: String?) {
            val prefs = getSharedPreferences(SyncWorker.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                if (!serverUrl.isNullOrBlank()) {
                    putString("server_url", serverUrl.trim())
                }
                if (!syncToken.isNullOrBlank()) {
                    putString("device_sync_token", syncToken.trim())
                }
                if (!accessToken.isNullOrBlank()) {
                    putString("access_token", accessToken.trim())
                }
                apply()
            }
        }

        @JavascriptInterface
        fun scanHistoricalSms(limit: Int) {
            scanHistoricalSmsAsync(if (limit > 0) limit else 500, notifyWeb = true)
        }

        @JavascriptInterface
        fun openNativeSettings() {
            runOnUiThread {
                loadSettingsIntoUi()
                updatePermissionStatus()
                settingsOverlay.visibility = View.VISIBLE
            }
        }
    }
}
