package com.autochat.floating

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.*
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

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

    // License Card Views
    private lateinit var cardLicenseStatus: LinearLayout
    private lateinit var tvLicenseIcon: TextView
    private lateinit var tvLicenseTitle: TextView
    private lateinit var tvLicenseSubtitle: TextView
    private lateinit var badgeLicenseStatus: TextView
    private lateinit var tvLicenseTimeRemaining: TextView
    private lateinit var tvLicenseDeviceBoundInfo: TextView
    private lateinit var btnClaimTrialAction: Button
    private lateinit var btnBuyLifetimeAction: Button
    private lateinit var tvLicenseDeviceWarning: TextView

    // Offline Overlay Views
    private lateinit var layoutOfflineOverlay: View
    private lateinit var btnOpenNetworkSettings: Button

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

    // Countdown & Polling Handlers
    private val mainHandler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null
    private val bgExecutor = Executors.newCachedThreadPool()

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

        // Init License Card views
        cardLicenseStatus = findViewById(R.id.cardLicenseStatus)
        tvLicenseIcon = findViewById(R.id.tvLicenseIcon)
        tvLicenseTitle = findViewById(R.id.tvLicenseTitle)
        tvLicenseSubtitle = findViewById(R.id.tvLicenseSubtitle)
        badgeLicenseStatus = findViewById(R.id.badgeLicenseStatus)
        tvLicenseTimeRemaining = findViewById(R.id.tvLicenseTimeRemaining)
        tvLicenseDeviceBoundInfo = findViewById(R.id.tvLicenseDeviceBoundInfo)
        btnClaimTrialAction = findViewById(R.id.btnClaimTrialAction)
        btnBuyLifetimeAction = findViewById(R.id.btnBuyLifetimeAction)
        tvLicenseDeviceWarning = findViewById(R.id.tvLicenseDeviceWarning)

        btnClaimTrialAction.setOnClickListener {
            LicenseManager.getInstance(this).activateTrial { success, msg ->
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                updateLicenseUI()
            }
        }

        btnBuyLifetimeAction.setOnClickListener {
            showDongtubeQrisDialog()
        }

        if (intent.getBooleanExtra("OPEN_QRIS_DIALOG", false)) {
            showDongtubeQrisDialog()
        }

        // Init Offline Overlay
        layoutOfflineOverlay = findViewById(R.id.layoutOfflineOverlay)
        btnOpenNetworkSettings = findViewById(R.id.btnOpenNetworkSettings)

        btnOpenNetworkSettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            } catch (_: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
        }

        // Realtime Network Detection (Auto-detect & Anti-Lag)
        NetworkMonitor.getInstance(this).addListener { isOnline ->
            if (isOnline) {
                if (layoutOfflineOverlay.visibility == View.VISIBLE) {
                    layoutOfflineOverlay.animate()
                        .alpha(0f)
                        .setDuration(300)
                        .withEndAction {
                            layoutOfflineOverlay.visibility = View.GONE
                            layoutOfflineOverlay.alpha = 1f
                        }
                    Toast.makeText(this, "Internet terhubung kembali 🌐", Toast.LENGTH_SHORT).show()
                    LicenseManager.getInstance(this).syncNetworkTimeAndIp {
                        updateLicenseUI()
                    }
                }
            } else {
                layoutOfflineOverlay.alpha = 0f
                layoutOfflineOverlay.visibility = View.VISIBLE
                layoutOfflineOverlay.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .start()
            }
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
            // Verifikasi Koneksi Internet Wajib Online
            if (!NetworkMonitor.getInstance(this).isOnline()) {
                Toast.makeText(this, "Harap hidupkan data internet atau WiFi terlebih dahulu", Toast.LENGTH_SHORT).show()
                layoutOfflineOverlay.visibility = View.VISIBLE
                return@setOnClickListener
            }

            // Verifikasi Masa Trial & Lisensi
            val licState = LicenseManager.getInstance(this).getLicenseState()
            if (licState.isTrialExpired && !licState.isLifetime) {
                Toast.makeText(this, "Masa trial 3 hari telah berakhir. Beli lisensi seumur hidup (Rp 10.000)", Toast.LENGTH_LONG).show()
                showDongtubeQrisDialog()
                return@setOnClickListener
            }

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

    private fun updateLicenseUI() {
        val manager = LicenseManager.getInstance(this)
        val state = manager.getLicenseState()

        val devShort = state.deviceId
        val ipText = if (state.registeredIp.isNotBlank()) " · IP: ${state.registeredIp}" else ""
        tvLicenseDeviceBoundInfo.text = "Device ID: $devShort$ipText"

        if (state.isLifetime) {
            tvLicenseIcon.text = "👑"
            tvLicenseTitle.text = "Lisensi Seumur Hidup"
            tvLicenseSubtitle.text = "Akses Penuh Tanpa Batas Waktu"
            badgeLicenseStatus.text = "SEUMUR HIDUP"
            badgeLicenseStatus.setTextColor(Color.parseColor("#10B981"))
            tvLicenseTimeRemaining.text = "Status: Aktif Selamanya (Lunas)"
            tvLicenseTimeRemaining.setTextColor(Color.parseColor("#10B981"))
            btnClaimTrialAction.visibility = View.GONE
            btnBuyLifetimeAction.visibility = View.GONE
            tvLicenseDeviceWarning.text = "✓ Lisensi aktif dan terkunci permanen pada perangkat ini."
        } else if (state.isTrialActive) {
            tvLicenseIcon.text = "⏳"
            tvLicenseTitle.text = "Masa Uji Coba Gratis"
            tvLicenseSubtitle.text = "Masa Trial 3 Hari (72 Jam)"
            badgeLicenseStatus.text = "TRIAL AKTIF"
            badgeLicenseStatus.setTextColor(Color.parseColor("#059669"))

            val rem = state.remainingMillis
            val days = rem / (24L * 60L * 60L * 1000L)
            val hours = (rem % (24L * 60L * 60L * 1000L)) / (60L * 60L * 1000L)
            val mins = (rem % (60L * 60L * 1000L)) / (60L * 1000L)
            val secs = (rem % (60L * 1000L)) / 1000L

            tvLicenseTimeRemaining.text = "Sisa Waktu: ${days} Hari ${hours} Jam ${mins} Mnt ${secs} Dtk"
            tvLicenseTimeRemaining.setTextColor(Color.parseColor("#0F172A"))

            btnClaimTrialAction.visibility = View.GONE
            btnBuyLifetimeAction.visibility = View.VISIBLE
            btnBuyLifetimeAction.text = "👑  Beli Lisensi Seumur Hidup — Rp 10.000"
            tvLicenseDeviceWarning.text = "*Peringatan: Lisensi dikunci permanen pada Device ID ini. Pembelian di HP ini hanya berlaku untuk HP ini."
        } else if (state.isTrialExpired) {
            tvLicenseIcon.text = "🔒"
            tvLicenseTitle.text = "Masa Uji Coba Habis"
            tvLicenseSubtitle.text = "Akses AutoChat Terkunci"
            badgeLicenseStatus.text = "TRIAL HABIS"
            badgeLicenseStatus.setTextColor(Color.parseColor("#EF4444"))

            tvLicenseTimeRemaining.text = "Trial 3 Hari Selesai. Silakan Beli Lisensi"
            tvLicenseTimeRemaining.setTextColor(Color.parseColor("#EF4444"))

            btnClaimTrialAction.visibility = View.GONE
            btnBuyLifetimeAction.visibility = View.VISIBLE
            btnBuyLifetimeAction.text = "👑  Beli Lisensi Seumur Hidup — Rp 10.000"
            tvLicenseDeviceWarning.text = "*Peringatan: Lisensi dikunci permanen pada Device ID ini. Pembelian di HP ini hanya berlaku untuk HP ini."
        } else {
            // Belum klaim trial (Unused)
            tvLicenseIcon.text = "🎁"
            tvLicenseTitle.text = "Masa Percobaan 3 Hari"
            tvLicenseSubtitle.text = "Coba Gratis 72 Jam Tanpa Bayar"
            badgeLicenseStatus.text = "GRATIS 3 HARI"
            badgeLicenseStatus.setTextColor(Color.parseColor("#3B82F6"))

            tvLicenseTimeRemaining.text = "Tersedia 3 Hari (72 Jam) Gratis"
            tvLicenseTimeRemaining.setTextColor(Color.parseColor("#0F172A"))

            btnClaimTrialAction.visibility = View.VISIBLE
            btnBuyLifetimeAction.visibility = View.VISIBLE
            btnBuyLifetimeAction.text = "👑  Beli Lisensi Seumur Hidup — Rp 10.000"
            tvLicenseDeviceWarning.text = "*Peringatan: Lisensi dikunci permanen pada Device ID ini. Pembelian di HP ini hanya berlaku untuk HP ini."
        }
    }

    private fun startCountdownTimer() {
        stopCountdownTimer()
        countdownRunnable = object : Runnable {
            override fun run() {
                updateLicenseUI()
                mainHandler.postDelayed(this, 1000L)
            }
        }
        mainHandler.post(countdownRunnable!!)
    }

    private fun stopCountdownTimer() {
        countdownRunnable?.let { mainHandler.removeCallbacks(it) }
        countdownRunnable = null
    }

    // =========================================================================
    // DIALOG PEMBAYARAN QRIS DONGTUBE
    // =========================================================================

    private fun showDongtubeQrisDialog() {
        val dialog = Dialog(this, android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar_MinWidth)
        dialog.setContentView(R.layout.dialog_dongtube_qris)
        dialog.setCancelable(true)

        val btnClose = dialog.findViewById<TextView>(R.id.btnQrisClose)
        val tvDeviceDisclaimer = dialog.findViewById<TextView>(R.id.tvQrisDeviceDisclaimer)
        val layoutLoading = dialog.findViewById<View>(R.id.layoutQrisLoading)
        val tvLoadingStatus = dialog.findViewById<TextView>(R.id.tvQrisLoadingStatus)
        val layoutContent = dialog.findViewById<View>(R.id.layoutQrisContent)
        val layoutSuccess = dialog.findViewById<View>(R.id.layoutQrisSuccess)

        val ivQris = dialog.findViewById<ImageView>(R.id.ivQrisCode)
        val tvTotalAmount = dialog.findViewById<TextView>(R.id.tvQrisTotalAmount)
        val tvInvoiceNote = dialog.findViewById<TextView>(R.id.tvQrisInvoiceNote)
        val btnCopy = dialog.findViewById<TextView>(R.id.btnCopyAmount)
        val tvStatusLive = dialog.findViewById<TextView>(R.id.tvQrisStatusLive)
        val tvExpiry = dialog.findViewById<TextView>(R.id.tvQrisExpiryCountdown)
        val btnCheckManual = dialog.findViewById<Button>(R.id.btnCheckPaymentManual)
        val btnDone = dialog.findViewById<Button>(R.id.btnSuccessDone)

        val licenseMgr = LicenseManager.getInstance(this)
        tvDeviceDisclaimer.text = "Lisensi Seumur Hidup (Rp 10.000) ini dikunci khusus untuk Device ID: ${licenseMgr.deviceId}. Pembelian pada perangkat ini hanya dapat digunakan di perangkat ini dan tidak bisa dipindahkan."

        var pollingRunnable: Runnable? = null
        var isDialogActive = true
        var currentInvoiceId: String? = null
        var exactTotalAmount = 10000

        fun stopPolling() {
            pollingRunnable?.let { mainHandler.removeCallbacks(it) }
            pollingRunnable = null
        }

        dialog.setOnDismissListener {
            isDialogActive = false
            stopPolling()
            updateLicenseUI()
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        btnDone.setOnClickListener {
            dialog.dismiss()
            updateLicenseUI()
        }

        btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Total Bayar", exactTotalAmount.toString())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Nominal Rp $exactTotalAmount disalin", Toast.LENGTH_SHORT).show()
        }

        // Panggil Dongtube Payment API untuk buat invoice
        tvLoadingStatus.text = "Menghubungkan ke Dongtube Payment..."
        layoutLoading.visibility = View.VISIBLE
        layoutContent.visibility = View.GONE
        layoutSuccess.visibility = View.GONE

        licenseMgr.createDongtubeInvoice(10000) { invoice, preloadedBitmap, errorMsg ->
            if (!isDialogActive) return@createDongtubeInvoice

            if (invoice != null) {
                currentInvoiceId = invoice.invoiceId
                exactTotalAmount = invoice.total

                val formatRupiah = NumberFormat.getNumberInstance(Locale("in", "ID"))
                tvTotalAmount.text = "Rp ${formatRupiah.format(invoice.total)}"
                tvInvoiceNote.text = "*Transfer SESUAI nominal Rp ${formatRupiah.format(invoice.total)} termasuk kode unik/fee"

                if (preloadedBitmap != null) {
                    // Instan tanpa delay!
                    ivQris.setImageBitmap(preloadedBitmap)
                    layoutLoading.visibility = View.GONE
                    layoutContent.visibility = View.VISIBLE
                } else {
                    // Fallback jika belum selesai di-decode
                    bgExecutor.execute {
                        try {
                            val imgUrl = URL(invoice.qrisImageUrl)
                            val conn = imgUrl.openConnection()
                            conn.connectTimeout = 6000
                            conn.readTimeout = 6000
                            val bitmap = BitmapFactory.decodeStream(conn.getInputStream())
                            mainHandler.post {
                                if (isDialogActive && bitmap != null) {
                                    ivQris.setImageBitmap(bitmap)
                                    layoutLoading.visibility = View.GONE
                                    layoutContent.visibility = View.VISIBLE
                                }
                            }
                        } catch (e: Exception) {
                            mainHandler.post {
                                if (isDialogActive) {
                                    tvLoadingStatus.text = "Gagal memuat QRIS: ${e.localizedMessage}"
                                }
                            }
                        }
                    }
                }

                // Handler Polling Status Pembayaran Otomatis
                pollingRunnable = object : Runnable {
                    override fun run() {
                        if (!isDialogActive || currentInvoiceId == null) return

                        licenseMgr.checkDongtubeInvoiceStatus(currentInvoiceId!!) { isPaid, status, _ ->
                            if (!isDialogActive) return@checkDongtubeInvoiceStatus

                            if (isPaid) {
                                stopPolling()
                                layoutContent.visibility = View.GONE
                                layoutSuccess.visibility = View.VISIBLE
                                updateLicenseUI()
                                Toast.makeText(this@MainActivity, "Pembayaran Berhasil Dikonfirmasi!", Toast.LENGTH_LONG).show()
                            } else {
                                tvStatusLive.text = "Menunggu transfer... (${status})"
                                mainHandler.postDelayed(this, 3500L)
                            }
                        }
                    }
                }
                mainHandler.postDelayed(pollingRunnable!!, 3500L)

            } else {
                tvLoadingStatus.text = errorMsg ?: "Gagal membuat tagihan QRIS"
                Toast.makeText(this, errorMsg ?: "Gagal membuat invoice", Toast.LENGTH_LONG).show()
            }
        }

        btnCheckManual.setOnClickListener {
            val invId = currentInvoiceId
            if (invId == null) {
                Toast.makeText(this, "Tagihan belum siap", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnCheckManual.isEnabled = false
            btnCheckManual.text = "Memeriksa Status..."

            licenseMgr.checkDongtubeInvoiceStatus(invId) { isPaid, status, err ->
                btnCheckManual.isEnabled = true
                btnCheckManual.text = "🔄  Cek Status Pembayaran"

                if (isPaid) {
                    stopPolling()
                    layoutContent.visibility = View.GONE
                    layoutSuccess.visibility = View.VISIBLE
                    updateLicenseUI()
                    Toast.makeText(this, "Pembayaran Berhasil! Lisensi Aktif", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Status: $status (Belum lunas)", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
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
        updateLicenseUI()
        startCountdownTimer()
        // Prefetch QRIS lebih awal di background agar saat tombol Beli ditekan, gambar langsung muncul instan!
        LicenseManager.getInstance(this).prefetchDongtubeInvoice(10000)
    }

    override fun onPause() {
        super.onPause()
        stopCountdownTimer()
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
                    val w = view.width.coerceAtLeast(1)
                    val h = view.height.coerceAtLeast(1)
                    val bannerH = (52 * resources.displayMetrics.density).toInt()
                    val totalH = h + bannerH

                    val fullBitmap = Bitmap.createBitmap(w, totalH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(fullBitmap)
                    canvas.drawColor(Color.parseColor("#F8FAFC"))

                    // Gambar isi dialog
                    view.draw(canvas)

                    // Gambar Watermark Stamp Verifikasi di Bagian Bawah
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    val rect = RectF(0f, h.toFloat(), w.toFloat(), totalH.toFloat())
                    paint.color = Color.parseColor("#0F172A")
                    canvas.drawRect(rect, paint)

                    paint.color = Color.parseColor("#10B981")
                    paint.textSize = 12f * resources.displayMetrics.density
                    paint.isFakeBoldText = true
                    canvas.drawText("✓ SAH & DISETUJUI PENGGUNA", 20f * resources.displayMetrics.density, h + (22f * resources.displayMetrics.density), paint)

                    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm:ss 'WIB'", Locale("in", "ID"))
                    val timeStr = sdf.format(Date())
                    val devId = LicenseManager.getInstance(this).deviceId

                    paint.color = Color.parseColor("#94A3B8")
                    paint.textSize = 9.5f * resources.displayMetrics.density
                    paint.isFakeBoldText = false
                    canvas.drawText("$timeStr · ID: $devId", 20f * resources.displayMetrics.density, h + (40f * resources.displayMetrics.density), paint)

                    // Eksekusi penyimpanan ke galeri di background thread agar anti-lag (0 lag)
                    bgExecutor.execute {
                        try {
                            val fileName = "Bukti_Persetujuan_${System.currentTimeMillis()}.jpg"
                            var savedUri: Uri? = null

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val values = ContentValues().apply {
                                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AutoChatProof")
                                    put(MediaStore.Images.Media.IS_PENDING, 1)
                                }
                                savedUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                                if (savedUri != null) {
                                    contentResolver.openOutputStream(savedUri)?.use { out ->
                                        fullBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                                    }
                                    values.clear()
                                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                                    contentResolver.update(savedUri, values, null, null)
                                }
                            } else {
                                val picturesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "AutoChatProof")
                                if (!picturesDir.exists()) picturesDir.mkdirs()
                                val file = File(picturesDir, fileName)
                                FileOutputStream(file).use { out ->
                                    fullBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                                }
                                val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                                mediaScanIntent.data = Uri.fromFile(file)
                                sendBroadcast(mediaScanIntent)
                            }

                            mainHandler.post {
                                Toast.makeText(this@MainActivity, "📸 Bukti persetujuan tersimpan otomatis di Galeri Foto (Album AutoChatProof)", Toast.LENGTH_LONG).show()
                            }
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }
}
