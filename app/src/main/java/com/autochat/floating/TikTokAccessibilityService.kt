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

class TikTokAccessibilityService : AccessibilityService() {

    companion object {
        var instance: TikTokAccessibilityService? = null
        fun isServiceRunning(): Boolean = instance != null
    }

    private var isRunning = false
    private var activeMessages: ArrayList<String> = arrayListOf()
    private var currentMessageIndex = 0
    private var delaySeconds: Long = 4
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Toast.makeText(this, "Aksesibilitas AutoChat Terhubung! Siap digunakan.", Toast.LENGTH_SHORT).show()
    }

    fun startAutoChat(messages: ArrayList<String>, delay: Long) {
        if (messages.isEmpty()) return
        activeMessages = messages
        delaySeconds = delay
        currentMessageIndex = 0
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

            val messageToSend = activeMessages[currentMessageIndex % activeMessages.size]
            currentMessageIndex++

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

        // Cek apakah kolom input komentar EditText sudah terbuka
        val editTexts = findNodesByClassName(rootNode, "android.widget.EditText")
        if (editTexts.isNotEmpty()) {
            val targetInput = editTexts.last()
            typeAndSend(targetInput, textToType)
            return
        }

        // Jika kolom komentar belum terbuka di TikTok, klik pemicunya
        val clicked = clickCommentTrigger(rootNode)
        if (clicked) {
            handler.postDelayed({
                val updatedRoot = rootInActiveWindow ?: return@postDelayed
                val newInputs = findNodesByClassName(updatedRoot, "android.widget.EditText")
                if (newInputs.isNotEmpty()) {
                    typeAndSend(newInputs.last(), textToType)
                } else {
                    // Fallback: klik area bawah tengah lalu ketik
                    fallbackTapBottomInput(textToType)
                }
            }, 600)
        } else {
            // Fallback gesture tap jika UI TikTok menggunakan custom canvas/textureview
            fallbackTapBottomInput(textToType)
        }
    }

    private fun typeAndSend(inputNode: AccessibilityNodeInfo, text: String) {
        inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        inputNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        // 1. Salin ke clipboard sistem untuk paste instan & andal
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard != null) {
            val clip = ClipData.newPlainText("AutoChat", text)
            clipboard.setPrimaryClip(clip)
        }

        // 2. Set teks langsung via Accessibility Action
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val textSet = inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

        if (!textSet) {
            inputNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }

        // Beri jeda 400ms agar TikTok mengaktifkan tombol send, lalu klik Kirim
        handler.postDelayed({
            val root = rootInActiveWindow ?: return@postDelayed
            sendComment(root)
        }, 400)
    }

    private fun sendComment(root: AccessibilityNodeInfo) {
        // 1. Cari view dengan resource-id khas TikTok
        val knownSendIds = listOf(
            "com.zhiliaoapp.musically:id/btn_send",
            "com.zhiliaoapp.musically:id/send_btn",
            "com.zhiliaoapp.musically:id/iv_send",
            "com.zhiliaoapp.musically.go:id/send",
            "com.ss.android.ugc.trill:id/btn_send"
        )
        for (id in knownSendIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            for (node in nodes) {
                if (performSafeClick(node)) return
            }
        }

        // 2. Cari berdasarkan Content Description atau Text "send", "kirim"
        val allNodes = getAllNodes(root)
        for (node in allNodes) {
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val text = node.text?.toString()?.lowercase() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            if (desc.contains("kirim") || desc.contains("send") ||
                text.contains("kirim") || text.contains("send") ||
                viewId.contains("send") || viewId.contains("submit")) {
                if (performSafeClick(node)) return
            }
        }

        // 3. Fallback: Tap posisi tombol Send di samping kanan input (sekitar 92% width)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val metrics = resources.displayMetrics
            val x = metrics.widthPixels * 0.92f
            val y = metrics.heightPixels * 0.58f // Area atas keyboard TikTok
            simulateTap(x, y)
        }
    }

    private fun clickCommentTrigger(root: AccessibilityNodeInfo): Boolean {
        val triggers = listOf(
            "Tambahkan komentar...", "Tambahkan komentar",
            "Add comment...", "Add comment",
            "Say something...", "Say something",
            "Kirim komentar", "Komentar", "Chat"
        )

        for (triggerText in triggers) {
            val nodes = root.findAccessibilityNodeInfosByText(triggerText)
            for (node in nodes) {
                if (performSafeClick(node)) return true
            }
        }

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

    private fun fallbackTapBottomInput(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val metrics = resources.displayMetrics
            // Tap area bar komentar live TikTok di bawah kiri (25% X, 94% Y)
            val tapX = metrics.widthPixels * 0.25f
            val tapY = metrics.heightPixels * 0.94f
            simulateTap(tapX, tapY)

            handler.postDelayed({
                val updatedRoot = rootInActiveWindow ?: return@postDelayed
                val inputs = findNodesByClassName(updatedRoot, "android.widget.EditText")
                if (inputs.isNotEmpty()) {
                    typeAndSend(inputs.last(), text)
                }
            }, 600)
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
        stopAutoChat()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoChat()
        instance = null
    }
}
