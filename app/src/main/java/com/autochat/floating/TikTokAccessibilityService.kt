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
    private val handler = Handler(Looper.getMainLooper())

    private val safeVariations = listOf("✨", "🔥", "⚡", "👍", "🛍️", "✓", "💯", "🙌", "😊")

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Toast.makeText(this, "Aksesibilitas AutoChat Terhubung! Siap digunakan.", Toast.LENGTH_SHORT).show()
    }

    fun startAutoChat(messages: ArrayList<String>, delay: Long, antiSpam: Boolean = true) {
        if (messages.isEmpty()) return
        activeMessages = messages
        delaySeconds = delay
        enableAntiSpam = antiSpam
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

        // 1. Filter: Cari EditText yang HANYA milik TikTok (Bukan milik widget AutoChat sendiri)
        val tikTokEditTexts = findTikTokInputs(rootNode)
        if (tikTokEditTexts.isNotEmpty()) {
            val targetInput = tikTokEditTexts.last()
            typeAndSend(targetInput, textToType)
            return
        }

        // 2. Jika kolom komentar TikTok belum terbuka, buka dengan klik bar komentar TikTok
        val clicked = clickCommentTrigger(rootNode)
        if (clicked) {
            handler.postDelayed({
                val updatedRoot = rootInActiveWindow ?: return@postDelayed
                val newInputs = findTikTokInputs(updatedRoot)
                if (newInputs.isNotEmpty()) {
                    typeAndSend(newInputs.last(), textToType)
                } else {
                    fallbackTapBottomInput(textToType)
                }
            }, 550)
        } else {
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

        handler.postDelayed({
            val root = rootInActiveWindow ?: return@postDelayed
            sendComment(root)
        }, 350)
    }

    private fun sendComment(root: AccessibilityNodeInfo) {
        val knownSendIds = listOf(
            "com.zhiliaoapp.musically:id/btn_send",
            "com.zhiliaoapp.musically:id/send_btn",
            "com.zhiliaoapp.musically:id/iv_send",
            "com.zhiliaoapp.musically:id/live_send_btn",
            "com.zhiliaoapp.musically:id/live_btn_send",
            "com.zhiliaoapp.musically:id/live_comment_send",
            "com.zhiliaoapp.musically.go:id/send",
            "com.ss.android.ugc.trill:id/btn_send",
            "com.ss.android.ugc.trill:id/live_send_btn"
        )
        for (id in knownSendIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            for (node in nodes) {
                if (performSafeClick(node)) return
            }
        }

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
                if (performSafeClick(node)) return
            }
        }

        // Fallback Gesture Tap: Area tombol kirim di atas keyboard (kanan bawah)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val metrics = resources.displayMetrics
            val x = metrics.widthPixels * 0.92f
            val y = metrics.heightPixels * 0.58f
            simulateTap(x, y)
        }
    }

    private fun clickCommentTrigger(root: AccessibilityNodeInfo): Boolean {
        // Prioritaskan teks pemicu chat Live TikTok
        val liveTriggers = listOf(
            "Tambahkan komentar...", "Tambahkan komentar",
            "Say something...", "Say something",
            "Katakan sesuatu...", "Katakan sesuatu",
            "Send a comment...", "Send a comment",
            "Chat...", "Chat", "Obrolan...", "Obrolan"
        )

        for (triggerText in liveTriggers) {
            val nodes = root.findAccessibilityNodeInfosByText(triggerText)
            for (node in nodes) {
                val pkg = node.packageName?.toString() ?: ""
                if (!pkg.contains("autochat") && performSafeClick(node)) return true
            }
        }

        val allNodes = getAllNodes(root)
        // 1. Scan berdasarkan id atau desc yang mengandung live chat
        for (node in allNodes) {
            val pkg = node.packageName?.toString() ?: ""
            if (pkg.contains("autochat")) continue

            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val idName = node.viewIdResourceName?.lowercase() ?: ""
            val nodeText = node.text?.toString()?.lowercase() ?: ""

            // JANGAN klik tombol komentar video reguler jika ada alternatif live
            if (idName.contains("live_chat") || idName.contains("comment_et") ||
                idName.contains("et_comment") || idName.contains("input_box") ||
                desc.contains("say something") || desc.contains("katakan sesuatu") ||
                nodeText.contains("say something") || nodeText.contains("katakan sesuatu") ||
                desc.contains("obrolan") || desc.contains("tambahkan komentar")) {
                if (performSafeClick(node)) return true
            }
        }

        // 2. Scan fallback jika belum ketemu
        for (node in allNodes) {
            val pkg = node.packageName?.toString() ?: ""
            if (pkg.contains("autochat")) continue

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
            // Tap area bawah layar kiri tempat input bar TikTok Live biasanya berada
            val tapX = metrics.widthPixels * 0.28f
            val tapY = metrics.heightPixels * 0.955f
            simulateTap(tapX, tapY)

            handler.postDelayed({
                val updatedRoot = rootInActiveWindow ?: return@postDelayed
                val inputs = findTikTokInputs(updatedRoot)
                if (inputs.isNotEmpty()) {
                    typeAndSend(inputs.last(), text)
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
