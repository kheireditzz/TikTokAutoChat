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

        // Bind quick coordinate inputs
        val etFloatCoordX = floatingView.findViewById<EditText>(R.id.etFloatCoordX)
        val etFloatCoordY = floatingView.findViewById<EditText>(R.id.etFloatCoordY)
        val etFloatSendX = floatingView.findViewById<EditText>(R.id.etFloatSendX)
        val etFloatSendY = floatingView.findViewById<EditText>(R.id.etFloatSendY)

        etFloatCoordX.setText(prefs.getInt("coord_input_x", 25).toString())
        etFloatCoordY.setText(prefs.getInt("coord_input_y", 96).toString())
        etFloatSendX.setText(prefs.getInt("coord_send_x", 92).toString())
        etFloatSendY.setText(prefs.getInt("coord_send_y", 94).toString())

        // Focus handling untuk coordinate input agar keyboard muncul saat ditekan
        val coordTouchListener = View.OnTouchListener { _, _ ->
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            try {
                windowManager.updateViewLayout(floatingView, params)
            } catch (_: Exception) {}
            false
        }
        etFloatCoordX.setOnTouchListener(coordTouchListener)
        etFloatCoordY.setOnTouchListener(coordTouchListener)
        etFloatSendX.setOnTouchListener(coordTouchListener)
        etFloatSendY.setOnTouchListener(coordTouchListener)

        // Tombol Geser Titik Layar (Target Pembidik Visual)
        val btnToggleTargets = floatingView.findViewById<TextView>(R.id.btnToggleDraggableTargets)
        btnToggleTargets.setOnClickListener {
            toggleTargetPointers(etFloatCoordX, etFloatCoordY, etFloatSendX, etFloatSendY)
        }

        etDelay.setText(prefs.getInt("delay", 4).toString())

        fun setMinimizeState(minimized: Boolean) {
            isMinimized = minimized
            if (minimized) {
                layoutExpanded.visibility = View.GONE
                layoutBubble.visibility = View.VISIBLE
                snapToNearestEdge()
            } else {
                layoutBubble.visibility = View.GONE
                layoutExpanded.visibility = View.VISIBLE
            }
            windowManager.updateViewLayout(floatingView, params)
        }

        btnMinimize.setOnClickListener {
            setMinimizeState(true)
        }

        layoutBubble.setOnClickListener {
            setMinimizeState(false)
        }

        // Drag Handler dengan Auto Snap ke Samping Layar
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
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick && v == layoutBubble) {
                            v?.performClick()
                        } else {
                            snapToNearestEdge()
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

                val coordInputX = etFloatCoordX.text.toString().toIntOrNull() ?: 25
                val coordInputY = etFloatCoordY.text.toString().toIntOrNull() ?: 96
                val coordSendX = etFloatSendX.text.toString().toIntOrNull() ?: 92
                val coordSendY = etFloatSendY.text.toString().toIntOrNull() ?: 94

                // Simpan perubahan pesan & koordinat saat ini ke SharedPreferences secara otomatis
                prefs.edit()
                    .putString("messages_json", allListJson.toString())
                    .putInt("delay", delay.toInt())
                    .putInt("coord_input_x", coordInputX)
                    .putInt("coord_input_y", coordInputY)
                    .putInt("coord_send_x", coordSendX)
                    .putInt("coord_send_y", coordSendY)
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

                // Panggil service dengan koordinat persentase
                service.startAutoChat(
                    activeList,
                    delay,
                    antiSpam,
                    coordInputX / 100f,
                    coordInputY / 100f,
                    coordSendX / 100f,
                    coordSendY / 100f
                )

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

    // Draggable Target Pointer Views
    private var chatTargetView: View? = null
    private var sendTargetView: View? = null
    private var isTargetOverlayVisible = false

    private fun toggleTargetPointers(
        etCoordX: EditText,
        etCoordY: EditText,
        etSendX: EditText,
        etSendY: EditText
    ) {
        if (isTargetOverlayVisible) {
            removeTargetPointers()
            Toast.makeText(this, "Target titik layar ditutup & disimpan! ✅", Toast.LENGTH_SHORT).show()
        } else {
            showTargetPointers(etCoordX, etCoordY, etSendX, etSendY)
            Toast.makeText(this, "🎯 Geser target merah ke kolom chat, dan biru ke tombol kirim!", Toast.LENGTH_LONG).show()
        }
    }

    private fun showTargetPointers(
        etCoordX: EditText,
        etCoordY: EditText,
        etSendX: EditText,
        etSendY: EditText
    ) {
        if (isTargetOverlayVisible) return

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 1. Target Pointer 1: Kolom Chat (Merah)
        chatTargetView = LayoutInflater.from(this).inflate(R.layout.layout_target_pointer, null)
        val tvChatLabel = chatTargetView!!.findViewById<TextView>(R.id.tvTargetLabel)
        tvChatLabel.text = "📍 1. CHAT (GESER SAYA)"

        val savedChatXPercent = prefs.getInt("coord_input_x", 25)
        val savedChatYPercent = prefs.getInt("coord_input_y", 96)

        val chatParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth * (savedChatXPercent / 100f) - 24 * displayMetrics.density).toInt()
            y = (screenHeight * (savedChatYPercent / 100f) - 24 * displayMetrics.density).toInt()
        }

        setupDraggablePointer(chatTargetView!!, chatParams, displayMetrics, tvChatLabel, "CHAT") { newXPercent, newYPercent ->
            etCoordX.setText(newXPercent.toString())
            etCoordY.setText(newYPercent.toString())
            prefs.edit()
                .putInt("coord_input_x", newXPercent)
                .putInt("coord_input_y", newYPercent)
                .apply()
        }

        windowManager.addView(chatTargetView, chatParams)

        // 2. Target Pointer 2: Tombol Kirim (Biru)
        sendTargetView = LayoutInflater.from(this).inflate(R.layout.layout_target_pointer, null)
        val tvSendLabel = sendTargetView!!.findViewById<TextView>(R.id.tvTargetLabel)
        tvSendLabel.text = "🎯 2. KIRIM (GESER SAYA)"
        tvSendLabel.setBackgroundColor(Color.parseColor("#DD1E40AF"))

        val ringView = sendTargetView!!.findViewById<View>(R.id.viewTargetRing)
        val crossH = sendTargetView!!.findViewById<View>(R.id.viewCrossH)
        val crossV = sendTargetView!!.findViewById<View>(R.id.viewCrossV)
        val centerDot = sendTargetView!!.findViewById<View>(R.id.viewTargetCenter)
        ringView.setBackgroundResource(R.drawable.bg_target_ring_blue)
        crossH.setBackgroundColor(Color.parseColor("#3B82F6"))
        crossV.setBackgroundColor(Color.parseColor("#3B82F6"))
        centerDot.setBackgroundResource(R.drawable.bg_target_center_blue)

        val savedSendXPercent = prefs.getInt("coord_send_x", 92)
        val savedSendYPercent = prefs.getInt("coord_send_y", 94)

        val sendParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth * (savedSendXPercent / 100f) - 24 * displayMetrics.density).toInt()
            y = (screenHeight * (savedSendYPercent / 100f) - 24 * displayMetrics.density).toInt()
        }

        setupDraggablePointer(sendTargetView!!, sendParams, displayMetrics, tvSendLabel, "KIRIM") { newXPercent, newYPercent ->
            etSendX.setText(newXPercent.toString())
            etSendY.setText(newYPercent.toString())
            prefs.edit()
                .putInt("coord_send_x", newXPercent)
                .putInt("coord_send_y", newYPercent)
                .apply()
        }

        windowManager.addView(sendTargetView, sendParams)

        isTargetOverlayVisible = true
    }

    private fun setupDraggablePointer(
        targetView: View,
        targetParams: WindowManager.LayoutParams,
        metrics: DisplayMetrics,
        labelView: TextView,
        tag: String,
        onCoordUpdated: (Int, Int) -> Unit
    ) {
        targetView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = targetParams.x
                        initialY = targetParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // Pergerakan bebas tanpa batas ke seluruh sudut layar
                        targetParams.x = (initialX + (event.rawX - initialTouchX)).toInt()
                        targetParams.y = (initialY + (event.rawY - initialTouchY)).toInt()
                        try {
                            windowManager.updateViewLayout(targetView, targetParams)
                        } catch (_: Exception) {}

                        // Hitung titik pusat presisi (center bullseye):
                        // Lingkaran berukuran 48dp x 48dp, titik pusat tepat di tengah +24dp
                        val halfSize = (24 * metrics.density).toInt()
                        val bullseyeX = targetParams.x + halfSize
                        val bullseyeY = targetParams.y + halfSize

                        val xPercent = ((bullseyeX.toFloat() / metrics.widthPixels.toFloat()) * 100).toInt().coerceIn(0, 100)
                        val yPercent = ((bullseyeY.toFloat() / metrics.heightPixels.toFloat()) * 100).toInt().coerceIn(0, 100)

                        labelView.text = "$tag: X:$xPercent% Y:$yPercent%"
                        onCoordUpdated(xPercent, yPercent)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun removeTargetPointers() {
        if (chatTargetView != null) {
            try {
                windowManager.removeView(chatTargetView)
            } catch (_: Exception) {}
            chatTargetView = null
        }
        if (sendTargetView != null) {
            try {
                windowManager.removeView(sendTargetView)
            } catch (_: Exception) {}
            sendTargetView = null
        }
        isTargetOverlayVisible = false
    }

    override fun onDestroy() {
        super.onDestroy()
        removeTargetPointers()
        TikTokAccessibilityService.instance?.stopAutoChat()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}
