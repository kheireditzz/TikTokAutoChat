package com.autochat.floating

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import kotlin.random.Random

class TikTokAccessibilityService : AccessibilityService() {

    companion object {
        var instance: TikTokAccessibilityService? = null
        fun isServiceRunning(): Boolean = instance != null
    }

    private var isRunning = false
    private var activeMessages: ArrayList<String> = arrayListOf()
    private var currentMessageIndex = 0
    private var delaySeconds: Long = 4
    private var enableAntiSpam = true
    private var sentCount = 0
    private var onChatSentListener: ((Int, String) -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isTypingWorkflowRunning = false

    // Kalibrasi 1x Sentuh Coordinates (Disimpan dalam pixel pasti layar user)
    private var calibratedChatX: Float = -1f
    private var calibratedChatY: Float = -1f
    private var calibratedSendX: Float = -1f
    private var calibratedSendY: Float = -1f

    private val safeVariations = listOf("✨", "🔥", "⚡", "👍", "🛍️", "✓", "💯", "🙌", "😊")

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        loadCalibratedCoordinates()
        Toast.makeText(this, "Aksesibilitas AutoChat Terhubung! Siap digunakan.", Toast.LENGTH_SHORT).show()
    }

    fun loadCalibratedCoordinates() {
        val prefs = getSharedPreferences("AutoChatPrefs", Context.MODE_PRIVATE)
        calibratedChatX = prefs.getFloat("calibrated_chat_x", -1f)
        calibratedChatY = prefs.getFloat("calibrated_chat_y", -1f)
        calibratedSendX = prefs.getFloat("calibrated_send_x", -1f)
        calibratedSendY = prefs.getFloat("calibrated_send_y", -1f)
    }

    fun setCalibratedCoordinates(chatX: Float, chatY: Float, sendX: Float, sendY: Float) {
        calibratedChatX = chatX
        calibratedChatY = chatY
        calibratedSendX = sendX
        calibratedSendY = sendY
        val prefs = getSharedPreferences("AutoChatPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat("calibrated_chat_x", chatX)
            .putFloat("calibrated_chat_y", chatY)
            .putFloat("calibrated_send_x", sendX)
            .putFloat("calibrated_send_y", sendY)
            .apply()
    }

    fun setOnChatSentListener(listener: ((Int, String) -> Unit)?) {
        onChatSentListener = listener
    }

    fun startAutoChat(
        messages: ArrayList<String>,
        delay: Long,
        antiSpam: Boolean = true
    ) {
        if (messages.isEmpty()) return
        activeMessages = messages
        delaySeconds = delay
        enableAntiSpam = antiSpam
        currentMessageIndex = 0
        sentCount = 0
        isRunning = true
        isTypingWorkflowRunning = false
        handler.removeCallbacks(actionRunnable)
        handler.post(actionRunnable)
    }

    fun stopAutoChat() {
        isRunning = false
        isTypingWorkflowRunning = false
        handler.removeCallbacks(actionRunnable)
    }

    private val actionRunnable = object : Runnable {
        override fun run() {
            if (!isRunning || activeMessages.isEmpty()) return

            if (isTypingWorkflowRunning) {
                // Tunggu jika siklus sebelumnya masih berlangsung
                handler.postDelayed(this, 1000)
                return
            }

            var messageToSend = activeMessages[currentMessageIndex % activeMessages.size]
            currentMessageIndex++

            if (enableAntiSpam) {
                val suffix = safeVariations[Random.nextInt(safeVariations.size)]
                messageToSend = "$messageToSend $suffix"
            }

            isTypingWorkflowRunning = true
            try {
                executeSmartKeyboardWorkflow(messageToSend)
            } catch (e: Exception) {
                e.printStackTrace()
                isTypingWorkflowRunning = false
            }

            handler.postDelayed(this, delaySeconds * 1000)
        }
    }

    /**
     * ALUR CERDAS (SMART KEYBOARD WORKFLOW):
     * 1. Klik kolom komentar bawah kiri di TikTok Live (via Accessibility Node / Tap presisi).
     * 2. Tunggu sampai Keyboard/EditText aktif muncul (polling hingga muncul).
     * 3. Setelah keyboard/input benar-benar ADA di layar, masukkan teks komentar & copy ke clipboard.
     * 4. Tekan tombol Kirim / Enter (IME Action atau Tombol Send TikTok).
     */
    private fun executeSmartKeyboardWorkflow(textToType: String) {
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            isTypingWorkflowRunning = false
            return
        }

        // Cek apakah input chat sudah terbuka saat ini
        val existingInput = findActiveTikTokInput(rootNode)
        if (existingInput != null) {
            // Input sudah siap di layar, langsung isi teks dan kirim
            fillAndSend(existingInput, textToType)
            return
        }

        // TAHAP 1: Klik kolom komentar di TikTok Live untuk memunculkan keyboard
        val clicked = triggerCommentBar(rootNode)

        // TAHAP 2: Polling tunggu keyboard/input box muncul di layar (maksimal 2.5 detik)
        waitForKeyboardInputAndProceed(textToType, attempt = 0, maxAttempts = 5)
    }

    private fun waitForKeyboardInputAndProceed(textToType: String, attempt: Int, maxAttempts: Int) {
        if (!isRunning) {
            isTypingWorkflowRunning = false
            return
        }

        handler.postDelayed({
            val root = rootInActiveWindow
            val activeInput = if (root != null) findActiveTikTokInput(root) else null

            if (activeInput != null) {
                // KEYBOARD & INPUT MUNCUL! Lanjut ke TAHAP 3 & 4
                fillAndSend(activeInput, textToType)
            } else if (attempt < maxAttempts) {
                // Belum muncul, coba picu tap sekali lagi jika di attempt ke-2
                if (attempt == 2 && root != null) {
                    triggerCommentBar(root)
                }
                waitForKeyboardInputAndProceed(textToType, attempt + 1, maxAttempts)
            } else {
                // Fallback terakhir jika Accessibility terhalang: lakukan paste & trigger IME
                fallbackClipboardAndEnter(textToType)
            }
        }, 300)
    }

    private fun triggerCommentBar(root: AccessibilityNodeInfo): Boolean {
        // Prioritas 0: Jika user sudah melakukan "Kalibrasi 1x Sentuh", gunakan pixel pasti yang dikalibrasi
        if (calibratedChatX > 0 && calibratedChatY > 0) {
            simulateTap(calibratedChatX, calibratedChatY)
            return true
        }

        val allNodes = getAllNodes(root)
        val metrics = resources.displayMetrics
        val screenHeight = metrics.heightPixels
        val screenWidth = metrics.widthPixels

        // 1. Cari node trigger komentar TikTok (biasanya di bawah, teks "Katakan sesuatu", "Comment", "Tambah komentar", dsb)
        for (node in allNodes) {
            val pkg = node.packageName?.toString() ?: ""
            if (pkg.contains("autochat")) continue

            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            val isCommentText = text.contains("komentar") || text.contains("comment") ||
                    text.contains("katakan") || text.contains("say something") ||
                    desc.contains("komentar") || desc.contains("comment") ||
                    viewId.contains("comment") || viewId.contains("input") || viewId.contains("edit")

            if (isCommentText) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                // Pastikan berada di 25% area bawah layar (bottom bar live)
                if (rect.bottom > screenHeight * 0.75f && rect.width() > 0 && rect.height() > 0) {
                    if (performSafeClick(node)) return true
                }
            }
        }

        // 2. Jika tidak ada ID khusus, tap presisi area komentar default TikTok Live (Pojok Bawah Kiri: X: 15%, Y: 96%)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val tapX = screenWidth * 0.15f
            val tapY = screenHeight * 0.96f
            simulateTap(tapX, tapY)
            return true
        }

        return false
    }

    private fun findActiveTikTokInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val allNodes = getAllNodes(root)
        for (node in allNodes) {
            val pkg = node.packageName?.toString() ?: ""
            if (pkg.contains("autochat")) continue

            if (node.className == "android.widget.EditText") {
                return node
            }
        }
        return null
    }

    private fun fillAndSend(inputNode: AccessibilityNodeInfo, text: String) {
        // TAHAP 3: Isi Teks Komentar
        inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        inputNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard != null) {
            val clip = ClipData.newPlainText("AutoChat", text)
            clipboard.setPrimaryClip(clip)
        }

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val textSet = inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!textSet) {
            inputNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }

        val inputRect = Rect()
        inputNode.getBoundsInScreen(inputRect)

        // Berikan jeda stabil sebelum kirim (250ms) agar TikTok memvalidasi teks di input box
        handler.postDelayed({
            val root = rootInActiveWindow
            if (root != null) {
                dispatchSend(root, inputRect, inputNode, text)
            } else {
                dispatchImeSend(inputNode)
                notifySuccess(text)
                isTypingWorkflowRunning = false
            }
        }, 250)
    }

    private fun dispatchSend(
        root: AccessibilityNodeInfo,
        inputRect: Rect,
        inputNode: AccessibilityNodeInfo,
        text: String
    ) {
        // 0. Prioritas Utama jika sudah Kalibrasi 1x Sentuh untuk tombol Kirim
        if (calibratedSendX > 0 && calibratedSendY > 0) {
            simulateTap(calibratedSendX, calibratedSendY)
            dispatchImeSend(inputNode)
            notifySuccess(text)
            isTypingWorkflowRunning = false
            return
        }

        // 1. Coba klik tombol send TikTok resmi
        val knownSendIds = listOf(
            "com.zhiliaoapp.musically:id/btn_send",
            "com.zhiliaoapp.musically:id/send_btn",
            "com.zhiliaoapp.musically:id/iv_send",
            "com.zhiliaoapp.musically:id/live_send_btn",
            "com.zhiliaoapp.musically:id/live_btn_send",
            "com.zhiliaoapp.musically:id/live_comment_send",
            "com.zhiliaoapp.musically:id/send_icon",
            "com.zhiliaoapp.musically.go:id/send",
            "com.ss.android.ugc.trill:id/btn_send",
            "com.ss.android.ugc.trill:id/live_send_btn",
            "com.ss.android.ugc.trill:id/live_btn_send"
        )
        for (id in knownSendIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            for (node in nodes) {
                if (performSafeClick(node)) {
                    notifySuccess(text)
                    isTypingWorkflowRunning = false
                    return
                }
            }
        }

        // 2. Cari view yang berada di sebelah kanan kolom teks chat
        val allNodes = getAllNodes(root)
        if (inputRect.width() > 0) {
            for (node in allNodes) {
                val pkg = node.packageName?.toString() ?: ""
                if (pkg.contains("autochat")) continue

                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (rect.left >= inputRect.right - 20 &&
                    rect.centerY() >= inputRect.top - 60 &&
                    rect.centerY() <= inputRect.bottom + 60 &&
                    rect.width() > 0 && rect.height() > 0
                ) {
                    if (performSafeClick(node)) {
                        notifySuccess(text)
                        isTypingWorkflowRunning = false
                        return
                    }
                }
            }
        }

        // 3. Cari tombol kirim berdasarkan teks / deskripsi
        for (node in allNodes) {
            val pkg = node.packageName?.toString() ?: ""
            if (pkg.contains("autochat")) continue

            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val t = node.text?.toString()?.lowercase() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            if (desc.contains("kirim") || desc.contains("send") ||
                t.contains("kirim") || t.contains("send") ||
                viewId.contains("send") || viewId.contains("submit") || viewId.contains("enter")
            ) {
                if (performSafeClick(node)) {
                    notifySuccess(text)
                    isTypingWorkflowRunning = false
                    return
                }
            }
        }

        // 4. Trigger IME Enter keyboard
        dispatchImeSend(inputNode)

        // 5. Fallback Tap kanan kolom input
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && inputRect.width() > 0) {
            val tapX = (inputRect.right + 40).toFloat().coerceAtMost(resources.displayMetrics.widthPixels - 30f)
            val tapY = inputRect.centerY().toFloat()
            simulateTap(tapX, tapY)
        }

        notifySuccess(text)
        isTypingWorkflowRunning = false
    }

    private fun dispatchImeSend(inputNode: AccessibilityNodeInfo) {
        inputNode.performAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            inputNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        }
    }

    private fun fallbackClipboardAndEnter(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard != null) {
            val clip = ClipData.newPlainText("AutoChat", text)
            clipboard.setPrimaryClip(clip)
        }

        val metrics = resources.displayMetrics
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Tap tombol enter di keyboard (kanan bawah)
            val tapEnterX = metrics.widthPixels * 0.90f
            val tapEnterY = metrics.heightPixels * 0.92f
            simulateTap(tapEnterX, tapEnterY)
        }

        notifySuccess(text)
        isTypingWorkflowRunning = false
    }

    private fun notifySuccess(sentText: String = "") {
        sentCount++
        handler.post {
            onChatSentListener?.invoke(sentCount, sentText)
        }
    }

    private fun performSafeClick(node: AccessibilityNodeInfo): Boolean {
        var curr: AccessibilityNodeInfo? = node
        while (curr != null) {
            if (curr.isClickable) {
                return curr.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            curr = curr.parent
        }

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
        stopAutoChat()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoChat()
        instance = null
    }
}
