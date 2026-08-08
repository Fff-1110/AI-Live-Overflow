package com.ailiveoverflow

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Base64
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AppObserverService : AccessibilityService() {

    private var lastScreenScan = 0L
    private var lastScreenText = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg.isEmpty() || pkg == "com.ailiveoverflow") return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            OverlayService.onForegroundAppChanged(pkg)
        }

        // 屏幕文字感知：3秒节流，避免频繁遍历节点树
        val now = System.currentTimeMillis()
        if (now - lastScreenScan < 3000) return
        lastScreenScan = now
        try {
            val root = rootInActiveWindow ?: return
            val sb = StringBuilder()
            collectText(root, sb, 0)
            val text = sb.toString().trim()
            if (text.isNotEmpty() && text != lastScreenText) {
                lastScreenText = text
                val b64 = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                OverlayService.onScreenText(b64)
            }
        } catch (e: Exception) {
        }
    }

    private fun collectText(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (node == null || sb.length > 400 || depth > 12) return
        try {
            val t = node.text?.toString()
            if (!t.isNullOrBlank() && t.length > 1) {
                sb.append(t).append(' ')
            }
        } catch (e: Exception) {
        }
        if (sb.length > 400) return
        try {
            val count = node.childCount
            for (i in 0 until count) {
                collectText(node.getChild(i), sb, depth + 1)
                if (sb.length > 400) return
            }
        } catch (e: Exception) {
        }
    }

    override fun onInterrupt() {}
}
