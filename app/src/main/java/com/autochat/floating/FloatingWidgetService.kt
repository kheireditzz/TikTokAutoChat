package com.autochat.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.*
import android.widget.*
import kotlin.math.abs

class FloatingWidgetService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var prefs: SharedPreferences

    private var isRunning = false
    private var isMinimized = false

    private val CHANNEL_ID = "AutoChat_Floating_Channel"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        prefs = getSharedPreferences("AutoChatPrefs", Context.MODE_PRIVATE)
        startForegroundNotification()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 200
        }

        windowManager.addView(floatingView, params)
        setupInteractions()
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AutoChat Floating Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)

            val notification: Notification = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("TikTok AutoChat Aktif")
                .setContentText("Widget melayang siap mengontrol obrolan.")
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .build()

            startForeground(1001, notification)
        }
    }

    private fun setupInteractions() {
        val layoutExpanded = floatingView.findViewById<LinearLayout>(R.id.layoutExpanded)
        val layoutBubble = floatingView.findViewById<LinearLayout>(R.id.layoutBubble)
        val header = floatingView.findViewById<LinearLayout>(R.id.layoutHeader)

        val btnMinimize = floatingView.findViewById<TextView>(R.id.btnMinimize)
        val btnClose = floatingView.findViewById<TextView>(R.id.btnCloseFloating)
        val btnToggle = floatingView.findViewById<Button>(R.id.btnToggleStart)
        val etDelay = floatingView.findViewById<EditText>(R.id.etDelayInput)
        val statusIndicator = floatingView.findViewById<View>(R.id.statusIndicator)
        val bubbleStatusDot = floatingView.findViewById<View>(R.id.bubbleStatusDot)
        val tvStatusText = floatingView.findViewById<TextView>(R.id.tvStatusText)
        val tvBubbleLabel = floatingView.findViewById<TextView>(R.id.tvBubbleLabel)
        val tvBubbleSentCount = floatingView.findViewById<TextView>(R.id.tvBubbleSentCount)
        val tvActiveCount = floatingView.findViewById<TextView>(R.id.tvActiveCount)
        val tvSentCountStatus = floatingView.findViewById<TextView>(R.id.tvSentCountStatus)
        val containerMessages = floatingView.findViewById<LinearLayout>(R.id.containerFloatingMessages)
        val btnAddMessage = floatingView.findViewById<TextView>(R.id.btnFloatingAddMessage)

        var totalSentCount = 0

        // Pasang listener status kirim chat valid
        TikTokAccessibilityService.instance?.setOnChatSentListener { count, _ ->
            totalSentCount = count
            tvSentCountStatus.setText("$count Terkirim")
            tvBubbleSentCount.setText("$count kirim")
            tvStatusText.setText("Terkirim $count pesan ✓")
        }

        val widgetMessageItems = mutableListOf<Pair<CheckBox, EditText>>()

        fun updateCountText() {
            var count = 0
            for ((cb, et) in widgetMessageItems) {
                if (cb.isChecked && et.text.isNotBlank()) count++
            }
            tvActiveCount.text = "$count Aktif"
        }

        fun addWidgetMessageItem(text: String, isChecked: Boolean = true) {
            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(2, 2, 2, 2)
            }

            val cb = CheckBox(this).apply {
                layoutParams = LinearLayout.LayoutParams((24 * resources.displayMetrics.density).toInt(), (24 * resources.displayMetrics.density).toInt())
                this.isChecked = isChecked
                buttonTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FE2C55"))
                setOnCheckedChangeListener { _, _ -> updateCountText() }
            }

            val et = EditText(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, (30 * resources.displayMetrics.density).toInt(), 1f)
                setText(text)
                hint = "Ketik komentar..."
                setTextColor(Color.parseColor("#0F172A"))
                textSize = 11.5f
                background = null
                val pad = (4 * resources.displayMetrics.density).toInt()
                setPadding(pad, 0, pad, 0)
                isSingleLine = true

                setOnTouchListener { _, _ ->
                    params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                    try {
                        windowManager.updateViewLayout(floatingView, params)
                    } catch (_: Exception) {}
                    false
                }
            }

            val btnDel = TextView(this).apply {
                val s = (24 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(s, s)
                gravity = Gravity.CENTER
                setText("✕")
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 11f
                setOnClickListener {
                    containerMessages.removeView(itemLayout)
                    widgetMessageItems.removeAll { it.second == et }
                    updateCountText()
                }
            }

            itemLayout.addView(cb)
            itemLayout.addView(et)
            itemLayout.addView(btnDel)

            if (containerMessages.childCount > 0) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                        val m = (4 * resources.displayMetrics.density).toInt()
                        setMargins(m, 0, m, 0)
                    }
                    setBackgroundColor(Color.parseColor("#E2E8F0"))
                }
                containerMessages.addView(divider)
            }

            containerMessages.addView(itemLayout)
            widgetMessageItems.add(Pair(cb, et))
            updateCountText()
        }

        // Load pesan tersimpan dari SharedPreferences
        val jsonStr = prefs.getString("messages_json", null)
        val loadedList = mutableListOf<String>()
        if (!jsonStr.isNullOrBlank()) {
            try {
                val arr = org.json.JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val s = arr.getString(i)
                    if (s.isNotBlank()) loadedList.add(s)
                }
            } catch (_: Exception) {}
        }

        if (loadedList.isEmpty()) {
            loadedList.add(prefs.getString("msg1", "Halo kak, barangnya ready? 🔥") ?: "")
            loadedList.add(prefs.getString("msg2", "Spill etalase nomor 1 dong kak 🛍️") ?: "")
            loadedList.add(prefs.getString("msg3", "Tap tap layar terus ya guys! ✨") ?: "")
        }

        containerMessages.removeAllViews()
        for (msg in loadedList) {
            if (msg.isNotBlank()) {
                addWidgetMessageItem(msg, true)
            }
        }

        btnAddMessage.setOnClickListener {
            addWidgetMessageItem("", true)
        }

        val btnCalibrate = floatingView.findViewById<TextView>(R.id.btnCalibrate)
        btnCalibrate.setOnClickListener {
            startOneTouchCalibration()
        }

        etDelay.setText(prefs.getInt("delay", 4).toString())

        fun setMinimizeState(minimized: Boolean) {
            isMinimized = minimized
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            val screenWidth = displayMetrics.widthPixels

            if (minimized) {
                layoutExpanded.visibility = View.GONE
                layoutBubble.visibility = View.VISIBLE
                snapToNearestEdge()
            } else {
                layoutBubble.visibility = View.GONE
                layoutExpanded.visibility = View.VISIBLE
                // Pastikan saat diperbesar, widget tidak terpotong di tepi kanan layar
                val expandedWidth = (270 * resources.displayMetrics.density).toInt()
                if (params.x + expandedWidth > screenWidth) {
                    params.x = (screenWidth - expandedWidth - 16).coerceAtLeast(16)
                }
            }
            try {
                windowManager.updateViewLayout(floatingView, params)
            } catch (_: Exception) {}
        }

        btnMinimize.setOnClickListener {
            setMinimizeState(true)
        }

        layoutBubble.setOnClickListener {
            setMinimizeState(false)
        }

        // Drag Handler dengan Auto Snap ke Samping Layar & Deteksi Klik
        val dragTouchListener = object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isClick = false

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isClick = true
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        if (abs(dx) > 10 || abs(dy) > 10) {
                            isClick = false
                        }
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        try {
                            windowManager.updateViewLayout(floatingView, params)
                        } catch (_: Exception) {}
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            if (v == layoutBubble || isMinimized) {
                                setMinimizeState(false)
                            } else {
                                v?.performClick()
                            }
                        } else {
                            if (isMinimized) {
                                snapToNearestEdge()
                            }
                        }
                        return true
                    }
                }
                return false
            }
        }

        header.setOnTouchListener(dragTouchListener)
        layoutBubble.setOnTouchListener(dragTouchListener)

        etDelay.setOnTouchListener { _, _ ->
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            try {
                windowManager.updateViewLayout(floatingView, params)
            } catch (_: Exception) {}
            false
        }

        btnClose.setOnClickListener {
            TikTokAccessibilityService.instance?.stopAutoChat()
            stopSelf()
        }

        btnToggle.setOnClickListener {
            if (!isRunning) {
                val service = TikTokAccessibilityService.instance
                if (service == null) {
                    Toast.makeText(this, "Aksesibilitas belum aktif! Aktifkan 'AutoChat' di pengaturan.", Toast.LENGTH_LONG).show()
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                    return@setOnClickListener
                }

                val activeList = arrayListOf<String>()
                val allListJson = org.json.JSONArray()
                for ((cb, et) in widgetMessageItems) {
                    val t = et.text.toString().trim()
                    if (t.isNotBlank()) {
                        allListJson.put(t)
                        if (cb.isChecked) {
                            activeList.add(t)
                        }
                    }
                }

                if (activeList.isEmpty()) {
                    Toast.makeText(this, "Centang minimal 1 pesan!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val delay = etDelay.text.toString().toLongOrNull() ?: 4L
                val antiSpam = prefs.getBoolean("anti_spam", true)

                // Simpan perubahan pesan saat ini ke SharedPreferences secara otomatis
                prefs.edit()
                    .putString("messages_json", allListJson.toString())
                    .putInt("delay", delay.toInt())
                    .apply()

                // Lepas fokus keyboard agar TikTok tidak terhalangi
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                windowManager.updateViewLayout(floatingView, params)

                // Pasang listener status kirim chat valid
                service.setOnChatSentListener { count, _ ->
                    totalSentCount = count
                    tvSentCountStatus.setText("$count Terkirim")
                    tvBubbleSentCount.setText("$count kirim")
                    tvStatusText.setText("Terkirim $count pesan ✓")
                }

                // Panggil service AutoChat cerdas (Smart Keyboard Flow)
                service.startAutoChat(activeList, delay, antiSpam)

                totalSentCount = 0
                tvSentCountStatus.setText("0 Terkirim")
                tvBubbleSentCount.setText("0 kirim")

                isRunning = true
                btnToggle.text = "STOP"
                btnToggle.setBackgroundColor(Color.parseColor("#EF4444"))
                statusIndicator.setBackgroundColor(Color.parseColor("#10B981"))
                bubbleStatusDot.setBackgroundColor(Color.parseColor("#10B981"))
                tvStatusText.text = "Berjalan (Tiap ${delay}s)"
                tvStatusText.setTextColor(Color.parseColor("#10B981"))
                tvBubbleLabel.text = "RUNNING"
                Toast.makeText(this, "Auto Chat Mulai Berjalan!", Toast.LENGTH_SHORT).show()

                // Otomatis minimize ke bubble samping agar tidak menghalangi live
                setMinimizeState(true)
            } else {
                TikTokAccessibilityService.instance?.stopAutoChat()

                isRunning = false
                btnToggle.text = "MULAI"
                btnToggle.setBackgroundColor(Color.parseColor("#FE2C55"))
                statusIndicator.setBackgroundColor(Color.parseColor("#94A3B8"))
                bubbleStatusDot.setBackgroundColor(Color.parseColor("#94A3B8"))
                tvStatusText.text = "Berhenti"
                tvStatusText.setTextColor(Color.parseColor("#64748B"))
                tvBubbleLabel.text = "AutoChat"
                Toast.makeText(this, "Auto Chat Dihentikan.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun snapToNearestEdge() {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels

        val viewWidth = floatingView.width.takeIf { it > 0 } ?: 180
        val middleX = screenWidth / 2

        params.x = if (params.x + viewWidth / 2 < middleX) {
            16
        } else {
            screenWidth - viewWidth - 16
        }
        try {
            windowManager.updateViewLayout(floatingView, params)
        } catch (_: Exception) {}
    }

    /**
     * MODE KALIBRASI 1x SENTUH:
     * Menampilkan overlay transparan satu layar penuh untuk merekam pixel koordinat asli
     * saat user menyentuh langsung kolom komentar & tombol kirim di TikTok Live.
     */
    private var calibrationOverlayView: View? = null

    private fun startOneTouchCalibration() {
        if (calibrationOverlayView != null) return

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val calParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        val calContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#44000000")) // Semi transparan gelap lembut
        }

        // Banner petunjuk di atas layar
        val bannerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val padH = (16 * resources.displayMetrics.density).toInt()
            val padV = (12 * resources.displayMetrics.density).toInt()
            setPadding(padH, padV, padH, padV)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadius = 16 * resources.displayMetrics.density
            }
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = (60 * resources.displayMetrics.density).toInt()
            }
            layoutParams = lp
        }

        val tvStepTitle = TextView(this).apply {
            text = "📍 MODE KALIBRASI 1x SENTUH"
            setTextColor(Color.parseColor("#FE2C55"))
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val tvStepDesc = TextView(this).apply {
            text = "Langkah 1/2: Sentuh KOLOM KOMENTAR di TikTok Anda"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 8)
        }

        val btnBatal = TextView(this).apply {
            text = "✕ Batal Kalibrasi"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(8, 4, 8, 4)
            setOnClickListener {
                stopOneTouchCalibration()
            }
        }

        bannerLayout.addView(tvStepTitle)
        bannerLayout.addView(tvStepDesc)
        bannerLayout.addView(btnBatal)
        calContainer.addView(bannerLayout)

        var step = 1
        var recordedChatX = 0f
        var recordedChatY = 0f

        calContainer.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (step == 1) {
                    recordedChatX = event.rawX
                    recordedChatY = event.rawY
                    step = 2

                    // Animasi update teks banner langkah kedua
                    tvStepDesc.text = "Langkah 2/2: Sentuh TOMBOL KIRIM / PANAH TikTok"
                    tvStepDesc.setTextColor(Color.parseColor("#10B981"))
                    Toast.makeText(this, "Kolom Chat terkunci (${recordedChatX.toInt()}, ${recordedChatY.toInt()})", Toast.LENGTH_SHORT).show()
                } else if (step == 2) {
                    val recordedSendX = event.rawX
                    val recordedSendY = event.rawY

                    // Simpan ke SharedPreferences & Accessibility Service
                    prefs.edit()
                        .putFloat("calibrated_chat_x", recordedChatX)
                        .putFloat("calibrated_chat_y", recordedChatY)
                        .putFloat("calibrated_send_x", recordedSendX)
                        .putFloat("calibrated_send_y", recordedSendY)
                        .apply()

                    TikTokAccessibilityService.instance?.setCalibratedCoordinates(
                        recordedChatX, recordedChatY, recordedSendX, recordedSendY
                    )

                    Toast.makeText(this, "Kalibrasi Berhasil! Titik chat & kirim terkunci 100% presisi.", Toast.LENGTH_LONG).show()
                    stopOneTouchCalibration()
                }
                return@setOnTouchListener true
            }
            false
        }

        calibrationOverlayView = calContainer
        windowManager.addView(calContainer, calParams)
    }

    private fun stopOneTouchCalibration() {
        calibrationOverlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
            calibrationOverlayView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopOneTouchCalibration()
        TikTokAccessibilityService.instance?.stopAutoChat()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}
