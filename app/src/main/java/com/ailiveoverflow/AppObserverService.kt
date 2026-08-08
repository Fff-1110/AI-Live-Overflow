package com.ailiveoverflow

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AppObserverService : AccessibilityService() {

    private var lastPkg = ""
    private var lastVideoSwitch = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == "com.ailiveoverflow") return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (pkg != lastPkg) {
                    lastPkg = pkg
                    if (pkg.isNotEmpty()) OverlayService.onForegroundAppChanged(pkg)
                }
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (pkg == "com.ss.android.ugc.aweme") {
                    val action = detectDouyinAction(event)
                    if (action != null) OverlayService.onDouyinAction(action)
                }
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (pkg == "com.ss.android.ugc.aweme") {
                    val now = System.currentTimeMillis()
                    if (now - lastVideoSwitch > 8000) {
                        lastVideoSwitch = now
                        OverlayService.onVideoSwitch()
                    }
                }
            }
        }
    }

    // 识别抖音按钮：点赞/评论/关注/分享/收藏
    private fun detectDouyinAction(event: AccessibilityEvent): String? {
        val node = event.source ?: return null
        val sb = StringBuilder()
        var n: AccessibilityNodeInfo? = node
        repeat(4) {
            if (n == null) return@repeat
            val t = n.text?.toString() ?: ""
            val d = n.contentDescription?.toString() ?: ""
            val r = n.viewIdResourceName ?: ""
            if (t.isNotEmpty()) sb.append(t).append(' ')
            if (d.isNotEmpty()) sb.append(d).append(' ')
            if (r.isNotEmpty()) sb.append(r).append(' ')
            n = n.parent
        }
        val s = sb.toString().lowercase()
        return when {
            s.contains("follow") || s.contains("关注") -> "follow"
            s.contains("comment") || s.contains("评论") || s.contains("留言") -> "comment"
            s.contains("share") || s.contains("分享") -> "share"
            s.contains("collect") || s.contains("favorite") || s.contains("star") || s.contains("收藏") -> "collect"
            s.contains("like") || s.contains("digg") || s.contains("点赞") || s.contains("喜欢") -> "like"
            else -> null
        }
    }

    override fun onInterrupt() {}
}
