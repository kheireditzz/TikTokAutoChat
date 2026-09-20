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

    // Koordinat Manual (dalam persentase layar 0.0 - 1.0)
    private var coordInputXPercent = 0.25f
    private var coordInputYPercent = 0.96f
    private var coordSendXPercent = 0.92f
    private var coordSendYPercent = 0.94f

    private val safeVariations = listOf("✨", "🔥", "⚡", "👍", "🛍️", "✓", "💯", "🙌", "😊")

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Toast.makeText(this, "Aksesibilitas AutoChat Terhubung! Siap digunakan.", Toast.LENGTH_SHORT).show()
    }

    fun setOnChatSentListener(listener: ((Int, String) -> Unit)?) {
        onChatSentListener = listener
    }

    fun setManualCoordinates(inputX: Float, inputY: Float, sendX: Float, sendY: Float) {
        coordInputXPercent = inputX
        coordInputYPercent = inputY
        coordSendXPercent = sendX
        coordSendYPercent = sendY
    }

    fun startAutoChat(
        messages: ArrayList<String>,
        delay: Long,
        antiSpam: Boolean = true,
        inputX: Float = 0.25f,
        inputY: Float = 0.96f,
        sendX: Float = 0.92f,
        sendY: Float = 0.94f
    ) {
        if (messages.isEmpty()) return
        activeMessages = messages
        delaySeconds = delay
        enableAntiSpam = antiSpam
        coordInputXPercent = inputX
        coordInputYPercent = inputY
        coordSendXPercent = sendX
        coordSendYPercent = sendY
        currentMessageIndex = 0
        sentCount = 0
        isRunning = true
        handler.removeCallbacks(actionRunnable)
        handler.post(actionRunnable)
    }

    fun stopAutoChat() {
        isRunning = false
        handler.removeCallbacks(actionRunnable)
    }

    private val actionRunnable = object : Runnable {
        override fun run() {
            if (!isRunning || activeMessages.isEmpty()) return

            var messageToSend = activeMessages[currentMessageIndex % activeMessages.size]
            currentMessageIndex++

            // Proteksi Anti-Spam: Beri variasi halus jika diaktifkan
            if (enableAntiSpam) {
                val suffix = safeVariations[Random.nextInt(safeVariations.size)]
                messageToSend = "$messageToSend $suffix"
            }

            try {
                executeCommentWorkflow(messageToSend)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            handler.postDelayed(this, delaySeconds * 1000)
        }
    }

    private fun executeCommentWorkflow(textToType: String) {
        val rootNode = rootInActiveWindow ?: return

        // Perintah User: "KETIKA MASUK KE LIVE UNTUK MAU KITA KOMEN TATALETAK KOMEN ADA DI BAWAH KIRI KLIK ITU DULU"
        // Selalu prioritaskan klik bar komentar di pojok bawah kiri terlebih dahulu!
        val clicked = clickCommentTrigger(rootNode)

        if (clicked) {
            handler.postDelayed({
                val updatedRoot = rootInActiveWindow ?: return@postDelayed
                val inputs = findTikTokInputs(updatedRoot)
                if (inputs.isNotEmpty()) {
                    typeAndSend(inputs.last(), textToType)
                } else {
                    fallbackTapBottomInput(textToType)
                }
            }, 350)
        } else {
            // Jika trigger belum tertekan, langsung fallback tap presisi di pojok bawah kiri layar
            fallbackTapBottomInput(textToType)
        }
    }

    private fun findTikTokInputs(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val allNodes = getAllNodes(root)
        for (node in allNodes) {
            val pkg = node.packageName?.toString() ?: ""
            if (node.className == "android.widget.EditText" && !pkg.contains("autochat")) {
                result.add(node)
            }
        }
        return result
    }

    private fun typeAndSend(inputNode: AccessibilityNodeInfo, text: String) {
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

        // Ambil posisi persis kolom input di layar
        val inputRect = Rect()
        inputNode.getBoundsInScreen(inputRect)

        // Coba kirim via IME Action (Enter / Send pada software keyboard)
        inputNode.performAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            inputNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        }

        handler.postDelayed({
            val root = rootInActiveWindow ?: return@postDelayed
            sendComment(root, inputRect, text)
        }, 300)
    }

    private fun notifySuccess(sentText: String = "") {
        sentCount++
        handler.post {
            onChatSentListener?.invoke(sentCount, sentText)
        }
    }

    private fun sendComment(root: AccessibilityNodeInfo, inputRect: Rect? = null, sentText: String = "") {
        // 1. Cek tombol send berdasarkan resource ID TikTok
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
                    notifySuccess()
                    return
                }
            }
        }

        // 2. Cek tombol atau icon yang sejajar / di sebelah kanan kolom chat (inputRect)
        if (inputRect != null && inputRect.width() > 0) {
            val allNodes = getAllNodes(root)
            for (node in allNodes) {
                val pkg = node.packageName?.toString() ?: ""
                if (pkg.contains("autochat")) continue

                val rect = Rect()
                node.getBoundsInScreen(rect)
                // Jika node berada di sebelah kanan input box dan memiliki ketinggian vertikal sejajar
                if (rect.left >= inputRect.right - 20 &&
                    rect.centerY() >= inputRect.top - 50 &&
                    rect.centerY() <= inputRect.bottom + 50 &&
                    rect.width() > 0 && rect.height() > 0) {
                    if (performSafeClick(node)) {
                        notifySuccess()
                        return
                    }
                }
            }
        }

        // 3. Scan node berdasarkan deskripsi teks pengiriman
        val allNodes = getAllNodes(root)
        for (node in allNodes) {
            val pkg = node.packageName?.toString() ?: ""
            if (pkg.contains("autochat")) continue

            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val text = node.text?.toString()?.lowercase() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            if (desc.contains("kirim") || desc.contains("send") ||
                text.contains("kirim") || text.contains("send") ||
                viewId.contains("send") || viewId.contains("submit") || viewId.contains("enter")) {
                if (performSafeClick(node)) {
                    notifySuccess()
                    return
                }
            }
        }

        // 4. Fallback Tap Layar:
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val metrics = resources.displayMetrics

            // Posisi A: Tombol kirim persis di titik yang ditentukan pengguna
            val sendTapX = metrics.widthPixels * (coordSendXPercent / 100f)
            val sendTapY = metrics.heightPixels * (coordSendYPercent / 100f)
            simulateTap(sendTapX, sendTapY)

            // Posisi B: Sekaligus jalankan IME enter
            handler.postDelayed({
                simulateTap(sendTapX, sendTapY)
            }, 180)

            notifySuccess()
        }
    }

    private fun clickCommentTrigger(root: AccessibilityNodeInfo): Boolean {
        // PERINTAH USER: Ketuk tepat di koordinat yang sudah digeser dan ditentukan oleh pengguna!
        tapUserCommentTarget()
        return true
    }

    private fun tapUserCommentTarget() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val metrics = resources.displayMetrics
            // Ketuk persis 100% di titik pusat target merah yang ditentukan oleh pengguna
            val tapX = metrics.widthPixels * (coordInputXPercent / 100f)
            val tapY = metrics.heightPixels * (coordInputYPercent / 100f)
            simulateTap(tapX, tapY)
        }
    }

    private fun fallbackTapBottomInput(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            tapUserCommentTarget()

            handler.postDelayed({
                val updatedRoot = rootInActiveWindow ?: return@postDelayed
                val inputs = findTikTokInputs(updatedRoot)
                if (inputs.isNotEmpty()) {
                    typeAndSend(inputs.last(), text)
                } else {
                    // Jika elemen accessibility tidak muncul, paste clipboard langsung lalu ketuk tombol kirim
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    if (clipboard != null) {
                        val clip = ClipData.newPlainText("AutoChat", text)
                        clipboard.setPrimaryClip(clip)
                    }
                    val metrics = resources.displayMetrics
                    val sendTapX = metrics.widthPixels * (coordSendXPercent / 100f)
                    val sendTapY = metrics.heightPixels * (coordSendYPercent / 100f)
                    handler.postDelayed({
                        simulateTap(sendTapX, sendTapY)
                        notifySuccess()
                    }, 400)
                }
            }, 550)
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
