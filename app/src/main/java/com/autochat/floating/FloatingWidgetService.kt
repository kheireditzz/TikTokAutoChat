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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.*
import android.widget.*
import org.json.JSONArray

class FloatingWidgetService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var prefs: SharedPreferences

    private var isRunning = false
    private var isMinimized = false

    private val CHANNEL_ID = "AutoChat_Floating_Channel"

    // Views untuk Kalibrasi Non-Blocking (Bebas Sentuh Layar)
    private var pinChatView: View? = null
    private var pinSendView: View? = null
    private var calibrationToolbarView: View? = null

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
            x = 30
            y = 180
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
                .setContentText("Widget melayang siap mengontrol chat otomatis.")
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
        val etTargetLimit = floatingView.findViewById<EditText>(R.id.etTargetLimitInput)
        val statusIndicator = floatingView.findViewById<View>(R.id.statusIndicator)
        val bubbleStatusDot = floatingView.findViewById<View>(R.id.bubbleStatusDot)
        val tvStatusText = floatingView.findViewById<TextView>(R.id.tvStatusText)
        val tvBubbleLabel = floatingView.findViewById<TextView>(R.id.tvBubbleLabel)
        val tvBubbleSentCount = floatingView.findViewById<TextView>(R.id.tvBubbleSentCount)
        val tvActiveCount = floatingView.findViewById<TextView>(R.id.tvActiveCount)
        val tvSentCountStatus = floatingView.findViewById<TextView>(R.id.tvSentCountStatus)
        val containerMessages = floatingView.findViewById<LinearLayout>(R.id.containerFloatingMessages)
        val btnAddMessage = floatingView.findViewById<TextView>(R.id.btnFloatingAddMessage)
        val btnCalibrate = floatingView.findViewById<TextView>(R.id.btnCalibrate)

        var totalSentCount = 0

        val widgetMessageItems = mutableListOf<Pair<CheckBox, EditText>>()

        fun persistMessages() {
            val arr = JSONArray()
            for ((_, et) in widgetMessageItems) {
                val t = et.text.toString().trim()
                if (t.isNotBlank()) arr.put(t)
            }
            if (arr.length() > 0) {
                prefs.edit().putString("messages_json", arr.toString()).apply()
            }
        }

        fun updateCountText() {
            var count = 0
            for ((cb, et) in widgetMessageItems) {
                if (cb.isChecked && et.text.isNotBlank()) count++
            }
            tvActiveCount.text = "$count Aktif"
            persistMessages()
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

                setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        persistMessages()
                    }
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

        // Muat pesan dari SharedPreferences
        fun reloadMessagesFromPrefs() {
            containerMessages.removeAllViews()
            widgetMessageItems.clear()

            val jsonStr = prefs.getString("messages_json", null)
            val loadedList = mutableListOf<String>()
            if (!jsonStr.isNullOrBlank()) {
                try {
                    val arr = JSONArray(jsonStr)
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

            for (msg in loadedList) {
                if (msg.isNotBlank()) {
                    addWidgetMessageItem(msg, true)
                }
            }
            etDelay.setText(prefs.getInt("delay", 4).toString())
            etTargetLimit.setText(prefs.getInt("max_count", 0).toString())
        }

        reloadMessagesFromPrefs()

        btnAddMessage.setOnClickListener {
            addWidgetMessageItem("", true)
        }

        btnCalibrate.setOnClickListener {
            startPinCalibration()
        }

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
                val expandedWidth = (275 * resources.displayMetrics.density).toInt()
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

        // Listener status kirim sukses
        TikTokAccessibilityService.instance?.setOnChatSentListener { count, _ ->
            totalSentCount = count
            tvSentCountStatus.text = "$count Terkirim"
            tvBubbleSentCount.text = "$count kirim"
            tvStatusText.text = "Terkirim $count pesan ✓"
        }

        // Listener jika target batas pesan telah tercapai
        TikTokAccessibilityService.instance?.setOnTargetReachedListener { reachedCount ->
            isRunning = false
            btnToggle.text = "MULAI"
            btnToggle.setBackgroundResource(R.drawable.bg_tiktok_gradient)
            statusIndicator.setBackgroundColor(Color.parseColor("#94A3B8"))
            bubbleStatusDot.setBackgroundColor(Color.parseColor("#94A3B8"))
            tvStatusText.text = "Selesai ($reachedCount pesan)"
            tvStatusText.setTextColor(Color.parseColor("#3B82F6"))
            tvBubbleLabel.text = "SELESAI"
        }

        fun stopRunningState() {
            TikTokAccessibilityService.instance?.stopAutoChat()
            isRunning = false
            btnToggle.text = "MULAI"
            btnToggle.setBackgroundResource(R.drawable.bg_tiktok_gradient)
            statusIndicator.setBackgroundColor(Color.parseColor("#94A3B8"))
            bubbleStatusDot.setBackgroundColor(Color.parseColor("#94A3B8"))
            tvStatusText.text = "Berhenti"
            tvStatusText.setTextColor(Color.parseColor("#64748B"))
            tvBubbleLabel.text = "AutoChat"
            Toast.makeText(this, "Auto Chat Dihentikan.", Toast.LENGTH_SHORT).show()
        }

        btnToggle.setOnClickListener {
            if (!isRunning) {
                val service = TikTokAccessibilityService.instance
                if (service == null) {
                    Toast.makeText(this, "Aksesibilitas belum aktif! Buka Pengaturan HP.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                val activeList = arrayListOf<String>()
                for ((cb, et) in widgetMessageItems) {
                    val t = et.text.toString().trim()
                    if (cb.isChecked && t.isNotBlank()) {
                        activeList.add(t)
                    }
                }

                if (activeList.isEmpty()) {
                    Toast.makeText(this, "Centang minimal 1 komentar untuk dikirim!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val delay = etDelay.text.toString().toLongOrNull() ?: 4L
                val targetLimit = etTargetLimit.text.toString().toIntOrNull() ?: 0
                val antiSpam = prefs.getBoolean("anti_spam", true)

                // Simpan delay & limit terbaru
                prefs.edit()
                    .putInt("delay", delay.toInt())
                    .putInt("max_count", targetLimit)
                    .apply()

                service.setOnChatSentListener { count, _ ->
                    totalSentCount = count
                    tvSentCountStatus.text = "$count Terkirim"
                    tvBubbleSentCount.text = "$count kirim"
                    tvStatusText.text = "Terkirim $count pesan ✓"
                }

                service.setOnTargetReachedListener { reachedCount ->
                    isRunning = false
                    btnToggle.text = "MULAI"
                    btnToggle.setBackgroundResource(R.drawable.bg_tiktok_gradient)
                    statusIndicator.setBackgroundColor(Color.parseColor("#94A3B8"))
                    bubbleStatusDot.setBackgroundColor(Color.parseColor("#94A3B8"))
                    tvStatusText.text = "Selesai ($reachedCount pesan)"
                    tvStatusText.setTextColor(Color.parseColor("#3B82F6"))
                    tvBubbleLabel.text = "SELESAI"
                }

                // Mulai AutoChat
                service.startAutoChat(activeList, delay, antiSpam, targetLimit)

                totalSentCount = 0
                tvSentCountStatus.text = "0 Terkirim"
                tvBubbleSentCount.text = "0 kirim"

                isRunning = true
                btnToggle.text = "STOP"
                btnToggle.setBackgroundColor(Color.parseColor("#EF4444"))
                statusIndicator.setBackgroundColor(Color.parseColor("#10B981"))
                bubbleStatusDot.setBackgroundColor(Color.parseColor("#10B981"))
                tvStatusText.text = if (targetLimit > 0) "Berjalan (${targetLimit}x)" else "Berjalan (${delay}s)"
                tvStatusText.setTextColor(Color.parseColor("#10B981"))
                tvBubbleLabel.text = "RUNNING"
                Toast.makeText(this, "Auto Chat Dimulai!", Toast.LENGTH_SHORT).show()

                // Otomatis minimize ke samping
                setMinimizeState(true)
            } else {
                stopRunningState()
            }
        }

        btnClose.setOnClickListener {
            stopRunningState()
            stopPinCalibration()
            stopSelf()
        }

        // Dragging handler untuk header dan bubble
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        val dragListener = View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        windowManager.updateViewLayout(floatingView, params)
                    } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isMinimized) {
                        snapToNearestEdge()
                    }
                    true
                }
                else -> false
            }
        }

        header.setOnTouchListener(dragListener)
        layoutBubble.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        windowManager.updateViewLayout(floatingView, params)
                    } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = kotlin.math.abs(event.rawX - initialTouchX)
                    val diffY = kotlin.math.abs(event.rawY - initialTouchY)
                    if (diffX < 15 && diffY < 15) {
                        setMinimizeState(false)
                    } else {
                        snapToNearestEdge()
                    }
                    true
                }
                else -> false
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
     * MODE KALIBRASI PIN TARGET (NON-BLOCKING / BEBAS SENTUH LAYAR):
     * Memunculkan Pin 1 (Chat) dan Pin 2 (Kirim) serta Toolbar Mini di atas.
     * Layar di belakangnya 100% bebas disentuh sehingga user bisa memunculkan keyboard
     * dan mengeklik tombol TikTok tanpa terhalang sama sekali.
     */
    private fun startPinCalibration() {
        if (pinChatView != null || pinSendView != null) return

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Koordinat awal (ambil dari saved prefs atau default layar)
        val savedChatX = prefs.getFloat("calibrated_chat_x", screenWidth * 0.15f)
        val savedChatY = prefs.getFloat("calibrated_chat_y", screenHeight * 0.94f)
        val savedSendX = prefs.getFloat("calibrated_send_x", screenWidth * 0.85f)
        val savedSendY = prefs.getFloat("calibrated_send_y", screenHeight * 0.62f)

        // 1. PIN 1: CHAT / KOLOM KOMENTAR (Merah TikTok)
        val pin1 = LayoutInflater.from(this).inflate(R.layout.layout_target_pointer, null)
        val tvLabel1 = pin1.findViewById<TextView>(R.id.tvTargetLabel)
        tvLabel1.text = "🔴 1. CHAT"

        val pin1Params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (savedChatX - 24 * displayMetrics.density).toInt().coerceAtLeast(0)
            y = (savedChatY - 24 * displayMetrics.density).toInt().coerceAtLeast(0)
        }

        // 2. PIN 2: TOMBOL KIRIM (Biru)
        val pin2 = LayoutInflater.from(this).inflate(R.layout.layout_target_pointer, null)
        val tvLabel2 = pin2.findViewById<TextView>(R.id.tvTargetLabel)
        tvLabel2.text = "🔵 2. KIRIM"
        tvLabel2.setTextColor(Color.parseColor("#38BDF8"))
        pin2.findViewById<View>(R.id.viewTargetRing).setBackgroundResource(R.drawable.bg_target_ring_blue)
        pin2.findViewById<View>(R.id.viewCrossH).setBackgroundColor(Color.parseColor("#3B82F6"))
        pin2.findViewById<View>(R.id.viewCrossV).setBackgroundColor(Color.parseColor("#3B82F6"))
        pin2.findViewById<View>(R.id.viewTargetCenter).setBackgroundResource(R.drawable.bg_target_center_blue)

        val pin2Params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (savedSendX - 24 * displayMetrics.density).toInt().coerceAtLeast(0)
            y = (savedSendY - 24 * displayMetrics.density).toInt().coerceAtLeast(0)
        }

        // 3. TOOLBAR KONTROL KALIBRASI DI ATAS LAYAR
        val toolbar = LayoutInflater.from(this).inflate(R.layout.layout_calibration_toolbar, null)
        val tvCoordInfo = toolbar.findViewById<TextView>(R.id.tvCalibCoordInfo)
        val btnSave = toolbar.findViewById<Button>(R.id.btnCalibSave)
        val btnTest = toolbar.findViewById<Button>(R.id.btnCalibTest)
        val btnReset = toolbar.findViewById<TextView>(R.id.btnCalibResetDefault)
        val btnClose = toolbar.findViewById<TextView>(R.id.btnCalibClose)

        val toolbarParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = (35 * displayMetrics.density).toInt()
        }

        fun updateLiveCoordinatesDisplay() {
            val circle1 = pin1.findViewById<View>(R.id.flTargetCircle)
            val circle2 = pin2.findViewById<View>(R.id.flTargetCircle)
            val loc1 = IntArray(2)
            val loc2 = IntArray(2)
            circle1.getLocationOnScreen(loc1)
            circle2.getLocationOnScreen(loc2)
            val cX = loc1[0] + circle1.width / 2
            val cY = loc1[1] + circle1.height / 2
            val sX = loc2[0] + circle2.width / 2
            val sY = loc2[1] + circle2.height / 2
            tvCoordInfo.text = "🔴 Chat: ($cX, $cY) • 🔵 Kirim: ($sX, $sY)"
        }

        fun makeDraggable(v: View, p: WindowManager.LayoutParams) {
            var startX = 0
            var startY = 0
            var touchX = 0f
            var touchY = 0f

            v.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = p.x
                        startY = p.y
                        touchX = event.rawX
                        touchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        p.x = startX + (event.rawX - touchX).toInt()
                        p.y = startY + (event.rawY - touchY).toInt()
                        try {
                            windowManager.updateViewLayout(v, p)
                        } catch (_: Exception) {}
                        updateLiveCoordinatesDisplay()
                        true
                    }
                    else -> false
                }
            }
        }

        makeDraggable(pin1, pin1Params)
        makeDraggable(pin2, pin2Params)

        btnSave.setOnClickListener {
            val circle1 = pin1.findViewById<View>(R.id.flTargetCircle)
            val circle2 = pin2.findViewById<View>(R.id.flTargetCircle)
            val loc1 = IntArray(2)
            val loc2 = IntArray(2)
            circle1.getLocationOnScreen(loc1)
            circle2.getLocationOnScreen(loc2)
            val cX = (loc1[0] + circle1.width / 2f)
            val cY = (loc1[1] + circle1.height / 2f)
            val sX = (loc2[0] + circle2.width / 2f)
            val sY = (loc2[1] + circle2.height / 2f)

            prefs.edit()
                .putFloat("calibrated_chat_x", cX)
                .putFloat("calibrated_chat_y", cY)
                .putFloat("calibrated_send_x", sX)
                .putFloat("calibrated_send_y", sY)
                .apply()

            TikTokAccessibilityService.instance?.setCalibratedCoordinates(cX, cY, sX, sY)
            Toast.makeText(this, "✅ Posisi tersimpan! Chat: (${cX.toInt()}, ${cY.toInt()}) | Kirim: (${sX.toInt()}, ${sY.toInt()})", Toast.LENGTH_SHORT).show()
            stopPinCalibration()
        }

        btnTest.setOnClickListener {
            val circle1 = pin1.findViewById<View>(R.id.flTargetCircle)
            val circle2 = pin2.findViewById<View>(R.id.flTargetCircle)
            val loc1 = IntArray(2)
            val loc2 = IntArray(2)
            circle1.getLocationOnScreen(loc1)
            circle2.getLocationOnScreen(loc2)
            val cX = loc1[0] + circle1.width / 2f
            val cY = loc1[1] + circle1.height / 2f
            val sX = loc2[0] + circle2.width / 2f
            val sY = loc2[1] + circle2.height / 2f

            val service = TikTokAccessibilityService.instance
            if (service != null) {
                Toast.makeText(this, "🎯 Mengetes klik Pin 1 lalu Pin 2...", Toast.LENGTH_SHORT).show()
                service.testTapSingleCoordinate(cX, cY)
                Handler(Looper.getMainLooper()).postDelayed({
                    service.testTapSingleCoordinate(sX, sY)
                }, 600)
            } else {
                Toast.makeText(this, "Aksesibilitas belum terhubung!", Toast.LENGTH_SHORT).show()
            }
        }

        btnReset.setOnClickListener {
            TikTokAccessibilityService.instance?.resetToDefaultCoordinates()
            Toast.makeText(this, "↩ Koordinat dikembalikan ke Default TikTok Live.", Toast.LENGTH_SHORT).show()
            stopPinCalibration()
        }

        btnClose.setOnClickListener {
            stopPinCalibration()
        }

        pinChatView = pin1
        pinSendView = pin2
        calibrationToolbarView = toolbar

        windowManager.addView(pin1, pin1Params)
        windowManager.addView(pin2, pin2Params)
        windowManager.addView(toolbar, toolbarParams)

        Handler(Looper.getMainLooper()).postDelayed({
            updateLiveCoordinatesDisplay()
        }, 100)

        Toast.makeText(this, "Mode Kalibrasi Aktif. Layar bebas disentuh!", Toast.LENGTH_SHORT).show()
    }

    private fun stopPinCalibration() {
        pinChatView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            pinChatView = null
        }
        pinSendView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            pinSendView = null
        }
        calibrationToolbarView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            calibrationToolbarView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPinCalibration()
        TikTokAccessibilityService.instance?.stopAutoChat()
        if (::floatingView.isInitialized) {
            try { windowManager.removeView(floatingView) } catch (_: Exception) {}
        }
    }
}
