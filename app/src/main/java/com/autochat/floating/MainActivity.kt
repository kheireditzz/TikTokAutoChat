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
import android.view.LayoutInflater
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray

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
    private lateinit var containerMessageSettings: LinearLayout
    private lateinit var btnSettingsAddMessage: TextView
    private lateinit var etSettingDelay: EditText
    private lateinit var switchAntiSpam: Switch
    private lateinit var btnSaveSettings: Button
    private val settingEditTextList = mutableListOf<EditText>()

    // Coordinate views
    private lateinit var etCoordInputX: EditText
    private lateinit var etCoordInputY: EditText
    private lateinit var etCoordSendX: EditText
    private lateinit var etCoordSendY: EditText
    private lateinit var spinnerMainPreset: Spinner
    private lateinit var btnSaveMainPreset: Button
    private var presetList = mutableListOf<CoordPreset>()
    private var isSpinnerInit = false

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
        containerMessageSettings = findViewById(R.id.containerMessageSettings)
        btnSettingsAddMessage = findViewById(R.id.btnSettingsAddMessage)
        etSettingDelay = findViewById(R.id.etSettingDelay)
        switchAntiSpam = findViewById(R.id.switchAntiSpam)
        btnSaveSettings = findViewById(R.id.btnSaveSettings)

        etCoordInputX = findViewById(R.id.etCoordInputX)
        etCoordInputY = findViewById(R.id.etCoordInputY)
        etCoordSendX = findViewById(R.id.etCoordSendX)
        etCoordSendY = findViewById(R.id.etCoordSendY)
        spinnerMainPreset = findViewById(R.id.spinnerMainPreset)
        btnSaveMainPreset = findViewById(R.id.btnSaveMainPreset)

        setupPresetSpinner()

        btnSaveMainPreset.setOnClickListener {
            showSavePresetDialog()
        }

        btnSettingsAddMessage.setOnClickListener {
            addSettingMessageRow("")
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
            saveSettings()
            Toast.makeText(this, "Pengaturan & daftar pesan berhasil disimpan! ✅", Toast.LENGTH_SHORT).show()
        }

        btnLaunch.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Aktifkan Izin Overlay terlebih dahulu!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "Penting: Aktifkan izin Aksesibilitas agar bisa mengetik otomatis!", Toast.LENGTH_LONG).show()
            }

            // Simpan perubahan pesan saat ini sebelum membuka widget
            saveSettings()

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
            setText("✕")
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
        val anti = switchAntiSpam.isChecked

        val coordInputX = etCoordInputX.text.toString().toIntOrNull() ?: 13
        val coordInputY = etCoordInputY.text.toString().toIntOrNull() ?: 96
        val coordSendX = etCoordSendX.text.toString().toIntOrNull() ?: 85
        val coordSendY = etCoordSendY.text.toString().toIntOrNull() ?: 63

        prefs.edit()
            .putString("messages_json", messages.toString())
            .putInt("delay", d)
            .putBoolean("anti_spam", anti)
            .putInt("coord_input_x", coordInputX)
            .putInt("coord_input_y", coordInputY)
            .putInt("coord_send_x", coordSendX)
            .putInt("coord_send_y", coordSendY)
            .apply()
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
        switchAntiSpam.isChecked = prefs.getBoolean("anti_spam", true)

        etCoordInputX.setText(prefs.getInt("coord_input_x", 13).toString())
        etCoordInputY.setText(prefs.getInt("coord_input_y", 96).toString())
        etCoordSendX.setText(prefs.getInt("coord_send_x", 85).toString())
        etCoordSendY.setText(prefs.getInt("coord_send_y", 63).toString())
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

    private fun setupPresetSpinner() {
        presetList = PresetManager.getPresets(prefs)
        val names = presetList.map { it.name }.toMutableList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        spinnerMainPreset.adapter = adapter

        spinnerMainPreset.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isSpinnerInit) {
                    isSpinnerInit = true
                    return
                }
                if (position in 0 until presetList.size) {
                    val p = presetList[position]
                    etCoordInputX.setText(p.chatX.toString())
                    etCoordInputY.setText(p.chatY.toString())
                    etCoordSendX.setText(p.sendX.toString())
                    etCoordSendY.setText(p.sendY.toString())
                    Toast.makeText(this@MainActivity, "Preset '${p.name}' dipilih! Otomatis diterapkan.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun showSavePresetDialog() {
        val chatX = etCoordInputX.text.toString().toIntOrNull() ?: 13
        val chatY = etCoordInputY.text.toString().toIntOrNull() ?: 96
        val sendX = etCoordSendX.text.toString().toIntOrNull() ?: 85
        val sendY = etCoordSendY.text.toString().toIntOrNull() ?: 63

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_save_preset, null)
        val tvSummary = dialogView.findViewById<TextView>(R.id.tvPresetCoordsSummary)
        val etName = dialogView.findViewById<EditText>(R.id.etPresetName)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelPreset)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirmSavePreset)

        tvSummary.text = "Chat: X:$chatX% Y:$chatY% | Kirim: X:$sendX% Y:$sendY%"
        etName.setText("Preset ${presetList.size + 1}")

        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isBlank()) {
                Toast.makeText(this, "Nama preset tidak boleh kosong!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val newPreset = CoordPreset(name, chatX, chatY, sendX, sendY)
            PresetManager.addOrUpdatePreset(prefs, newPreset)
            dialog.dismiss()
            setupPresetSpinner()
            // Pilih item yang baru disimpan
            val newIndex = presetList.indexOfFirst { it.name.equals(name, ignoreCase = true) }
            if (newIndex >= 0) {
                spinnerMainPreset.setSelection(newIndex)
            }
            Toast.makeText(this, "Preset '$name' berhasil disimpan! ✅", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
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
