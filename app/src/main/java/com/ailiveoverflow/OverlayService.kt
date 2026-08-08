package com.ailiveoverflow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.InetAddress
import java.net.URLDecoder
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
    private var cmdPollRunnable: Runnable? = null
    private var pendingDx = 0
    private var pendingDy = 0
    private var moveScheduled = false

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

        fun onDouyinAction(action: String) {
            Log.d("KuroNeko", "onDouyinAction: $action")
            instance?.overlayView?.evaluateJavascript(
                "if(window.KuroNeko)KuroNeko.onDouyinAction('$action')", null
            )
        }

        fun onVideoSwitch() {
            Log.d("KuroNeko", "onVideoSwitch")
            instance?.overlayView?.evaluateJavascript(
                "if(window.KuroNeko)KuroNeko.onVideoSwitch()", null
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        setupOverlay()
        startMurmurs()
        startCmdPolling()
        startLocalHttpServer()
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
        var lastDragNotify = 0L
        var downTime = 0L

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    layoutParams?.let { lp ->
                        initialX = lp.x
                        initialY = lp.y
                    }
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    downTime = event.eventTime
                    dragging = false
                    view.evaluateJavascript("if(window.KuroNeko)KuroNeko.onDragStart()", null)
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
                        val now = System.currentTimeMillis()
                        if (now - lastDragNotify > 250) {
                            lastDragNotify = now
                            view.evaluateJavascript("if(window.KuroNeko)KuroNeko.onDragMove()", null)
                        }
                    }
                    dragging
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        val dt = (event.eventTime - downTime).coerceAtLeast(50)
                        val vx = ((event.rawX - initialTouchX) * 1000f / dt)
                        val vy = ((event.rawY - initialTouchY) * 1000f / dt)
                        view.evaluateJavascript("if(window.KuroNeko)KuroNeko.onFling($vx,$vy)", null)
                        view.evaluateJavascript("if(window.KuroNeko)KuroNeko.onDragEnd()", null)
                    }
                    dragging
                }
                else -> false
            }
        }
    }

    // === 欧尼酱绑定：命令文件轮询 ===
    private fun startCmdPolling() {
        cmdPollRunnable = object : Runnable {
            override fun run() {
                try {
                    val f = File(filesDir, "cmd.txt")
                    if (f.exists()) {
                        val text = f.readText().trim()
                        if (text.isNotEmpty()) {
                            val escaped = text.replace("'", "\\'").replace("\n", " ")
                            overlayView?.evaluateJavascript("if(window.KuroNeko)KuroNeko.exec('$escaped')", null)
                            f.writeText("")
                        }
                    }
                } catch (e: Exception) {
                }
                handler.postDelayed(this, 3000)
            }
        }
        handler.postDelayed(cmdPollRunnable!!, 3000)
    }

    // === 本地HTTP小饭馆：127.0.0.1:28990，供Operit插件投喂/说话（广播受限的替代通道） ===
    private var httpServer: ServerSocket? = null
    private fun startLocalHttpServer() {
        Thread {
            try {
                val server = ServerSocket(28990, 50, InetAddress.getByName("127.0.0.1"))
                httpServer = server
                while (!server.isClosed) {
                    val socket = server.accept()
                    Thread {
                        try {
                            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                            val requestLine = reader.readLine() ?: return@Thread
                            val parts = requestLine.split(" ")
                            if (parts.size >= 2) {
                                val path = parts[1]
                                if (path.startsWith("/cmd?c=")) {
                                    val encoded = path.substring("/cmd?c=".length)
                                    val cmd = URLDecoder.decode(encoded, "UTF-8")
                                    if (cmd.isNotEmpty()) {
                                        File(filesDir, "cmd.txt").writeText(cmd)
                                    }
                                    respond(socket, "{\"ok\":true,\"cmd\":\"$cmd\"}")
                                } else if (path.startsWith("/feed?food=")) {
                                    val encoded = path.substring("/feed?food=".length)
                                    val food = URLDecoder.decode(encoded, "UTF-8")
                                    File(filesDir, "cmd.txt").writeText("food:" + food)
                                    respond(socket, "{\"ok\":true,\"food\":\"$food\"}")
                                } else if (path.startsWith("/ping")) {
                                    respond(socket, "{\"ok\":true,\"name\":\"diao\"}")
                                } else {
                                    respond(socket, "{\"ok\":false,\"error\":\"unknown\"}")
                                }
                            }
                        } catch (e: Exception) {
                        } finally {
                            try { socket.close() } catch (e: Exception) {}
                        }
                    }.start()
                }
            } catch (e: Exception) {
            }
        }.start()
    }
    private fun respond(socket: java.net.Socket, body: String) {
        try {
            val out: OutputStream = socket.getOutputStream()
            val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: " + body.toByteArray().size + "\r\nConnection: close\r\n\r\n" + body
            out.write(resp.toByteArray())
            out.flush()
        } catch (e: Exception) {
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
                pendingDx += dx
                pendingDy += dy
                if (!moveScheduled) {
                    moveScheduled = true
                    handler.postDelayed({
                        moveScheduled = false
                        val mx = pendingDx
                        val my = pendingDy
                        pendingDx = 0
                        pendingDy = 0
                        layoutParams?.let { lp ->
                            val dm = resources.displayMetrics
                            val maxX = dm.widthPixels - (overlayView?.width ?: 0)
                            val maxY = dm.heightPixels - (overlayView?.height ?: 0)
                            lp.x = (lp.x + mx).coerceIn(0, maxX.coerceAtLeast(0))
                            lp.y = (lp.y + my).coerceIn(0, maxY.coerceAtLeast(0))
                            windowManager?.updateViewLayout(overlayView, lp)
                        }
                    }, 200)
                }
            }
        }

        @JavascriptInterface
        fun updateNotification(text: String) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(MURMUR_ID, buildMurmurNotification(text))
        }

        @JavascriptInterface
        fun getWindowPos(): String {
            return try {
                val lp = layoutParams ?: return "{}"
                val v = overlayView ?: return "{}"
                val dm = resources.displayMetrics
                "{\"x\":${lp.x},\"y\":${lp.y},\"w\":${v.width},\"h\":${v.height},\"sw\":${dm.widthPixels},\"sh\":${dm.heightPixels}}"
            } catch (e: Exception) {
                "{}"
            }
        }

        @JavascriptInterface
        fun moveToCenter() {
            handler.post {
                val v = overlayView ?: return@post
                val lp = layoutParams ?: return@post
                val dm = resources.displayMetrics
                lp.x = (dm.widthPixels - v.width) / 2
                lp.y = (dm.heightPixels - v.height) / 2
                windowManager?.updateViewLayout(v, lp)
            }
        }
    }

    override fun onDestroy() {
        murmurRunnable?.let { handler.removeCallbacks(it) }
        cmdPollRunnable?.let { handler.removeCallbacks(it) }
        try { httpServer?.close() } catch (e: Exception) {}
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