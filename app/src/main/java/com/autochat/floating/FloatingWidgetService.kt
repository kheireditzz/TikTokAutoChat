package com.autochat.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
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
    private var isRunning = false
    private var isMinimized = false

    private val CHANNEL_ID = "AutoChat_Floating_Channel"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

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
            x = 60
            y = 160
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
                .setContentText("Widget melayang siap digunakan.")
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
        val tvActiveCount = floatingView.findViewById<TextView>(R.id.tvActiveCount)

        // Custom Chat Checkboxes & EditTexts
        val cb1 = floatingView.findViewById<CheckBox>(R.id.cbPreset1)
        val et1 = floatingView.findViewById<EditText>(R.id.etPreset1)
        val cb2 = floatingView.findViewById<CheckBox>(R.id.cbPreset2)
        val et2 = floatingView.findViewById<EditText>(R.id.etPreset2)
        val cb3 = floatingView.findViewById<CheckBox>(R.id.cbPreset3)
        val et3 = floatingView.findViewById<EditText>(R.id.etPreset3)

        fun updateCountText() {
            var count = 0
            if (cb1.isChecked) count++
            if (cb2.isChecked) count++
            if (cb3.isChecked) count++
            tvActiveCount.text = "$count Aktif"
        }

        cb1.setOnCheckedChangeListener { _, _ -> updateCountText() }
        cb2.setOnCheckedChangeListener { _, _ -> updateCountText() }
        cb3.setOnCheckedChangeListener { _, _ -> updateCountText() }

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

        // Buka keyboard saat edit text diklik
        val textTouchListener = View.OnTouchListener { _, _ ->
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            windowManager.updateViewLayout(floatingView, params)
            false
        }
        et1.setOnTouchListener(textTouchListener)
        et2.setOnTouchListener(textTouchListener)
        et3.setOnTouchListener(textTouchListener)
        etDelay.setOnTouchListener(textTouchListener)

        btnClose.setOnClickListener {
            TikTokAccessibilityService.instance?.stopAutoChat()
            stopSelf()
        }

        btnToggle.setOnClickListener {
            if (!isRunning) {
                // Verifikasi apakah Layanan Aksesibilitas aktif
                val service = TikTokAccessibilityService.instance
                if (service == null) {
                    Toast.makeText(this, "Layanan Aksesibilitas belum AKTIF!\nBuka pengaturan dan aktifkan AutoChat.", Toast.LENGTH_LONG).show()
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                    return@setOnClickListener
                }

                val activeList = arrayListOf<String>()
                if (cb1.isChecked && et1.text.isNotBlank()) activeList.add(et1.text.toString().trim())
                if (cb2.isChecked && et2.text.isNotBlank()) activeList.add(et2.text.toString().trim())
                if (cb3.isChecked && et3.text.isNotBlank()) activeList.add(et3.text.toString().trim())

                if (activeList.isEmpty()) {
                    Toast.makeText(this, "Centang minimal 1 pesan untuk dikirim!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val delay = etDelay.text.toString().toLongOrNull() ?: 4L

                // Lepas fokus keyboard agar TikTok tidak terhalangi
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                windowManager.updateViewLayout(floatingView, params)

                // Panggil langsung instance service
                service.startAutoChat(activeList, delay)

                isRunning = true
                btnToggle.text = "STOP"
                btnToggle.setBackgroundColor(Color.parseColor("#DA3633"))
                statusIndicator.setBackgroundColor(Color.parseColor("#00E676"))
                bubbleStatusDot.setBackgroundColor(Color.parseColor("#00E676"))
                tvStatusText.text = "Mengetik tiap ${delay}s"
                tvStatusText.setTextColor(Color.parseColor("#00E676"))
                tvBubbleLabel.text = "RUNNING"
                Toast.makeText(this, "Auto Chat Mulai Berjalan!", Toast.LENGTH_SHORT).show()
            } else {
                TikTokAccessibilityService.instance?.stopAutoChat()

                isRunning = false
                btnToggle.text = "MULAI"
                btnToggle.setBackgroundColor(Color.parseColor("#0066FF"))
                statusIndicator.setBackgroundColor(Color.parseColor("#8B949E"))
                bubbleStatusDot.setBackgroundColor(Color.parseColor("#8B949E"))
                tvStatusText.text = "Berhenti"
                tvStatusText.setTextColor(Color.parseColor("#8B949E"))
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
            20
        } else {
            screenWidth - viewWidth - 20
        }
        try {
            windowManager.updateViewLayout(floatingView, params)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        TikTokAccessibilityService.instance?.stopAutoChat()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}
