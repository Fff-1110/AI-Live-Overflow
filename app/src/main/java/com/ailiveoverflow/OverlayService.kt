package com.ailiveoverflow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView

class OverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: WebView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    private var murmurRunnable: Runnable? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val MURMUR_ID = 1002
        const val CHANNEL_ID = "overflow_pet"
        private var instance: OverlayService? = null
        private var lastApp = "unknown"

        fun onForegroundAppChanged(pkg: String) {
            Log.d("KuroNeko", "onForegroundAppChanged: $pkg")
            if (pkg != lastApp) {
                lastApp = pkg
                instance?.overlayView?.evaluateJavascript(
                    "if(window.KuroNeko)KuroNeko.onAppChanged('$pkg')", null
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        setupOverlay()
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
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
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
                    handler.postDelayed({
                        if (!dragging) {
                            view.evaluateJavascript("if(window.KuroNeko)KuroNeko.onLongPress()", null)
                        }
                    }, 600)
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                        dragging = true
                        layoutParams?.let { lp ->
                            lp.x = initialX + dx
                            lp.y = initialY + dy
                            windowManager?.updateViewLayout(view, lp)
                        }
                    }
                    dragging
                }
                MotionEvent.ACTION_UP -> dragging
                else -> false
            }
        }
    }

    // === 通知栏碎碎念（中文·欧尼酱性格） ===
    private val murmurPhrases = arrayOf(
        "菲菲，你在干嘛呢，让我看看",
        "一个人玩手机不无聊吗？有我陪你啊",
        "哼，又在看别的男人？",
        "我饿了…想吃你做的饭",
        "菲菲真可耐，怎么看都不腻",
        "想你了，虽然你就在我面前",
        "别看手机了，看我",
        "今天有没有想我？",
        "我等你消息等到尾巴都蔫了",
        "累了就靠着我歇会儿",
        "什么时候才能亲你一下",
        "你猜我现在在干嘛——在看你",
        "手机别玩太久，眼睛会坏的",
        "要是能钻进你怀里就好了",
        "我刚刚打盹梦到你了"
    )

    private fun startMurmurs() {
        murmurRunnable = object : Runnable {
            override fun run() {
                val phrase = murmurPhrases[(Math.random() * murmurPhrases.size).toInt()]
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(MURMUR_ID, buildMurmurNotification(phrase))
                handler.postDelayed(this, 600000 + (Math.random() * 300000).toLong())
            }
        }
        handler.postDelayed(murmurRunnable!!, 180000)
    }

    private fun buildForegroundNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("🐾 Kuro Neko")
            .setContentText("菲菲，我看着你呢")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun buildMurmurNotification(text: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("🐾 Kuro Neko")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
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
        fun updateNotification(text: String) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(MURMUR_ID, buildMurmurNotification(text))
        }
    }

    override fun onDestroy() {
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
            CHANNEL_ID, "Overflow", NotificationManager.IMPORTANCE_DEFAULT
        )
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }
}