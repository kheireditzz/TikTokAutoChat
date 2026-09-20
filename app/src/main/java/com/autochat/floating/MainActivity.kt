package com.autochat.floating

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val REQUEST_OVERLAY_CODE = 2001
    private lateinit var prefs: SharedPreferences

    // Navigation Header Button (Gear saat di Beranda, Back saat di Pengaturan)
    private lateinit var btnHeaderAction: ImageView
    private lateinit var tvHeaderTitle: TextView
    private lateinit var tvHeaderSub: TextView
    private lateinit var viewHome: View
    private lateinit var viewSettings: View
    private var isSettingsOpen = false

    // Home views
    private lateinit var tvStatusTitle: TextView
    private lateinit var tvStatusSub: TextView
    private lateinit var switchOverlay: Switch
    private lateinit var switchAccessibility: Switch
    private lateinit var btnLaunch: Button

    // Settings views
    private lateinit var etSettingMsg1: EditText
    private lateinit var etSettingMsg2: EditText
    private lateinit var etSettingMsg3: EditText
    private lateinit var etSettingDelay: EditText
    private lateinit var switchAntiSpam: Switch
    private lateinit var btnSaveSettings: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("AutoChatPrefs", Context.MODE_PRIVATE)

        // Init Header & Navigation
        btnHeaderAction = findViewById(R.id.btnHeaderAction)
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        tvHeaderSub = findViewById(R.id.tvHeaderSub)
        viewHome = findViewById(R.id.viewHome)
        viewSettings = findViewById(R.id.viewSettings)

        btnHeaderAction.setOnClickListener {
            toggleScreen(!isSettingsOpen)
        }

        // Init Home
        tvStatusTitle = findViewById(R.id.tvStatusTitle)
        tvStatusSub = findViewById(R.id.tvStatusSub)
        switchOverlay = findViewById(R.id.switchOverlay)
        switchAccessibility = findViewById(R.id.switchAccessibility)
        btnLaunch = findViewById(R.id.btnLaunchFloating)

        // Init Settings
        etSettingMsg1 = findViewById(R.id.etSettingMsg1)
        etSettingMsg2 = findViewById(R.id.etSettingMsg2)
        etSettingMsg3 = findViewById(R.id.etSettingMsg3)
        etSettingDelay = findViewById(R.id.etSettingDelay)
        switchAntiSpam = findViewById(R.id.switchAntiSpam)
        btnSaveSettings = findViewById(R.id.btnSaveSettings)

        loadSavedSettings()

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
        findViewById<View>(R.id.cardOverlay).setOnClickListener(overlayAction)
        switchOverlay.setOnClickListener(overlayAction)

        val accessibilityAction = View.OnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Cari dan aktifkan 'AutoChat'", Toast.LENGTH_LONG).show()
        }
        findViewById<View>(R.id.cardAccessibility).setOnClickListener(accessibilityAction)
        switchAccessibility.setOnClickListener(accessibilityAction)

        btnSaveSettings.setOnClickListener {
            val m1 = etSettingMsg1.text.toString().trim()
            val m2 = etSettingMsg2.text.toString().trim()
            val m3 = etSettingMsg3.text.toString().trim()
            val d = etSettingDelay.text.toString().toIntOrNull() ?: 4
            val anti = switchAntiSpam.isChecked

            prefs.edit()
                .putString("msg1", m1)
                .putString("msg2", m2)
                .putString("msg3", m3)
                .putInt("delay", d)
                .putBoolean("anti_spam", anti)
                .apply()

            Toast.makeText(this, "Pengaturan berhasil disimpan secara permanen! ✅", Toast.LENGTH_SHORT).show()
        }

        btnLaunch.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Aktifkan Izin Overlay terlebih dahulu!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "Penting: Aktifkan izin Aksesibilitas agar bisa mengetik otomatis!", Toast.LENGTH_LONG).show()
            }

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

    private fun toggleScreen(toSettings: Boolean) {
        isSettingsOpen = toSettings
        if (toSettings) {
            viewHome.visibility = View.GONE
            viewSettings.visibility = View.VISIBLE
            tvHeaderTitle.text = "Pengaturan"
            tvHeaderSub.text = "KUSTOMISASI CHAT"
            btnHeaderAction.setImageResource(R.drawable.ic_arrow_back)
        } else {
            viewHome.visibility = View.VISIBLE
            viewSettings.visibility = View.GONE
            tvHeaderTitle.text = "AutoChat"
            tvHeaderSub.text = "TIKTOK ASSISTANT"
            btnHeaderAction.setImageResource(R.drawable.ic_tab_settings)
        }
    }

    override fun onBackPressed() {
        if (isSettingsOpen) {
            toggleScreen(false)
        } else {
            super.onBackPressed()
        }
    }

    private fun loadSavedSettings() {
        etSettingMsg1.setText(prefs.getString("msg1", "Halo kak, barangnya ready? 🔥"))
        etSettingMsg2.setText(prefs.getString("msg2", "Spill etalase nomor 1 dong kak 🛍️"))
        etSettingMsg3.setText(prefs.getString("msg3", "Tap tap layar terus ya guys! ✨"))
        etSettingDelay.setText(prefs.getInt("delay", 4).toString())
        switchAntiSpam.isChecked = prefs.getBoolean("anti_spam", true)
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

        if (hasOverlay && hasAccessibility) {
            tvStatusTitle.text = "Sistem Siap"
            tvStatusSub.text = "Semua izin terhubung"
        } else {
            tvStatusTitle.text = "Izin Kurang"
            tvStatusSub.text = "Aktifkan tanda merah"
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
