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
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val REQUEST_OVERLAY_CODE = 2001

    private lateinit var tvSystemStatus: TextView
    private lateinit var tvOverlayBadge: TextView
    private lateinit var tvAccessibilityBadge: TextView
    private lateinit var btnGrantOverlay: Button
    private lateinit var btnGrantAccessibility: Button
    private lateinit var btnLaunch: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvSystemStatus = findViewById(R.id.tvSystemStatus)
        tvOverlayBadge = findViewById(R.id.tvOverlayBadge)
        tvAccessibilityBadge = findViewById(R.id.tvAccessibilityBadge)
        btnGrantOverlay = findViewById(R.id.btnGrantOverlay)
        btnGrantAccessibility = findViewById(R.id.btnGrantAccessibility)
        btnLaunch = findViewById(R.id.btnLaunchFloating)

        btnGrantOverlay.setOnClickListener {
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

        btnGrantAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Cari dan aktifkan 'AutoChat'", Toast.LENGTH_LONG).show()
        }

        btnLaunch.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Aktifkan Izin Overlay terlebih dahulu!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "Penting: Aktifkan izin Aksesibilitas agar bisa ngetik otomatis!", Toast.LENGTH_LONG).show()
            }

            // Luncurkan widget melayang
            val serviceIntent = Intent(this, FloatingWidgetService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Toast.makeText(this, "Widget melayang aktif! Buka TikTok dan gass!", Toast.LENGTH_SHORT).show()
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

        if (hasOverlay) {
            tvOverlayBadge.text = "AKTIF ✓"
            tvOverlayBadge.setTextColor(Color.parseColor("#7EE787"))
            btnGrantOverlay.text = "Izin Overlay Sudah Aktif"
        } else {
            tvOverlayBadge.text = "Belum Aktif"
            tvOverlayBadge.setTextColor(Color.parseColor("#FFA726"))
            btnGrantOverlay.text = "Buka Izin Overlay"
        }

        if (hasAccessibility) {
            tvAccessibilityBadge.text = "AKTIF ✓"
            tvAccessibilityBadge.setTextColor(Color.parseColor("#7EE787"))
            btnGrantAccessibility.text = "Aksesibilitas Sudah Aktif"
        } else {
            tvAccessibilityBadge.text = "Belum Aktif"
            tvAccessibilityBadge.setTextColor(Color.parseColor("#FFA726"))
            btnGrantAccessibility.text = "Buka Pengaturan Aksesibilitas"
        }

        if (hasOverlay && hasAccessibility) {
            tvSystemStatus.text = "Semua Sistem Siap Beroperasi! 🚀"
            tvSystemStatus.setTextColor(Color.parseColor("#00E676"))
        } else {
            tvSystemStatus.text = "Izin Diperlukan Sebelum Memulai"
            tvSystemStatus.setTextColor(Color.parseColor("#FFA726"))
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
