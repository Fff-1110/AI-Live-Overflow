package com.ailiveoverflow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView

class OverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: WebView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    private var currentApp = "unknown"
    private var appPollRunnable: Runnable? = null
    private var murmurRunnable: Runnable? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "overflow_pet"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("にゃ〜 見てるよ"))
        setupOverlay()
        startAppPolling()
        startMurmurs()
    }

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 300
        }

        overlayView = WebView(this).apply {
            setBackgroundColor(0x00000000)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(AndroidBridge(), "AndroidBridge")
            loadUrl("file:///android_asset/pet.html")
            setupTouchHandler(this)
        }

        windowManager?.addView(overlayView, layoutParams)
    }

    private fun setupTouchHandler(view: WebView) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var dragging = false
        var downTime = 0L
        var longPressTriggered = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    layoutParams?.let { lp ->
                        initialX = lp.x
                        initialY = lp.y
                    }
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    dragging = false
                    longPressTriggered = false
                    downTime = System.currentTimeMillis()
                    handler.postDelayed({
                        if (!dragging) {
                            longPressTriggered = true
                            view.evaluateJavascript("if(window.KuroNeko)KuroNeko.onLongPress()", null)
                        }
                    }, 600)
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) {
                        dragging = true
                        layoutParams?.let { lp ->
                            lp.x = initialX + dx
                            lp.y = initialY + dy
                            windowManager?.updateViewLayout(view, lp)
                        }
                    }
                    dragging
                }
                MotionEvent.ACTION_UP -> {
                    dragging
                }
                else -> false
            }
        }
    }

    // === 前台App检测 ===
    private fun startAppPolling() {
        appPollRunnable = object : Runnable {
            override fun run() {
                val app = detectForegroundApp()
                if (app != currentApp) {
                    currentApp = app
                    overlayView?.evaluateJavascript(
                        "if(window.KuroNeko)KuroNeko.onAppChanged('$app')", null
                    )
                }
                handler.postDelayed(this, 3000)
            }
        }
        handler.postDelayed(appPollRunnable!!, 1000)
    }

    private fun detectForegroundApp(): String {
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 5000, now)
            var pkg = "unknown"
            val event = android.app.usage.UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                    pkg = event.packageName
                }
            }
            pkg
        } catch (e: Exception) {
            "unknown"
        }
    }

    // === 通知栏碎碎念 ===
    private val murmurPhrases = arrayOf(
        "にゃ〜 たいくつ…",
        "ねむい… なにか面白いことない？",
        "じーーーっと見てるよ",
        "お腹すいたかも…",
        "今日はいい天気だにゃ",
        "菲菲、なにしてるの？",
        "退屈だから毛づくろいしとく",
        "誰か遊んでくれないかな〜",
        "ふぁ〜あ…ちょっと寝てた",
        "画面の隅っこで応援してるにゃ"
    )

    private fun startMurmurs() {
        murmurRunnable = object : Runnable {
            override fun run() {
                val phrase = murmurPhrases[(Math.random() * murmurPhrases.size).toInt()]
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIFICATION_ID, buildNotification(phrase))
                handler.postDelayed(this, 30000 + (Math.random() * 60000).toLong())
            }
        }
        handler.postDelayed(murmurRunnable!!, 45000)
    }

    private fun buildNotification(text: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("🐾 Kuro Neko")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    // === JS Bridge ===
    inner class AndroidBridge {
        @JavascriptInterface
        fun moveWindow(dx: Int, dy: Int) {
            handler.post {
                layoutParams?.let { lp ->
                    lp.x += dx
                    lp.y += dy
                    windowManager?.updateViewLayout(overlayView, lp)
                }
            }
        }

        @JavascriptInterface
        fun getCurrentApp(): String = currentApp

        @JavascriptInterface
        fun updateNotification(text: String) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildNotification(text))
        }
    }

    override fun onDestroy() {
        appPollRunnable?.let { handler.removeCallbacks(it) }
        murmurRunnable?.let { handler.removeCallbacks(it) }
        overlayView?.let {
            windowManager?.removeView(it)
            it.destroy()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Overflow", NotificationManager.IMPORTANCE_LOW
        )
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }
}
