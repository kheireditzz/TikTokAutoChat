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
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import android.view.accessibility.AccessibilityManager
import android.widget.*
import org.json.JSONArray

class MainActivity : Activity() {

    private val REQUEST_OVERLAY_CODE = 2001
    private lateinit var prefs: SharedPreferences

    // Header & Navigation
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
    private lateinit var containerMessageSettings: LinearLayout
    private lateinit var btnSettingsAddMessage: TextView
    private lateinit var etSettingDelay: EditText
    private lateinit var etSettingMaxCount: EditText
    private lateinit var switchAntiSpam: Switch
    private lateinit var tvSettingsCoordStatus: TextView
    private lateinit var btnSettingsResetCoords: Button
    private lateinit var btnSaveSettings: Button
    private val settingEditTextList = mutableListOf<EditText>()

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

        // Init Home views
        tvStatusTitle = findViewById(R.id.tvStatusTitle)
        tvStatusSub = findViewById(R.id.tvStatusSub)
        switchOverlay = findViewById(R.id.switchOverlay)
        switchAccessibility = findViewById(R.id.switchAccessibility)
        btnLaunch = findViewById(R.id.btnLaunchFloating)

        // Init Settings views
        containerMessageSettings = findViewById(R.id.containerMessageSettings)
        btnSettingsAddMessage = findViewById(R.id.btnSettingsAddMessage)
        etSettingDelay = findViewById(R.id.etSettingDelay)
        etSettingMaxCount = findViewById(R.id.etSettingMaxCount)
        switchAntiSpam = findViewById(R.id.switchAntiSpam)
        tvSettingsCoordStatus = findViewById(R.id.tvSettingsCoordStatus)
        btnSettingsResetCoords = findViewById(R.id.btnSettingsResetCoords)
        btnSaveSettings = findViewById(R.id.btnSaveSettings)

        val btnSettingDelayMinus = findViewById<TextView>(R.id.btnSettingDelayMinus)
        val btnSettingDelayPlus = findViewById<TextView>(R.id.btnSettingDelayPlus)
        val btnSettingMaxMinus = findViewById<TextView>(R.id.btnSettingMaxMinus)
        val btnSettingMaxPlus = findViewById<TextView>(R.id.btnSettingMaxPlus)

        btnSettingDelayMinus.setOnClickListener {
            val current = etSettingDelay.text.toString().toIntOrNull() ?: 4
            val next = (current - 1).coerceAtLeast(1)
            etSettingDelay.setText(next.toString())
        }

        btnSettingDelayPlus.setOnClickListener {
            val current = etSettingDelay.text.toString().toIntOrNull() ?: 4
            val next = (current + 1).coerceAtMost(999)
            etSettingDelay.setText(next.toString())
        }

        btnSettingMaxMinus.setOnClickListener {
            val current = etSettingMaxCount.text.toString().toIntOrNull() ?: 0
            val next = if (current <= 5) (current - 1).coerceAtLeast(0) else (current - 5).coerceAtLeast(0)
            etSettingMaxCount.setText(next.toString())
        }

        btnSettingMaxPlus.setOnClickListener {
            val current = etSettingMaxCount.text.toString().toIntOrNull() ?: 0
            val next = if (current < 5) (current + 1) else (current + 5).coerceAtMost(99999)
            etSettingMaxCount.setText(next.toString())
        }

        btnSettingsAddMessage.setOnClickListener {
            addSettingMessageRow("")
        }

        // Toggle Disclaimer Card (Bisa disembunyikan/dibuka)
        val layoutDisclaimerBody = findViewById<View>(R.id.layoutDisclaimerBody)
        val btnToggleDisclaimer = findViewById<TextView>(R.id.btnToggleDisclaimer)
        val layoutDisclaimerHeader = findViewById<View>(R.id.layoutDisclaimerHeader)
        var isDisclaimerExpanded = false
        val toggleDisclaimerAction = View.OnClickListener {
            isDisclaimerExpanded = !isDisclaimerExpanded
            if (isDisclaimerExpanded) {
                layoutDisclaimerBody.visibility = View.VISIBLE
                btnToggleDisclaimer.text = "Tutup ▲"
            } else {
                layoutDisclaimerBody.visibility = View.GONE
                btnToggleDisclaimer.text = "Buka ▼"
            }
        }
        btnToggleDisclaimer?.setOnClickListener(toggleDisclaimerAction)
        layoutDisclaimerHeader?.setOnClickListener(toggleDisclaimerAction)

        // Cek dialog persetujuan melayang saat pertama kali masuk
        checkFirstLaunchAgreement()

        btnSettingsResetCoords.setOnClickListener {
            TikTokAccessibilityService.instance?.resetToDefaultCoordinates()
            prefs.edit()
                .remove("calibrated_chat_x")
                .remove("calibrated_chat_y")
                .remove("calibrated_send_x")
                .remove("calibrated_send_y")
                .apply()
            updateCoordStatusText()
            Toast.makeText(this, "Koordinat kembali ke default", Toast.LENGTH_SHORT).show()
        }

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
                    Toast.makeText(this, "Izin overlay sudah aktif", Toast.LENGTH_SHORT).show()
                }
            }
        }
        findViewById<View>(R.id.cardOverlay).setOnClickListener(overlayAction)
        switchOverlay.setOnClickListener(overlayAction)

        val accessibilityAction = View.OnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Aktifkan AutoChat di Aksesibilitas", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.cardAccessibility).setOnClickListener(accessibilityAction)
        switchAccessibility.setOnClickListener(accessibilityAction)

        btnSaveSettings.setOnClickListener {
            saveSettings()
            Toast.makeText(this, "Pengaturan berhasil disimpan", Toast.LENGTH_SHORT).show()
        }

        btnLaunch.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Aktifkan izin overlay terlebih dahulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "Aktifkan izin aksesibilitas", Toast.LENGTH_SHORT).show()
            }

            // Simpan perubahan sebelum membuka widget
            saveSettings()

            val serviceIntent = Intent(this, FloatingWidgetService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Toast.makeText(this, "Widget melayang aktif", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun addSettingMessageRow(initialText: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 6, 0, 6)
        }

        val et = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, (42 * resources.displayMetrics.density).toInt(), 1f)
            setText(initialText)
            hint = "Tulis template komentar..."
            setTextColor(Color.parseColor("#090A0F"))
            setHintTextColor(Color.parseColor("#94A3B8"))
            textSize = 12.5f
            setBackgroundResource(R.drawable.bg_input_white)
            val pad = (12 * resources.displayMetrics.density).toInt()
            setPadding(pad, 0, pad, 0)
            isSingleLine = true
        }

        val btnDel = TextView(this).apply {
            val size = (32 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginStart = (8 * resources.displayMetrics.density).toInt()
            }
            gravity = android.view.Gravity.CENTER
            text = "✕"
            setTextColor(Color.parseColor("#EF4444"))
            textSize = 12f
            setBackgroundResource(R.drawable.bg_circle_btn_white)
            setOnClickListener {
                containerMessageSettings.removeView(row)
                settingEditTextList.remove(et)
            }
        }

        row.addView(et)
        row.addView(btnDel)
        containerMessageSettings.addView(row)
        settingEditTextList.add(et)
    }

    private fun saveSettings() {
        val messages = JSONArray()
        for (et in settingEditTextList) {
            val t = et.text.toString().trim()
            if (t.isNotBlank()) {
                messages.put(t)
            }
        }
        if (messages.length() == 0) {
            messages.put("Halo kak, barangnya ready? 🔥")
        }

        val d = etSettingDelay.text.toString().toIntOrNull() ?: 4
        val maxTarget = etSettingMaxCount.text.toString().toIntOrNull() ?: 0
        val anti = switchAntiSpam.isChecked

        prefs.edit()
            .putString("messages_json", messages.toString())
            .putInt("delay", d)
            .putInt("max_count", maxTarget)
            .putBoolean("anti_spam", anti)
            .apply()
    }

    private fun updateCoordStatusText() {
        val chatX = prefs.getFloat("calibrated_chat_x", -1f)
        val chatY = prefs.getFloat("calibrated_chat_y", -1f)
        val sendX = prefs.getFloat("calibrated_send_x", -1f)
        val sendY = prefs.getFloat("calibrated_send_y", -1f)

        if (chatX > 0 && sendX > 0) {
            tvSettingsCoordStatus.text = "Status: Kalibrasi Aktif (${chatX.toInt()}, ${chatY.toInt()}) & (${sendX.toInt()}, ${sendY.toInt()})"
            tvSettingsCoordStatus.setTextColor(Color.parseColor("#10B981"))
        } else {
            tvSettingsCoordStatus.text = "Status: Default TikTok Live (Auto Deteksi)"
            tvSettingsCoordStatus.setTextColor(Color.parseColor("#090A0F"))
        }
    }

    private fun loadSavedSettings() {
        containerMessageSettings.removeAllViews()
        settingEditTextList.clear()

        val jsonStr = prefs.getString("messages_json", null)
        val list = mutableListOf<String>()

        if (!jsonStr.isNullOrBlank()) {
            try {
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    list.add(arr.getString(i))
                }
            } catch (_: Exception) {}
        }

        if (list.isEmpty()) {
            list.add(prefs.getString("msg1", "Halo kak, barangnya ready? 🔥") ?: "")
            list.add(prefs.getString("msg2", "Spill etalase nomor 1 dong kak 🛍️") ?: "")
            list.add(prefs.getString("msg3", "Tap tap layar terus ya guys! ✨") ?: "")
        }

        for (item in list) {
            if (item.isNotBlank()) {
                addSettingMessageRow(item)
            }
        }

        if (settingEditTextList.isEmpty()) {
            addSettingMessageRow("Halo kak, barangnya ready? 🔥")
        }

        etSettingDelay.setText(prefs.getInt("delay", 4).toString())
        etSettingMaxCount.setText(prefs.getInt("max_count", 0).toString())
        switchAntiSpam.isChecked = prefs.getBoolean("anti_spam", true)
        updateCoordStatusText()
    }

    private fun toggleScreen(toSettings: Boolean) {
        isSettingsOpen = toSettings
        if (toSettings) {
            viewHome.visibility = View.GONE
            viewSettings.visibility = View.VISIBLE
            tvHeaderTitle.text = "Pengaturan"
            tvHeaderSub.text = "KUSTOMISASI CHAT"
            btnHeaderAction.setImageResource(R.drawable.ic_arrow_back)
            updateCoordStatusText()
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

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
        loadSavedSettings()
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

    private fun checkFirstLaunchAgreement() {
        val hasAgreed = prefs.getBoolean("has_agreed_disclaimer", false)
        if (hasAgreed) return

        val dialog = Dialog(this, android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar_MinWidth)
        dialog.setContentView(R.layout.dialog_disclaimer_agreement)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)

        val etConfirm = dialog.findViewById<EditText>(R.id.etAgreementConfirm)
        val cbQuickAgree = dialog.findViewById<CheckBox>(R.id.cbQuickAgree)
        val btnDecline = dialog.findViewById<Button>(R.id.btnAgreementDecline)
        val btnAccept = dialog.findViewById<Button>(R.id.btnAgreementAccept)
        val dialogRoot = dialog.findViewById<View>(R.id.dialogAgreementRoot)

        cbQuickAgree.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                etConfirm.setText("SETUJU")
            }
        }

        btnDecline.setOnClickListener {
            Toast.makeText(this, "Persetujuan ditolak. Aplikasi ditutup.", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            finishAffinity()
        }

        btnAccept.setOnClickListener {
            val input = etConfirm.text.toString().trim()
            if (!input.equals("SETUJU", ignoreCase = true) && !cbQuickAgree.isChecked) {
                Toast.makeText(this, "Ketik SETUJU atau centang kotak persetujuan", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Screenshot otomatis bukti persetujuan user
            captureAgreementProof(dialogRoot)

            // Simpan status agar hanya muncul sekali saja
            prefs.edit().putBoolean("has_agreed_disclaimer", true).apply()
            dialog.dismiss()
            Toast.makeText(this, "Persetujuan diterima", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun captureAgreementProof(view: View) {
        try {
            view.post {
                try {
                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    view.draw(canvas)

                    val picturesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "AutoChatProof")
                    if (!picturesDir.exists()) picturesDir.mkdirs()

                    val file = File(picturesDir, "Bukti_Persetujuan_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }

                    val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                    mediaScanIntent.data = Uri.fromFile(file)
                    sendBroadcast(mediaScanIntent)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }
}
