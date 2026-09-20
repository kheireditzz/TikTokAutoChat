package com.autochat.floating

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*

class FloatingWidgetService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var params: WindowManager.LayoutParams
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

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
            x = 80
            y = 180
        }

        windowManager.addView(floatingView, params)
        setupInteractions()
    }

    private fun setupInteractions() {
        val header = floatingView.findViewById<LinearLayout>(R.id.layoutHeader)
        val btnClose = floatingView.findViewById<ImageView>(R.id.btnCloseFloating)
        val btnToggle = floatingView.findViewById<Button>(R.id.btnToggleStart)
        val etDelay = floatingView.findViewById<EditText>(R.id.etDelayInput)
        val statusIndicator = floatingView.findViewById<View>(R.id.statusIndicator)

        // Custom Chat Checkboxes & EditTexts
        val cb1 = floatingView.findViewById<CheckBox>(R.id.cbPreset1)
        val et1 = floatingView.findViewById<EditText>(R.id.etPreset1)
        val cb2 = floatingView.findViewById<CheckBox>(R.id.cbPreset2)
        val et2 = floatingView.findViewById<EditText>(R.id.etPreset2)
        val cb3 = floatingView.findViewById<CheckBox>(R.id.cbPreset3)
        val et3 = floatingView.findViewById<EditText>(R.id.etPreset3)

        // Fitur Drag & Move Floating Widget
        header.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                }
                return false
            }
        })

        // Izinkan EditText menerima fokus keyboard saat disentuh
        val textTouchListener = View.OnTouchListener { _, _ ->
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            windowManager.updateViewLayout(floatingView, params)
            false
        }
        et1.setOnTouchListener(textTouchListener)
        et2.setOnTouchListener(textTouchListener)
        et3.setOnTouchListener(textTouchListener)

        btnClose.setOnClickListener {
            stopSelf()
        }

        btnToggle.setOnClickListener {
            if (!isRunning) {
                // Kumpulkan teks dari preset yang statusnya ON (Checked)
                val activeList = arrayListOf<String>()
                if (cb1.isChecked && et1.text.isNotEmpty()) activeList.add(et1.text.toString().trim())
                if (cb2.isChecked && et2.text.isNotEmpty()) activeList.add(et2.text.toString().trim())
                if (cb3.isChecked && et3.text.isNotEmpty()) activeList.add(et3.text.toString().trim())

                if (activeList.isEmpty()) {
                    Toast.makeText(this, "Centang dan isi minimal 1 custom chat!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val delay = etDelay.text.toString().toIntOrNull() ?: 5

                // Kembalikan flag agar tidak menghalangi aplikasi TikTok
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                windowManager.updateViewLayout(floatingView, params)

                // Kirim daftar pesan aktif ke service aksesibilitas
                val intent = Intent(TikTokAccessibilityService.ACTION_START_AUTOTYPE).apply {
                    putStringArrayListExtra(TikTokAccessibilityService.EXTRA_MESSAGES_LIST, activeList)
                    putExtra(TikTokAccessibilityService.EXTRA_DELAY_SEC, delay)
                }
                sendBroadcast(intent)

                isRunning = true
                btnToggle.text = "STOP"
                btnToggle.setBackgroundColor(Color.parseColor("#DA3633"))
                statusIndicator.setBackgroundColor(Color.parseColor("#00E5FF"))
            } else {
                val intent = Intent(TikTokAccessibilityService.ACTION_STOP_AUTOTYPE)
                sendBroadcast(intent)

                isRunning = false
                btnToggle.text = "START"
                btnToggle.setBackgroundColor(Color.parseColor("#238636"))
                statusIndicator.setBackgroundColor(Color.parseColor("#8B949E"))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}
