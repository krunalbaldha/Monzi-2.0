package com.krunal.monzi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var etServerUrl: EditText
    private lateinit var etSecret: EditText
    private lateinit var btnPermission: Button
    private lateinit var btnScanHistory: Button
    private lateinit var btnSaveSettings: Button

    private val SMS_PERMISSION_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        etServerUrl = findViewById(R.id.etServerUrl)
        etSecret = findViewById(R.id.etSecret)
        btnPermission = findViewById(R.id.btnPermission)
        btnScanHistory = findViewById(R.id.btnScanHistory)
        btnSaveSettings = findViewById(R.id.btnSaveSettings)

        val prefs = getSharedPreferences("monzi_prefs", Context.MODE_PRIVATE)
        etServerUrl.setText(prefs.getString("server_url", "http://10.0.2.2:3000/api/sync"))
        etSecret.setText(prefs.getString("sync_secret", "monzi_personal_sync_secret"))

        updatePermissionStatus()

        btnPermission.setOnClickListener {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS
                ),
                SMS_PERMISSION_CODE
            )
        }

        btnSaveSettings.setOnClickListener {
            prefs.edit().apply {
                putString("server_url", etServerUrl.text.toString().trim())
                putString("sync_secret", etSecret.text.toString().trim())
                apply()
            }
            Toast.makeText(this, "Settings Saved!", Toast.LENGTH_SHORT).show()
        }

        btnScanHistory.setOnClickListener {
            scanHistoricalSms()
        }
    }

    private fun updatePermissionStatus() {
        val hasReceive = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val hasRead = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

        if (hasReceive && hasRead) {
            tvStatus.text = "Status: Permissions Active & Listening"
            tvStatus.setTextColor(0xFF16A34A.toInt())
            btnPermission.isEnabled = false
            btnPermission.text = "Permissions Granted ✓"
        } else {
            tvStatus.text = "Status: SMS Permissions Required"
            tvStatus.setTextColor(0xFFE85D75.toInt())
            btnPermission.isEnabled = true
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SMS_PERMISSION_CODE) {
            updatePermissionStatus()
        }
    }

    private fun scanHistoricalSms() {
        val uri = Uri.parse("content://sms/inbox")
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, "date DESC LIMIT 500")

        var scanned = 0
        var detected = 0

        cursor?.use {
            val bodyIdx = it.getColumnIndex("body")
            val senderIdx = it.getColumnIndex("address")
            val dateIdx = it.getColumnIndex("date")

            while (it.moveToNext()) {
                scanned++
                val body = it.getString(bodyIdx) ?: ""
                val sender = it.getString(senderIdx) ?: ""
                val date = it.getLong(dateIdx)

                val parsed = BankSmsParser.parse(sender, body, date)
                if (parsed != null) {
                    detected++
                    SyncWorker.enqueueSync(this, parsed)
                }
            }
        }

        Toast.makeText(this, "Scanned $scanned SMS. Queued $detected transactions!", Toast.LENGTH_LONG).show()
    }
}
