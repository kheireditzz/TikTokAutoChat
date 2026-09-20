package com.autochat.floating

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class TikTokAccessibilityService : AccessibilityService() {

    companion object {
        var instance: TikTokAccessibilityService? = null
        const val ACTION_START_AUTOTYPE = "com.autochat.START_TYPE"
        const val ACTION_STOP_AUTOTYPE = "com.autochat.STOP_TYPE"
        const val EXTRA_MESSAGES_LIST = "extra_messages_list"
        const val EXTRA_DELAY_SEC = "extra_delay_sec"

        fun isServiceRunning(): Boolean = instance != null
    }

    private var isRunning = false
    private var activeMessages: ArrayList<String> = arrayListOf()
    private var currentMessageIndex = 0
    private var delaySeconds: Long = 5
    private val handler = Handler(Looper.getMainLooper())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_START_AUTOTYPE -> {
                    val receivedList = intent.getStringArrayListExtra(EXTRA_MESSAGES_LIST)
                    activeMessages = if (!receivedList.isNullOrEmpty()) receivedList else arrayListOf("Halo kak!")
                    delaySeconds = intent.getIntExtra(EXTRA_DELAY_SEC, 5).toLong()
                    currentMessageIndex = 0
                    startAutomationLoop()
                }
                ACTION_STOP_AUTOTYPE -> {
                    stopAutomationLoop()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val filter = IntentFilter().apply {
            addAction(ACTION_START_AUTOTYPE)
            addAction(ACTION_STOP_AUTOTYPE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    private fun startAutomationLoop() {
        if (isRunning || activeMessages.isEmpty()) return
        isRunning = true
        Toast.makeText(this, "Auto Chat Dimulai (${activeMessages.size} pesan berputar)", Toast.LENGTH_SHORT).show()
        handler.post(actionRunnable)
    }

    private fun stopAutomationLoop() {
        isRunning = false
        handler.removeCallbacks(actionRunnable)
    }

    private val actionRunnable = object : Runnable {
        override fun run() {
            if (!isRunning || activeMessages.isEmpty()) return

            val messageToSend = activeMessages[currentMessageIndex % activeMessages.size]
            currentMessageIndex++

            executeCommentWorkflow(messageToSend)

            handler.postDelayed(this, delaySeconds * 1000)
        }
    }

    private fun executeCommentWorkflow(textToType: String) {
        val rootNode = rootInActiveWindow ?: return

        // 1. Cari elemen input teks
        val editTexts = findNodesByClassName(rootNode, "android.widget.EditText")
        if (editTexts.isNotEmpty()) {
            val targetInput = editTexts.last() // Biasanya kolom chat TikTok berada di bagian paling aktif/bawah
            typeAndSend(targetInput, textToType)
            return
        }

        // 2. Jika kolom input belum muncul, klik bar/tombol pembuka chat TikTok
        val opened = clickCommentTrigger(rootNode)
        if (opened) {
            // Beri waktu animasi dialog chat TikTok terbuka, lalu ketik
            handler.postDelayed({
                val updatedRoot = rootInActiveWindow ?: return@postDelayed
                val inputs = findNodesByClassName(updatedRoot, "android.widget.EditText")
                if (inputs.isNotEmpty()) {
                    typeAndSend(inputs.last(), textToType)
                }
            }, 500)
        }
    }

    private fun typeAndSend(inputNode: AccessibilityNodeInfo, text: String) {
        inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        
        // Simulasikan klik pada kolom input agar keyboard/state siap
        inputNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val textSet = inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

        if (!textSet) {
            // Fallback paste
            inputNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }

        // Tunggu sedikit agar tombol 'Kirim' aktif (state enable berubah)
        handler.postDelayed({
            val root = rootInActiveWindow ?: return@postDelayed
            sendComment(root)
        }, 350)
    }

    private fun sendComment(root: AccessibilityNodeInfo) {
        // Cek ID spesifik TikTok
        val sendById = root.findAccessibilityNodeInfosByViewId("com.zhiliaoapp.musically:id/btn_send")
        if (sendById.isNotEmpty()) {
            sendById[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }

        val sendByIdLite = root.findAccessibilityNodeInfosByViewId("com.zhiliaoapp.musically.go:id/send")
        if (sendByIdLite.isNotEmpty()) {
            sendByIdLite[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }

        // Cek deskripsi tombol / contentDescription
        val allNodes = getAllNodes(root)
        for (node in allNodes) {
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val text = node.text?.toString()?.lowercase() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            if (desc.contains("send") || desc.contains("kirim") ||
                text.contains("send") || text.contains("kirim") ||
                viewId.contains("send") || viewId.contains("submit")) {

                if (node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return
                } else if (node.parent?.isClickable == true) {
                    node.parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return
                }
            }
        }

        // Fallback Gesture Tap tombol kirim (pojok kanan bawah sekitar keyboard)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val metrics = resources.displayMetrics
            val x = metrics.widthPixels * 0.92f
            val y = metrics.heightPixels * 0.60f // Area bar atas keyboard TikTok
            simulateTap(x, y)
        }
    }

    private fun clickCommentTrigger(root: AccessibilityNodeInfo): Boolean {
        val triggers = listOf(
            "Tambahkan komentar...", "Tambahkan komentar",
            "Add comment...", "Add comment",
            "Say something...", "Say something",
            "Kirim komentar", "Komentar"
        )

        for (triggerText in triggers) {
            val nodes = root.findAccessibilityNodeInfosByText(triggerText)
            for (node in nodes) {
                if (performSafeClick(node)) return true
            }
        }

        // Cari tombol ikon chat di live
        val allNodes = getAllNodes(root)
        for (node in allNodes) {
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val idName = node.viewIdResourceName?.lowercase() ?: ""
            if (desc.contains("komentar") || desc.contains("comment") || idName.contains("comment")) {
                if (performSafeClick(node)) return true
            }
        }

        return false
    }

    private fun performSafeClick(node: AccessibilityNodeInfo): Boolean {
        var curr: AccessibilityNodeInfo? = node
        while (curr != null) {
            if (curr.isClickable) {
                return curr.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            curr = curr.parent
        }

        // Jika tidak clickable langsung, tap posisi koordinatnya
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                simulateTap(rect.centerX().toFloat(), rect.centerY().toFloat())
                return true
            }
        }
        return false
    }

    private fun simulateTap(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(x, y)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
                .build()
            dispatchGesture(gesture, null, null)
        }
    }

    private fun findNodesByClassName(root: AccessibilityNodeInfo, className: String): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.className == className) {
                result.add(node)
            }
            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }
        traverse(root)
        return result
    }

    private fun getAllNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null) return
            result.add(node)
            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }
        traverse(root)
        return result
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        stopAutomationLoop()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutomationLoop()
        try {
            unregisterReceiver(receiver)
        } catch (_: Exception) {}
        instance = null
    }
}
