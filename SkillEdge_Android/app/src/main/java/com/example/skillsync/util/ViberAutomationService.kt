package com.example.skillsync.util

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.skillsync.data.SessionManager
import com.example.skillsync.data.cache.ViberOutboxItem
import com.example.skillsync.data.cache.ViberOutboxStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Android Accessibility Service for on-device UI automation of the Viber app (com.viber.voip).
 * Automates text injection into Viber chat and clicks send.
 */
class ViberAutomationService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.i(TAG, "ViberAutomationService connected and ready")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName != VIBER_PACKAGE) return
        val item = currentItem ?: return
        val managerEmail = SessionManager.getEmail().orEmpty()
        if (managerEmail.isBlank()) return

        val root = rootInActiveWindow ?: return

        // Search for message input field
        val textNodes = mutableListOf<AccessibilityNodeInfo>()
        findInputFields(root, textNodes)

        if (textNodes.isNotEmpty()) {
            val inputNode = textNodes.first()
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, item.messageText)
            }
            val setTextOk = inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

            if (setTextOk) {
                // Find send button and click
                serviceScope.launch {
                    delay(300)
                    val sendNodes = mutableListOf<AccessibilityNodeInfo>()
                    findSendButtons(rootInActiveWindow ?: root, sendNodes)
                    if (sendNodes.isNotEmpty()) {
                        val clicked = sendNodes.first().performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (clicked) {
                            Log.i(TAG, "Auto-sent Viber message ${item.id} to ${item.recipientName}")
                            ViberOutboxStore.markStatus(managerEmail, item.id, ViberOutboxItem.STATUS_SENT)
                            currentItem = null
                            delay(500)
                            performGlobalAction(GLOBAL_ACTION_BACK)
                        }
                    }
                }
            }
        }
    }

    private fun findInputFields(node: AccessibilityNodeInfo?, results: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        val className = node.className?.toString().orEmpty()
        val viewId = node.viewIdResourceName.orEmpty()
        if (className.contains("EditText", ignoreCase = true) || viewId.contains("message_text", ignoreCase = true) || viewId.contains("input", ignoreCase = true)) {
            if (node.isEditable) {
                results.add(node)
                return
            }
        }
        for (i in 0 until node.childCount) {
            findInputFields(node.getChild(i), results)
        }
    }

    private fun findSendButtons(node: AccessibilityNodeInfo?, results: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        val viewId = node.viewIdResourceName.orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        if (viewId.contains("send_btn", ignoreCase = true) || viewId.contains("send_button", ignoreCase = true) || desc.contains("Send", ignoreCase = true)) {
            if (node.isClickable) {
                results.add(node)
                return
            }
        }
        for (i in 0 until node.childCount) {
            findSendButtons(node.getChild(i), results)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "ViberAutomationService interrupted")
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ViberAutomationService"
        const val VIBER_PACKAGE = "com.viber.voip"

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var currentItem: ViberOutboxItem? = null
    }
}
