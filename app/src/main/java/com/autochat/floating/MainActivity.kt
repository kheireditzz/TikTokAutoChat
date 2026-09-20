package com.autochat.floating

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val REQUEST_OVERLAY_CODE = 2001

    private lateinit var tvSystemStatus: TextView
    private lateinit var mainStatusDot: View
    private lateinit var switchOverlay: Switch
    private lateinit var switchAccessibility: Switch
    private lateinit var btnGrantOverlay: Button
    private lateinit var btnGrantAccessibility: Button
    private lateinit var btnLaunch: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvSystemStatus = findViewById(R.id.tvSystemStatus)
        mainStatusDot = findViewById(R.id.mainStatusDot)
        switchOverlay = findViewById(R.id.switchOverlay)
        switchAccessibility = findViewById(R.id.switchAccessibility)
        btnGrantOverlay = findViewById(R.id.btnGrantOverlay)
        btnGrantAccessibility = findViewById(R.id.btnGrantAccessibility)
        btnLaunch = findViewById(R.id.btnLaunchFloating)

        val overlayAction = View.OnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivityForResult(intent, REQUEST_OVERLAY_CODE)
                } else {
                    Toast.makeText(this, "Izin Overlay sudah aktif! ✅", Toast.LENGTH_SHORT).show()
                }
            }
        }
        btnGrantOverlay.setOnClickListener(overlayAction)
        switchOverlay.setOnClickListener(overlayAction)

        val accessibilityAction = View.OnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Cari dan aktifkan 'AutoChat'", Toast.LENGTH_LONG).show()
        }
        btnGrantAccessibility.setOnClickListener(accessibilityAction)
        switchAccessibility.setOnClickListener(accessibilityAction)

        btnLaunch.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Aktifkan Izin Overlay terlebih dahulu!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "Penting: Aktifkan izin Aksesibilitas agar bisa mengetik otomatis!", Toast.LENGTH_LONG).show()
            }

            // Luncurkan widget melayang
            val serviceIntent = Intent(this, FloatingWidgetService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Toast.makeText(this, "Widget melayang aktif!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
    }

    private fun updatePermissionStatuses() {
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        val hasAccessibility = isAccessibilityServiceEnabled()

        switchOverlay.isChecked = hasOverlay
        switchAccessibility.isChecked = hasAccessibility

        if (hasOverlay) {
            btnGrantOverlay.text = "Izin Overlay Sudah Aktif"
            btnGrantOverlay.setTextColor(Color.parseColor("#10B981"))
        } else {
            btnGrantOverlay.text = "Buka Izin Overlay"
            btnGrantOverlay.setTextColor(Color.parseColor("#EF4444"))
        }

        if (hasAccessibility) {
            btnGrantAccessibility.text = "Aksesibilitas Sudah Aktif"
            btnGrantAccessibility.setTextColor(Color.parseColor("#10B981"))
        } else {
            btnGrantAccessibility.text = "Buka Pengaturan Aksesibilitas"
            btnGrantAccessibility.setTextColor(Color.parseColor("#EF4444"))
        }

        if (hasOverlay && hasAccessibility) {
            tvSystemStatus.text = "Semua Sistem Siap Beroperasi!"
            tvSystemStatus.setTextColor(Color.parseColor("#10B981"))
            mainStatusDot.setBackgroundColor(Color.parseColor("#10B981"))
        } else {
            tvSystemStatus.text = "Periksa Izin Di Bawah"
            tvSystemStatus.setTextColor(Color.parseColor("#F59E0B"))
            mainStatusDot.setBackgroundColor(Color.parseColor("#F59E0B"))
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (service in enabledServices) {
            if (service.resolveInfo.serviceInfo.packageName == packageName) {
                return true
            }
        }
        return false
    }
}
