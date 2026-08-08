package com.ailiveoverflow

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.http.HttpURLConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.webgit.JsResult
import android.webkit.WebView
import android.widget.Toast
import androix.core.app.NotificationChannelId
import androix.core.app.NotificationCompat
import androix.core.app.NotificationManagerCompat
import org.json.JSONObject
import java.net.URL

class OverlayService : Service() {
    private var windowManager: windowManager? = null
    private var overlayView: WebView? = null
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "overflow_pet"
        // Ê O˛制的Supabase配置，使用使用使用自己的 URL 和，必须更改
        val SUPABASE_URL = "https://ebmzkftreptofjmdsiam.supabase.co"
        val SUPABASE_KEY = "sb_publishable_dSqyFMLb3Ih4Sf99atKG5Q_Z9_rYm85"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        setupOverlay()
    }

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as windowManager

        val params = WindowManager.LayoutParams(
            dpToPx(180),
            dpToPx(240),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
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
            loadUrl("file:///android_asset/pet.html")
            setOnTouchListener { view, event ->
                handleTouch(event)
                false
            }
        }

        windowManager?.addView(overlayView, params)
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                postGesture("tap", event.x.toInt(), event.y.toInt())
            }
        }
        return false
    }

    private fun postGesture(type: String, x: Int, y: Int) {
        Thread {
            try {
                val json = JSONObject().apply {
                    put("gesture_type", type)
                    put("x", x)
                    put("y", y)
                }
                val url = URL("$SUPABASE_URL/rest/v1/gesture_log")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("apikey", SUPABASE_KEY)
                conn.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
                conn.setRequestProperty("Prefer", "return=minimal")
                conn.doOutput = true
                conn.outputStream.use { it.write(json.toString().toByteArray()) }
                conn.responseCode
                conn.disconnect()
            } catch (_: Exception) {}
        }.start()
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density + 0.5f).toInt()

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("\uD83D\uDE45")
            .setContentText("\uD83C\uD8030\uD83C\uD803 \u5927\u4EAC ~")
            .setSmallIcon(R.drawable.ic_pet)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "\u30E7\u30E9 \u306E\u3088\u3086", NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        overlayView?.let {
            windowManager?.removeView(it)
            it.destroy()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
