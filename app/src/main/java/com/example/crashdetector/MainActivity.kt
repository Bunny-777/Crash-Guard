package com.example.crashdetector

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var etEmergencyContacts: EditText
    private lateinit var btnSaveContacts: Button
    private lateinit var switchTestMode: Switch
    private lateinit var btnSOS: Button
    private lateinit var btnStartDetection: Button
    private lateinit var btnStopDetection: Button
    private lateinit var tvStatus: TextView

    private val PERMISSION_REQUEST_CODE = 100
    private val OVERLAY_PERMISSION_REQUEST_CODE = 1234

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etEmergencyContacts = findViewById(R.id.etEmergencyContacts)
        btnSaveContacts = findViewById(R.id.btnSaveContacts)
        switchTestMode = findViewById(R.id.switchTestMode)
        btnSOS = findViewById(R.id.btnSos)
        btnStartDetection = findViewById(R.id.btnStartDetection)
        btnStopDetection = findViewById(R.id.btnStopDetection)
        tvStatus = findViewById(R.id.tvStatus)

        val prefs = getSharedPreferences("CrashDetectorPrefs", MODE_PRIVATE)

        val savedContacts = prefs.getString("emergency_contacts", "") ?: ""
        etEmergencyContacts.setText(savedContacts)

        // If emergency contact is empty on first startup, prompt user to enter it immediately!
        if (savedContacts.isEmpty()) {
            showEmergencyContactPromptDialog()
        }

        // Load saved test mode state
        val savedTestMode = prefs.getBoolean("test_mode", false)
        switchTestMode.isChecked = savedTestMode

        // Save contacts button
        btnSaveContacts.setOnClickListener {
            val contacts = etEmergencyContacts.text.toString().trim()
            if (contacts.isEmpty()) {
                Toast.makeText(this, "Please enter a valid emergency contact number.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putString("emergency_contacts", contacts).apply()
            Toast.makeText(this, "✅ Emergency contact saved successfully!", Toast.LENGTH_SHORT).show()
        }

        // Toggle test mode
        switchTestMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("test_mode", isChecked).apply()
            val msg = if (isChecked) "✅ Test Mode Enabled (shake phone to simulate crash)"
            else "🚗 Test Mode Disabled (real crash thresholds)"
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }

        // Manual SOS button
        btnSOS.setOnClickListener {
            if (checkAndRequestPermissions()) {
                SmsUtils.sendEmergency(this, true)
            }
        }

        // Start Detection button
        btnStartDetection.setOnClickListener {
            val currentContacts = prefs.getString("emergency_contacts", "") ?: ""
            if (currentContacts.isEmpty()) {
                Toast.makeText(this, "Please save an emergency contact first!", Toast.LENGTH_LONG).show()
                showEmergencyContactPromptDialog()
                return@setOnClickListener
            }
            if (checkAndRequestPermissions() && checkOverlayPermission()) {
                startCrashDetectionService()
            }
        }

        // Stop Detection button
        btnStopDetection.setOnClickListener {
            stopCrashDetectionService()
        }
    }

    private fun showEmergencyContactPromptDialog() {
        val input = EditText(this).apply {
            hint = "Enter phone number (e.g., +1234567890)"
            inputType = InputType.TYPE_CLASS_PHONE
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(this)
            .setTitle("🚨 Set Emergency Contact")
            .setMessage("Welcome to CrashGuard! Please enter your emergency contact number before starting protection.")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Save & Continue") { _, _ ->
                val number = input.text.toString().trim()
                if (number.isNotEmpty()) {
                    val prefs = getSharedPreferences("CrashDetectorPrefs", MODE_PRIVATE)
                    prefs.edit().putString("emergency_contacts", number).apply()
                    etEmergencyContacts.setText(number)
                    Toast.makeText(this, "Emergency contact saved!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Number cannot be empty. Please set it in the main screen.", Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }

    private fun checkAndRequestPermissions(): Boolean {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) permissionsNeeded.add(Manifest.permission.SEND_SMS)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) permissionsNeeded.add(Manifest.permission.READ_PHONE_STATE)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)

        return if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsNeeded.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
            false
        } else {
            true
        }
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
                false
            } else {
                true
            }
        } else {
            true
        }
    }

    private fun startCrashDetectionService() {
        val intent = Intent(this, SensorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
        tvStatus.text = "Status: Detection Active 🟢"
        tvStatus.setTextColor(Color.GREEN)
        Toast.makeText(this, "🚗 Crash detection started!", Toast.LENGTH_SHORT).show()
    }

    private fun stopCrashDetectionService() {
        val intent = Intent(this, SensorService::class.java)
        stopService(intent)
        tvStatus.text = "Status: Detection Inactive 🔴"
        tvStatus.setTextColor(Color.RED)
        Toast.makeText(this, "⏹️ Crash detection stopped.", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                startCrashDetectionService()
            } else {
                Toast.makeText(this, "Overlay permission required!", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                if (checkOverlayPermission()) {
                    startCrashDetectionService()
                }
            } else {
                Toast.makeText(this, "All permissions are required!", Toast.LENGTH_LONG).show()
            }
        }
    }
}
