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
    private var maxMessageTarget: Int = 0 // 0 = Unlimited
    private var enableAntiSpam = true
    private var sentCount = 0
    private var onChatSentListener: ((Int, String) -> Unit)? = null
    private var onTargetReachedListener: ((Int) -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isTypingWorkflowRunning = false

    // Kalibrasi Koordinat (Disimpan dalam pixel layar user)
    private var calibratedChatX: Float = -1f
    private var calibratedChatY: Float = -1f
    private var calibratedSendX: Float = -1f
    private var calibratedSendY: Float = -1f

    // 200+ Koleksi Emoji Bervariasi & Anti-Spam
    private val safeVariations = listOf(
        "✨", "🔥", "⚡", "👍", "🛍️", "✓", "💯", "🙌", "😊", "🚀",
        "❤️", "🌟", "💥", "🎉", "🥳", "😍", "👏", "🎁", "💎", "🏆",
        "⭐", "🤩", "😎", "💫", "💖", "🌈", "☕", "🎯", "🥇", "🎈",
        "🛒", "🏷️", "💸", "👌", "🔥", "🤝", "🥳", "🌸", "💐", "🌺",
        "🌻", "🍀", "🍫", "🍰", "🍩", "🍹", "🥤", "🍿", "🎶", "🎵",
        "🎤", "🎧", "🎬", "🎨", "🎮", "🕹️", "⚡", "🔮", "🧿", "🪄",
        "🤍", "🤎", "💜", "💙", "💚", "💛", "🧡", "❣️", "💕", "💞",
        "💓", "💗", "💘", "💝", "💟", "💌", "💐", "🌷", "🌹", "🥀",
        "🌺", "🌸", "🌼", "🌻", "🌞", "🌝", "🌛", "⭐", "🌟", "💫",
        "✨", "☄️", "💥", "🔥", "🌈", "☀️", "🌤️", "⛅", "🌦️", "☁️",
        "🛍️", "🛒", "🏷️", "🪙", "💰", "💳", "💎", "💍", "👑", "🧢",
        "👕", "👗", "👠", "👟", "👜", "🎒", "👓", "🕶️", "🥽", "🎁",
        "🎯", "🎲", "🧩", "🎳", "🎸", "🎷", "🎺", "🥁", "🎹", "🪗",
        "🏆", "🥇", "🥈", "🥉", "🏅", "🎖️", "🎗️", "🎟️", "🎫", "🎪",
        "🚀", "🛸", "🚁", "⛵", "🚤", "🏎️", "🏍️", "🛵", "🚲", "🛴",
        "💯", "✅", "✔️", "☑️", "🔝", "🆒", "🆓", "🆕", "🆙", "🆗",
        "😍", "🥰", "😘", "😗", "😚", "😋", "😛", "😜", "🤪", "😝",
        "🤗", "🤭", "🤫", "🤔", "🤐", "🤨", "😐", "😑", "😶", "😏",
        "😁", "😄", "😃", "😀", "😆", "🥹", "😅", "😂", "🤣", "☺️"
    )

    // Invisible Zero-Width Characters untuk membedakan hash teks pesan di sistem TikTok
    private val zeroWidthChars = listOf("\u200B", "\u200C", "\u200D", "\uFEFF")

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

    fun resetToDefaultCoordinates() {
        calibratedChatX = -1f
        calibratedChatY = -1f
        calibratedSendX = -1f
        calibratedSendY = -1f
        val prefs = getSharedPreferences("AutoChatPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("calibrated_chat_x")
            .remove("calibrated_chat_y")
            .remove("calibrated_send_x")
            .remove("calibrated_send_y")
            .apply()
    }

    fun setOnChatSentListener(listener: ((Int, String) -> Unit)?) {
        onChatSentListener = listener
    }

    fun setOnTargetReachedListener(listener: ((Int) -> Unit)?) {
        onTargetReachedListener = listener
    }

    fun startAutoChat(
        messages: ArrayList<String>,
        delay: Long,
        antiSpam: Boolean = true,
        targetLimit: Int = 0
    ) {
        if (messages.isEmpty()) return
        activeMessages = ArrayList(messages.filter { it.isNotBlank() })
        if (activeMessages.isEmpty()) return

        delaySeconds = delay.coerceAtLeast(1)
        enableAntiSpam = antiSpam
        maxMessageTarget = targetLimit
        currentMessageIndex = 0
        sentCount = 0
        isRunning = true
        isTypingWorkflowRunning = false

        // Batalkan callback lama dan mulai eksekusi segar
        handler.removeCallbacksAndMessages(null)
        handler.post(actionRunnable)
    }

    /**
     * Stop AutoChat secara total & instan.
     * Membersihkan seluruh handler, membatalkan antrean ketik, dan memastikan nol lag.
     */
    fun stopAutoChat() {
        isRunning = false
        isTypingWorkflowRunning = false
        handler.removeCallbacksAndMessages(null)
    }

    private val actionRunnable = object : Runnable {
        override fun run() {
            if (!isRunning || activeMessages.isEmpty()) return

            if (isTypingWorkflowRunning) {
                // Tunggu jika siklus sebelumnya masih berlangsung (maksimal 800ms)
                handler.postDelayed(this, 800)
                return
            }

            var messageToSend = activeMessages[currentMessageIndex % activeMessages.size]
            currentMessageIndex++

            if (enableAntiSpam) {
                val emoji1 = safeVariations[Random.nextInt(safeVariations.size)]
                val emoji2 = safeVariations[Random.nextInt(safeVariations.size)]
                val invisible = zeroWidthChars[Random.nextInt(zeroWidthChars.size)]
                messageToSend = "$messageToSend $invisible$emoji1 $emoji2"
            }

            isTypingWorkflowRunning = true
            try {
                executeSmartKeyboardWorkflow(messageToSend)
            } catch (e: Exception) {
                e.printStackTrace()
                isTypingWorkflowRunning = false
            }

            // Tambahkan jitter acak kecil (+0-500ms) agar waktu pengiriman natural
            val jitter = if (enableAntiSpam) Random.nextLong(0, 500) else 0L
            handler.postDelayed(this, (delaySeconds * 1000) + jitter)
        }
    }

    /**
     * WORKFLOW PENGIRIMAN CERDAS & FLEKSIBEL:
     * 1. Cek apakah ada input chat yang sudah aktif di layar.
     * 2. Jika belum, picu tombol chat (baik via koordinat kalibrasi atau deteksi node).
     * 3. Tunggu keyboard / input muncul (polling ringan 300ms).
     * 4. Isi teks ke input dan kirimkan via tombol kirim.
     */
    private fun executeSmartKeyboardWorkflow(textToType: String) {
        if (!isRunning) {
            isTypingWorkflowRunning = false
            return
        }

        val rootNode = rootInActiveWindow

        // Cek jika kolom komentar sudah terbuka di layar
        if (rootNode != null) {
            val existingInput = findActiveTikTokInput(rootNode)
            if (existingInput != null) {
                fillAndSend(existingInput, textToType)
                return
            }
        }

        // Picu klik kolom komentar
        triggerCommentBar(rootNode)

        // Tunggu sampai input/keyboard aktif muncul
        waitForKeyboardInputAndProceed(textToType, attempt = 0, maxAttempts = 5)
    }

    private fun waitForKeyboardInputAndProceed(textToType: String, attempt: Int, maxAttempts: Int) {
        if (!isRunning) {
            isTypingWorkflowRunning = false
            return
        }

        handler.postDelayed({
            if (!isRunning) {
                isTypingWorkflowRunning = false
                return@postDelayed
            }

            val root = rootInActiveWindow
            val activeInput = if (root != null) findActiveTikTokInput(root) else null

            if (activeInput != null) {
                fillAndSend(activeInput, textToType)
            } else if (attempt < maxAttempts) {
                if (attempt == 2) {
                    triggerCommentBar(root)
                }
                waitForKeyboardInputAndProceed(textToType, attempt + 1, maxAttempts)
            } else {
                // Fallback jika tidak menemukan node input
                fallbackClipboardAndEnter(textToType)
            }
        }, 300)
    }

    private fun triggerCommentBar(root: AccessibilityNodeInfo?): Boolean {
        if (!isRunning) return false

        // Prioritas 1: Jika sudah dikalibrasi posisinya, tap titik kolom komentar
        if (calibratedChatX > 0 && calibratedChatY > 0) {
            simulateTap(calibratedChatX, calibratedChatY)
            return true
        }

        if (root == null) {
            // Fallback koordinat default jika belum ada kalibrasi (15% kiri, 96% bawah)
            val metrics = resources.displayMetrics
            simulateTap(metrics.widthPixels * 0.15f, metrics.heightPixels * 0.96f)
            return true
        }

        val allNodes = getAllNodes(root)
        val metrics = resources.displayMetrics
        val screenHeight = metrics.heightPixels

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
                if (rect.bottom > screenHeight * 0.70f && rect.width() > 0 && rect.height() > 0) {
                    if (performSafeClick(node)) return true
                }
            }
        }

        // Fallback default
        simulateTap(metrics.widthPixels * 0.15f, metrics.heightPixels * 0.96f)
        return true
    }

    private fun findActiveTikTokInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val allNodes = getAllNodes(root)
        for (node in allNodes) {
            val pkg = node.packageName?.toString() ?: ""
            if (pkg.contains("autochat")) continue

            if (node.className?.toString()?.contains("EditText") == true ||
                node.isEditable ||
                node.isFocused
            ) {
                return node
            }
        }
        return null
    }

    private fun fillAndSend(inputNode: AccessibilityNodeInfo, text: String) {
        if (!isRunning) {
            isTypingWorkflowRunning = false
            return
        }

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

        // Jeda stabil sebelum kirim (220ms) agar UI memvalidasi teks
        handler.postDelayed({
            if (!isRunning) {
                isTypingWorkflowRunning = false
                return@postDelayed
            }

            val root = rootInActiveWindow
            if (root != null) {
                dispatchSend(root, inputRect, inputNode, text)
            } else {
                dispatchImeSend(inputNode)
                notifySuccess(text)
                isTypingWorkflowRunning = false
            }
        }, 220)
    }

    private fun dispatchSend(
        root: AccessibilityNodeInfo,
        inputRect: Rect,
        inputNode: AccessibilityNodeInfo,
        text: String
    ) {
        if (!isRunning) {
            isTypingWorkflowRunning = false
            return
        }

        // Prioritas Utama: Koordinat Kalibrasi Tombol Kirim
        if (calibratedSendX > 0 && calibratedSendY > 0) {
            simulateTap(calibratedSendX, calibratedSendY)
            dispatchImeSend(inputNode)
            notifySuccess(text)
            isTypingWorkflowRunning = false
            return
        }

        // Cari ID resmi tombol send TikTok
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

        // Cari tombol kirim berdasarkan teks / deskripsi
        val allNodes = getAllNodes(root)
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

        // Trigger IME Enter keyboard
        dispatchImeSend(inputNode)

        // Fallback Tap kanan kolom input
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
        if (!isRunning) {
            isTypingWorkflowRunning = false
            return
        }

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard != null) {
            val clip = ClipData.newPlainText("AutoChat", text)
            clipboard.setPrimaryClip(clip)
        }

        if (calibratedSendX > 0 && calibratedSendY > 0) {
            simulateTap(calibratedSendX, calibratedSendY)
        } else {
            val metrics = resources.displayMetrics
            simulateTap(metrics.widthPixels * 0.90f, metrics.heightPixels * 0.92f)
        }

        notifySuccess(text)
        isTypingWorkflowRunning = false
    }

    private fun notifySuccess(sentText: String = "") {
        sentCount++
        handler.post {
            onChatSentListener?.invoke(sentCount, sentText)

            // Cek apakah target batas kirim tercapai
            if (maxMessageTarget > 0 && sentCount >= maxMessageTarget) {
                stopAutoChat()
                onTargetReachedListener?.invoke(sentCount)
                Toast.makeText(
                    this@TikTokAccessibilityService,
                    "Target $maxMessageTarget pesan telah tercapai! AutoChat berhenti otomatis. ✓",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun testTapSingleCoordinate(x: Float, y: Float) {
        simulateTap(x, y)
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
                .addStroke(GestureDescription.StrokeDescription(path, 0, 70))
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
