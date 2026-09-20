package com.autochat.floating

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
                    activeMessages = if (!receivedList.isNullOrEmpty()) receivedList else arrayListOf("Halo!")
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
        Toast.makeText(this, "Auto Chat Dimulai (${activeMessages.size} pesan aktif)", Toast.LENGTH_SHORT).show()
        handler.post(actionRunnable)
    }

    private fun stopAutomationLoop() {
        isRunning = false
        handler.removeCallbacks(actionRunnable)
    }

    private val actionRunnable = object : Runnable {
        override fun run() {
            if (!isRunning || activeMessages.isEmpty()) return

            // Ambil pesan bergiliran (Round-Robin) dari preset yang di-ON-kan
            val messageToSend = activeMessages[currentMessageIndex % activeMessages.size]
            currentMessageIndex++

            performTikTokComment(messageToSend)

            // Jadwalkan pengiriman berikutnya sesuai delay detik
            handler.postDelayed(this, delaySeconds * 1000)
        }
    }

    /**
     * Algoritma Pencarian Elemen & Pengetikan di TikTok Live
     */
    private fun performTikTokComment(textToType: String) {
        val rootNode = rootInActiveWindow ?: return

        // 1. Cari EditText chat
        val editTexts = rootNode.findAccessibilityNodeInfosByViewId("com.zhiliaoapp.musically:id/et_comment")
        val targetInputs = if (editTexts.isEmpty()) {
            findNodesByClassName(rootNode, "android.widget.EditText")
        } else editTexts

        if (targetInputs.isNotEmpty()) {
            val targetInput = targetInputs[0]

            // Berikan fokus
            targetInput.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

            // Masukkan teks pesan ke input
            val arguments = Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
            targetInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

            // Jeda 300ms lalu klik tombol Kirim
            handler.postDelayed({
                val updatedRoot = rootInActiveWindow ?: return@postDelayed
                val sendButtons = updatedRoot.findAccessibilityNodeInfosByViewId("com.zhiliaoapp.musically:id/btn_send")
                if (sendButtons.isNotEmpty()) {
                    sendButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } else {
                    clickSendButtonFallback(updatedRoot)
                }
            }, 300)
        } else {
            // Jika kolom komentar belum terbuka, klik placeholder untuk membukanya
            clickCommentPlaceholder(rootNode)
        }
    }

    private fun clickCommentPlaceholder(root: AccessibilityNodeInfo) {
        val placeholders = listOf("Tambahkan komentar", "Add comment", "Say something", "Komentar")
        for (text in placeholders) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            for (node in nodes) {
                if (node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return
                } else if (node.parent?.isClickable == true) {
                    node.parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return
                }
            }
        }
    }

    private fun clickSendButtonFallback(root: AccessibilityNodeInfo) {
        val buttons = findNodesByClassName(root, "android.widget.ImageView") +
                      findNodesByClassName(root, "android.widget.Button")
        for (btn in buttons) {
            val desc = btn.contentDescription?.toString()?.lowercase() ?: ""
            if (desc.contains("send") || desc.contains("kirim")) {
                btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
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
